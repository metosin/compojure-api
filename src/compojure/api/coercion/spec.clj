(ns compojure.api.coercion.spec
  (:require [compojure.api.middleware :as mw]
            [schema.core]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [compojure.api.coercion.core :as cc]
            [compojure.api.impl.logging :as log]
            [spec-tools.data-spec :as ds]
            [clojure.walk :as walk]
            [linked.core :as linked])
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
  "Returns a memoized version of a referentially transparent specify. The
  memoized version of the function keeps a cache of the mapping from arguments
  to results and, when calls with the same arguments are repeated often, has
  higher performance at the expense of higher memory use. FIFO with 10000 entries.
  Cache will be filled if anonymous coercers are used (does not match the cache)"
  (let [cache (atom (linked/map))
        cache-size 10000]
    (fn [spec]
      (or (@cache spec)
          (let [f (specify spec (gensym))]
            (swap! cache (fn [mem]
                           (let [mem (assoc mem spec f)]
                             (if (>= (count mem) cache-size)
                               (dissoc mem (-> mem first first))
                               mem))))
            f)))))

(defmulti coerce-response? identity :default ::default)
(defmethod coerce-response? ::default [_] true)

(defrecord SpecCoercion [name options]
  cc/Coercion
  (get-name [_] name)

  ;; TODO: spec-swagger
  (get-apidocs [_ _ info]
    (dissoc info :parameters :responses))

  (encode-errors [_ data]
    (update data :spec (comp str s/form)))

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
