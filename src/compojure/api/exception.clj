(ns compojure.api.exception
  (:require [ring.util.http-response :refer [internal-server-error bad-request]]
            [clojure.walk :refer [postwalk]]
            [schema.utils :as su])
  (:import [schema.utils ValidationError NamedError]
           [com.fasterxml.jackson.core JsonParseException]
           [org.yaml.snakeyaml.parser ParserException]))

;;
;; Default exception handlers
;;

(defn safe-handler
  "Prints stacktrace to console and returns safe error response.

   Error response only contains class of the Exception so that it won't accidentally
   expose secret details."
  [^Exception e data request]
  (.printStackTrace e)
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
  [_ data request]
  (internal-server-error {:errors (stringify-error (su/error-val data))}))

(defn request-validation-handler
  "Creates error response based on Schema error."
  [_ data request]
  (bad-request {:errors (stringify-error (su/error-val data))}))

(defn schema-error-handler
  "Creates error response based on Schema error."
  [ex data request]
  ; FIXME: Why error is not wrapped to ErrorContainer here?
  (bad-request {:errors (stringify-error (:error data))}))

(defn request-parsing-handler
  [ex data request]
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
