(ns compojure.api.coercion.schema
  (:require [schema.coerce :as sc]
            [schema.utils :as su]
            [linked.core :as linked]
            [ring.swagger.coerce :as coerce]
            [compojure.api.request :as request]
            [compojure.api.coercion.core :as cc]
            [compojure.api.impl.logging :as log]
            [clojure.walk :as walk]
            [schema.core :as s])
  (:import (java.io File)
           (schema.core OptionalKey RequiredKey)
           (schema.utils ValidationError NamedError)))

(def string-coercion-matcher coerce/query-schema-coercion-matcher)
(def json-coercion-matcher coerce/json-schema-coercion-matcher)

(defn stringify
  "Stringifies Schema records recursively."
  [error]
  (walk/postwalk
    (fn [x]
      (cond
        (class? x) (.getName ^Class x)
        (instance? OptionalKey x) (pr-str (list 'opt (:k x)))
        (instance? RequiredKey x) (pr-str (list 'req (:k x)))
        (satisfies? s/Schema x) (try (pr-str (s/explain x)) (catch Exception _ x))
        (instance? ValidationError x) (str (su/validation-error-explain x))
        (instance? NamedError x) (str (su/named-error-explain x))
        :else x))
    error))

(def memoized-coercer
  "Returns a memoized version of a referentially transparent coercer fn. The
  memoized version of the function keeps a cache of the mapping from arguments
  to results and, when calls with the same arguments are repeated often, has
  higher performance at the expense of higher memory use. FIFO with 10000 entries.
  Cache will be filled if anonymous coercers are used (does not match the cache)"
  (let [cache (atom (linked/map))
        cache-size 10000]
    (fn [& args]
      (or (@cache args)
          (let [coercer (apply sc/coercer args)]
            (swap! cache (fn [mem]
                           (let [mem (assoc mem args coercer)]
                             (if (>= (count mem) cache-size)
                               (dissoc mem (-> mem first first))
                               mem))))
            coercer)))))

;; don't use coercion for certain types
(defmulti coerce-response? identity :default ::default)
(defmethod coerce-response? ::default [_] true)
(defmethod coerce-response? File [_] false)

(defrecord SchemaCoercion [name options]
  cc/Coercion
  (get-name [_] name)

  (get-apidocs [_ _ data] data)

  (encode-errors [_ data]
    (-> data
        (update :schema stringify)
        (update :errors stringify)))

  (coerce-request [_ schema value type format request]
    (let [type-options (options type)]
      (if-let [matcher (or (get (get type-options :formats) format)
                           (get type-options :default))]
        (let [coerce (memoized-coercer schema matcher)
              coerced (coerce value)]
          (if (su/error? coerced)
            (let [errors (su/error-val coerced)]
              (cc/map->CoercionError
                {:schema schema
                 :errors errors}))
            coerced))
        value)))

  (coerce-response [this schema value type format request]
    (if (coerce-response? schema)
      (cc/coerce-request this schema value type format request)
      value)))

(def default-options
  {:body {:default (constantly nil)
          :formats {"application/json" json-coercion-matcher
                    "application/msgpack" json-coercion-matcher
                    "application/x-yaml" json-coercion-matcher}}
   :string {:default string-coercion-matcher}
   :response {:default (constantly nil)}})

(defn create-coercion [options]
  (->SchemaCoercion :schema options))

(def default-coercion (create-coercion default-options))

(defmethod cc/named-coercion :schema [_] default-coercion)

(log/log! :info ":schema coercion enabled in compojure.api")
