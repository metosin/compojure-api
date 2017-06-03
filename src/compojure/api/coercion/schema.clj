(ns compojure.api.coercion.schema
  (:require [schema.coerce :as sc]
            [schema.utils :as su]
            [linked.core :as linked]
            [ring.swagger.coerce :as coerce]
            [compojure.api.middleware :as mw]
            [compojure.api.coercion :as coercion])
  (:import (java.io File)))

(defn memoized-coercer
  "Returns a memoized version of a referentially transparent coercer fn. The
  memoized version of the function keeps a cache of the mapping from arguments
  to results and, when calls with the same arguments are repeated often, has
  higher performance at the expense of higher memory use. FIFO with 10000 entries.
  Cache will be filled if anonymous coercers are used (does not match the cache)"
  []
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

(defn cached-coercer [request]
  (or (-> request mw/get-options :coercer) sc/coercer))

;; don't use coercion for certain types
(defmulti coerce-response? identity :default ::default)
(defmethod coerce-response? ::default [_] true)
(defmethod coerce-response? File [_] false)

(defrecord SchemaCoercion [name options]
  coercion/Coercion
  (get-name [_] name)

  (coerce-request [_ schema value type format request]
    (let [type-options (options type)]
      (if-let [matcher (or (get (get type-options :formats) format)
                           (get type-options :default))]
        (let [coercer (cached-coercer request)
              coerce (coercer schema matcher)
              coerced (coerce value)]
          (if (su/error? coerced)
            (let [errors (su/error-val coerced)]
              (coercion/map->CoercionError
                {:schema schema
                 :errors errors}))
            coerced))
        value)))

  (coerce-response [this schema value type format request]
    (if (coerce-response? schema)
      (coercion/coerce-request this schema value type format request)
      value)))

(def string-coercion-matcher coerce/query-schema-coercion-matcher)
(def json-coercion-matcher coerce/json-schema-coercion-matcher)

(def default-options
  {:body {:default (constantly nil)
          :formats {"application/json" json-coercion-matcher
                    "application/msgpack" json-coercion-matcher
                    "application/x-yaml" json-coercion-matcher}}
   :string {:default string-coercion-matcher}
   :response {:default (constantly nil)}})

(def schema-coercion (->SchemaCoercion :schema default-options))

(defmethod coercion/named-coercion :schema [_] schema-coercion)