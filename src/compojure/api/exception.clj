(ns compojure.api.exception
  (:require [ring.util.http-response :as response]
            [clojure.walk :as walk]
            [compojure.api.impl.logging :as logging]
            [compojure.api.coercion.core :as cc]
            [compojure.api.coercion.schema]))

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

;; TODO: coercion should handle how to publish data
(defn response-validation-handler
  "Creates error response based on a response error. The following keys are available:

    :type            type of the exception (::response-validation)
    :coercion        coercion instance used
    :in              location of the value ([:response :body])
    :schema          schema to be validated against
    :error           schema error
    :request         raw request
    :response        raw response"
  [e data req]
  (response/internal-server-error
    (-> data
        (dissoc :request :response)
        (update :coercion cc/get-name)
        (assoc :value (-> data :response :body))
        (->> (cc/encode-errors (:coercion data))))))

;; TODO: coercion should handle how to publish data
(defn request-validation-handler
  "Creates error response based on Schema error. The following keys are available:

    :type            type of the exception (::request-validation)
    :coercion        coercion instance used
    :value           value that was validated
    :in              location of the value (e.g. [:request :query-params])
    :schema          schema to be validated against
    :error           schema error
    :request         raw request"
  [e data req]
  (response/bad-request
    (-> data
        (dissoc :request)
        (update :coercion cc/get-name)
        (->> (cc/encode-errors (:coercion data))))))

(defn schema-error-handler
  "Creates error response based on Schema error."
  [e data req]
  ; FIXME: Why error is not wrapped to ErrorContainer here?
  (response/bad-request
    {:errors (compojure.api.coercion.schema/stringify (:error data))}))

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
