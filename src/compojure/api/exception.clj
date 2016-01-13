(ns compojure.api.exception
  (:require [ring.util.http-response :refer [internal-server-error bad-request]]
            [clojure.walk :refer [postwalk]]
            [compojure.api.impl.logging :as logging]
            [schema.utils :as su])
  (:import [schema.utils ValidationError NamedError]
           [com.fasterxml.jackson.core JsonParseException]
           [org.yaml.snakeyaml.parser ParserException]))

;;
;; Default exception handlers
;;

(defn log-exception [^Exception e]
  (logging/log! :error e (.getMessage e)))

(defn safe-handler
  "Writes :error to log with the exception message & stacktrace.

   Error response only contains class of the Exception so that it won't accidentally
   expose secret details."
  [^Exception e _ _]
  (internal-server-error {:type "unknown-exception"
                          :class (.getName (.getClass e))}))

(defn stringify-error
  "Stringifies symbols and validation errors in Schema error, keeping the structure intact."
  [error]
  (postwalk
    (fn [x]
      (cond
        (instance? ValidationError x) (str (su/validation-error-explain x))
        (instance? NamedError x) (str (su/named-error-explain x))
        :else x))
    error))

(defn response-validation-handler
  "Creates error response based on Schema error."
  [_ data _]
  (internal-server-error {:errors (stringify-error (su/error-val data))}))

(defn request-validation-handler
  "Creates error response based on Schema error."
  [_ data _]
  (bad-request {:errors (stringify-error (su/error-val data))}))

(defn schema-error-handler
  "Creates error response based on Schema error."
  [_ data _]
  ; FIXME: Why error is not wrapped to ErrorContainer here?
  (bad-request {:errors (stringify-error (:error data))}))

(defn request-parsing-handler
  [^Exception ex _ _]
  (let [cause (.getCause ex)]
    (bad-request {:type (cond
                          (instance? JsonParseException cause) "json-parse-exception"
                          (instance? ParserException cause) "yaml-parse-exception"
                          :else "parse-exception")
                  :message (.getMessage cause)})))

;;
;; Mappings from other Exception types to our base types
;;

(def legacy-exception-types
  {:ring.swagger.schema/validation ::request-validation})
