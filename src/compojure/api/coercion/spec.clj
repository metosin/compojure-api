(ns compojure.api.coercion.spec
  (:require [schema.core]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [spec-tools.data-spec :as ds]
            [clojure.walk :as walk]
            [linked.core :as linked]
            [compojure.api.common :as common])
  (:import (clojure.lang IPersistentMap)))

(def string-conforming st/string-conforming)
(def json-conforming st/json-conforming)
(def default-conforming ::default)

(defprotocol Specify
  (specify [this name]))

(extend-protocol Specify
  IPersistentMap
  (specify [this name]
    (->>
      (walk/postwalk
        (fn [x]
          (if (and (map? x) (not (record? x)))
            (->> (for [[k v] x
                       :when (keyword? v)
                       :let [k (if-not (schema.core/required-key? k)
                                 (ds/opt (schema.core/explicit-schema-key k))
                                 k)]]
                   [k v])
                 (into {}))
            x))
        this)
      (ds/spec name)))
  Object
  (specify [this _] this))

(def memoized-specify
  (common/fifo-memoize #(specify %1 (gensym)) 10000))

(defmulti coerce-response? identity :default ::default)
(defmethod coerce-response? ::default [_] true)

(defrecord SpecCoercion [name options]
  cc/Coercion
  (get-name [_] name)

  ;; TODO: spec-swagger
  (get-apidocs [_ _ info]
    (dissoc info :parameters :responses))

  (encode-error [_ error]
    (update error :spec (comp str s/form)))

  (coerce-request [_ spec value type format request]
    (let [spec (memoized-specify spec)
          type-options (options type)]
      (if-let [conforming (or (get (get type-options :formats) format)
                              (get type-options :default))]
        (let [conforming (if-not (= conforming default-conforming) conforming)
              conformed (st/conform spec value conforming)]
          (if (s/invalid? conformed)
            (let [problems (st/explain-data spec value conforming)]
              (cc/map->CoercionError
                {:spec spec
                 :problems (::s/problems problems)}))
            conformed))
        value)))

  (coerce-response [this spec value type format request]
    (if (coerce-response? spec)
      (cc/coerce-request this spec value type format request)
      value)))

(def default-options
  {:body {:default default-conforming
          :formats {"application/json" json-conforming
                    "application/msgpack" json-conforming
                    "application/x-yaml" json-conforming}}
   :string {:default string-conforming}
   :response {:default default-conforming}})

(defn create-coercion [options]
  (->SpecCoercion :spec options))

(def default-coercion (create-coercion default-options))

(defmethod cc/named-coercion :spec [_] default-coercion)

(log/log! :info ":spec coercion enabled in compojure.api")
