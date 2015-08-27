(ns compojure.api.exception
  (:require [ring.util.http-response :refer [internal-server-error bad-request]]
            [clojure.walk :refer [postwalk]]
            [plumbing.core :refer [for-map]]
            [schema.utils :as su])
  (:import [schema.utils ValidationError]))

;;
;; Default exception handlers
;;

(defn safe-handler
  "Prints stacktrace to console and returns safe error response.

   Error response only contains class of the Exception so that it won't accidentally
   expose secret details."
  [^Exception e error-type request]
  (.printStackTrace e)
  (internal-server-error {:type "unknown-exception"
                          :class (.getName (.getClass e))}))

(defn stringify-error
  "Stringifies symbols and validation errors in Schema error, keeping the structure intact."
  [error]
  (postwalk
    (fn [x]
      (if-not (map? x)
        x
        (for-map [[k v] x]
          k (cond
              (instance? ValidationError v) (str (su/validation-error-explain v))
              (symbol? v) (str v)
              :else v))))
    error))

(defn response-validation-handler
  "Creates error response based on Schema error."
  [error error-type request]
  (internal-server-error {:errors (stringify-error (su/error-val error))}))

(defn request-validation-handler
  "Creates error response based on Schema error."
  [error error-type request]
  (bad-request {:errors (stringify-error (su/error-val error))}))

;;
;; Mappings from other Exception types to our base types
;;

(def legacy-exception-types
  {:ring.swagger.schema/validation ::request-validation})
