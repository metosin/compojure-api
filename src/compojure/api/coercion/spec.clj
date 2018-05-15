(ns compojure.api.coercion.spec
  (:require [schema.core]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [spec-tools.data-spec :as ds]
            [clojure.walk :as walk]
            [compojure.api.coercion.core :as cc]
            [compojure.api.impl.logging :as log]
            [spec-tools.transform :as stt]
            [spec-tools.swagger.core :as swagger]
            [compojure.api.common :as common])
  (:import (clojure.lang IPersistentMap)
           (schema.core RequiredKey OptionalKey)
           (spec_tools.core Spec)))

(def string-transformer
  (st/type-transformer
    {:name :string
     :decoders (merge
                 stt/string-type-decoders
                 stt/strip-extra-keys-type-decoders)
     :encoders stt/string-type-encoders
     :default-encoder stt/any->any}))

(def json-transformer
  (st/type-transformer
    {:name :json
     :decoders (merge
                 stt/json-type-decoders
                 stt/strip-extra-keys-type-decoders)
     :encoders stt/json-type-encoders
     :default-encoder stt/any->any}))

(def default-transformer
  (st/type-transformer {}))

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
  (common/fifo-memoize #(specify %1 (keyword "" (name (gensym "spec")))) 1000))

(defn maybe-memoized-specify [spec]
  (if (keyword? spec)
    (specify spec nil)
    (memoized-specify spec)))

(defn stringify-pred [pred]
  (str (if (instance? clojure.lang.LazySeq pred)
         (seq pred)
         pred)))

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
                             [k (maybe-memoized-specify v)])))
            responses (assoc
                        ::swagger/responses
                        (into
                          (empty responses)
                          (for [[k response] responses]
                            [k (update response :schema maybe-memoized-specify)])))))

  (make-open [_ spec] spec)

  (encode-error [_ error]
    (-> error
        (update :spec (comp str s/form))
        (update :problems (partial mapv #(update % :pred stringify-pred)))))

  (coerce-request [_ spec value type format _]
    (let [spec (maybe-memoized-specify spec)
          type-options (options type)]
      (if-let [conforming (or (get (get type-options :formats) format)
                              (get type-options :default))]
        (let [conformed (st/conform spec value conforming)]
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
  {:body {:default default-transformer
          :formats {"application/json" json-transformer
                    "application/msgpack" json-transformer
                    "application/x-yaml" json-transformer}}
   :string {:default string-transformer}
   :response {:default default-transformer}})

(defn create-coercion [options]
  (->SpecCoercion :spec options))

(def default-coercion (create-coercion default-options))

(defmethod cc/named-coercion :spec [_] default-coercion)

(log/log! :info ":spec coercion enabled in compojure.api")
