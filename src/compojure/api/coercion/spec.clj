(ns compojure.api.coercion.spec
  (:require [schema.core]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [spec-tools.data-spec :as ds]
            [clojure.walk :as walk]
            [linked.core :as linked]
            [compojure.api.coercion.core :as cc]
            [compojure.api.impl.logging :as log]
            [spec-tools.conform :as conform]
            [spec-tools.swagger.core :as swagger]
            [compojure.api.common :as common])
  (:import (clojure.lang IPersistentMap)
           (schema.core RequiredKey OptionalKey)
           (spec_tools.core Spec)))

(def string-conforming
  (st/type-conforming
    (merge
      conform/string-type-conforming
      conform/strip-extra-keys-type-conforming)))

(def json-conforming
  (st/type-conforming
    (merge
      conform/json-type-conforming
      conform/strip-extra-keys-type-conforming)))

(def default-conforming
  ::default)

(defprotocol Specify
  (specify [this name]))

(extend-protocol Specify
  IPersistentMap
  (specify [this name]
    (->>
      (walk/postwalk
        (fn [x]
          (if (and (map? x) (not (record? x)))
            (->> (for [[k v] (dissoc x schema.core/Keyword)
                       :let [k (cond
                                 ;; Schema required
                                 (instance? RequiredKey k)
                                 (ds/req (schema.core/explicit-schema-key k))

                                 ;; Schema options
                                 (instance? OptionalKey k)
                                 (ds/opt (schema.core/explicit-schema-key k))

                                 :else
                                 k)]]
                   [k v])
                 (into {}))
            x))
        this)
      (ds/spec name)))

  Spec
  (specify [this _] this)

  Object
  (specify [this _]
    (st/create-spec {:spec this})))

(def memoized-specify
  (common/fifo-memoize #(specify %1 (gensym "spec")) 10000))

(defmulti coerce-response? identity :default ::default)
(defmethod coerce-response? ::default [_] true)

(defrecord SpecCoercion [name options]
  cc/Coercion
  (get-name [_] name)

  (get-apidocs [_ _ {:keys [parameters responses] :as info}]
    (cond-> (dissoc info :parameters :responses)
            parameters (assoc
                         ::swagger/parameters
                         (into
                           (empty parameters)
                           (for [[k v] parameters]
                             [k (memoized-specify v)])))
            responses (assoc
                        ::swagger/responses
                        (into
                          (empty responses)
                          (for [[k response] responses]
                            [k (update response :schema memoized-specify)])))))

  (make-open [_ spec] spec)

  (encode-error [_ error]
    (update error :spec (comp str s/form)))

  (coerce-request [_ spec value type format _]
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
            (s/unform spec conformed)))
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
