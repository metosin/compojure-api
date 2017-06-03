(ns compojure.api.exception
  (:require [ring.util.http-response :as response]
            [clojure.walk :as walk]
            [compojure.api.impl.logging :as logging]
            [schema.utils :as su]
            [schema.core :as s])
  (:import
    (schema.core OptionalKey RequiredKey)
    (schema.utils ValidationError NamedError)))

;;
;; Default exception handlers
;;

(defn safe-handler
  "Writes :error to log with the exception message & stacktrace.

  Error response only contains class of the Exception so that it won't accidentally
  expose secret details."
  [^Exception e data req]
  (logging/log! :error e (.getMessage e))
  (response/internal-server-error {:type "unknown-exception"
                                   :class (.getName (.getClass e))}))

(defn stringify
  "Stringifies symbols and validation errors in Schema error, keeping the structure intact."
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

(defn response-validation-handler
  "Creates error response based on a response error. The following keys are available:

    :type            type of the exception (::response-validation)
    :validation      validation lib that was used (:schema)
    :in              location of the value ([:response :body])
    :schema          schema to be validated against
    :error           schema error
    :response        raw response"
  [e data req]
  (response/internal-server-error
    (-> data
        (dissoc :response)
        (assoc :value (-> data :response :body))
        (update :schema stringify)
        (update :errors stringify))))

(defn request-validation-handler
  "Creates error response based on Schema error. The following keys are available:

    :type            type of the exception (::request-validation)
    :validation      validation lib that was used (:schema)
    :value           value that was validated
    :in              location of the value (e.g. [:request :query-params])
    :schema          schema to be validated against
    :error           schema error
    :request         raw request"
  [e data req]
  (response/bad-request
    (-> data
        (dissoc :request)
        (update :schema stringify)
        (update :errors stringify))))

(defn schema-error-handler
  "Creates error response based on Schema error."
  [e data req]
  ; FIXME: Why error is not wrapped to ErrorContainer here?
  (response/bad-request {:errors (stringify (:error data))}))

(defn request-parsing-handler
  [^Exception ex data req]
  (let [cause (.getCause ex)
        original (.getCause cause)]
    (response/bad-request
      (merge (select-keys data [:type :format :charset])
             (if original {:original (.getMessage original)})
             {:message (.getMessage cause)}))))

;;
;; Logging
;;

(defn with-logging
  "Wrap compojure-api exception-handler a function which will log the
  exception message and stack-trace with given log-level."
  ([handler] (with-logging handler :error))
  ([handler log-level]
   {:pre [(#{:trace :debug :info :warn :error :fatal} log-level)]}
   (fn [^Exception e data req]
     (logging/log! log-level e (.getMessage e))
     (handler e data req))))

;;
;; Mappings from other Exception types to our base types
;;

(def mapped-exception-types
  {:ring.swagger.schema/validation ::request-validation
   :muuntaja/decode ::request-parsing})
