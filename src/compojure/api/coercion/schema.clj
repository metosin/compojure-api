(ns compojure.api.coercion.schema
  (:require [schema.coerce :as sc]
            [schema.utils :as su]
            [linked.core :as linked]
            [ring.swagger.coerce :as coerce]
            [compojure.api.request :as request]
            [compojure.api.coercion.core :as cc]
            [clojure.walk :as walk]
            [schema.core :as s]
            [compojure.api.common :as common])
  (:import (java.io File)
           (schema.core OptionalKey RequiredKey)
           (schema.utils ValidationError NamedError)))

(def string-coercion-matcher coerce/query-schema-coercion-matcher)
(def json-coercion-matcher coerce/json-schema-coercion-matcher)

(defn stringify
  "Stringifies Schema records recursively."
  [error]
  (walk/prewalk
    (fn [x]
      (cond
        (class? x) (.getName ^Class x)
        (instance? OptionalKey x) (pr-str (list 'opt (:k x)))
        (instance? RequiredKey x) (pr-str (list 'req (:k x)))
        (and (satisfies? s/Schema x) (record? x)) (try (pr-str (s/explain x)) (catch Exception _ x))
        (instance? ValidationError x) (str (su/validation-error-explain x))
        (instance? NamedError x) (str (su/named-error-explain x))
        :else x))
    error))

(def memoized-coercer
  (common/fifo-memoize sc/coercer 10000))

;; don't use coercion for certain types
(defmulti coerce-response? identity :default ::default)
(defmethod coerce-response? ::default [_] true)
(defmethod coerce-response? File [_] false)

(defrecord SchemaCoercion [name options]
  cc/Coercion
  (get-name [_] name)

  (get-apidocs [_ _ data] data)

  (make-open [_ schema]
    (if (map? schema)
      (assoc schema s/Keyword s/Any)
      schema))

  (encode-error [_ error]
    (-> error
        (update :schema pr-str)
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

  (accept-response? [_ model]
    (coerce-response? model))

  (coerce-response [this schema value type format request]
    (cc/coerce-request this schema value type format request)))

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
