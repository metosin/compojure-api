(ns compojure.api.test-utils
  (:require [cheshire.core :as cheshire]))

(defn read-body [body]
  (if (instance? java.io.InputStream body)
    (slurp body)
    body))

(defn parse-body [body]
  (let [body (read-body body)
        body (if (instance? String body)
               (cheshire/parse-string body true)
               body)]
    body))
