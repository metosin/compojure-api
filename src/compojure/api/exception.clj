(ns compojure.api.exception
  (:require [ring.util.http-response :as response]
            [clojure.walk :as walk]
            [compojure.api.impl.logging :as logging]
            [schema.utils :as su])
  (:import [schema.utils ValidationError NamedError]
           [com.fasterxml.jackson.core JsonParseException]
           [org.yaml.snakeyaml.parser ParserException]))

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

(defn stringify-error
  "Stringifies symbols and validation errors in Schema error, keeping the structure intact."
  [error]
  (walk/postwalk
    (fn [x]
      (cond
        (instance? ValidationError x) (str (su/validation-error-explain x))
        (instance? NamedError x) (str (su/named-error-explain x))
        :else x))
    error))

(defn response-validation-handler
  "Creates error response based on Schema error."
  [e data req]
  (response/internal-server-error {:errors (stringify-error (su/error-val data))}))

(defn request-validation-handler
  "Creates error response based on Schema error."
  [e data req]
  (response/bad-request {:errors (stringify-error (su/error-val data))}))

(defn schema-error-handler
  "Creates error response based on Schema error."
  [e data req]
  ; FIXME: Why error is not wrapped to ErrorContainer here?
  (response/bad-request {:errors (stringify-error (:error data))}))

(defn request-parsing-handler
  [^Exception ex data req]
  (let [cause (.getCause ex)]
    (response/bad-request {:type (cond
                                   (instance? JsonParseException cause) "json-parse-exception"
                                   (instance? ParserException cause) "yaml-parse-exception"
                                   :else "parse-exception")
                           :message (.getMessage cause)})))

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

(def legacy-exception-types
  {:ring.swagger.schema/validation ::request-validation})
