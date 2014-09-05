(ns compojure.api.test-utils
  (:require [cheshire.core :as cheshire]))

(defn parse-body [body]
  (let [body (if (instance? java.io.InputStream body)
                           (slurp body)
                           body)
        body (if (instance? String body)
               (cheshire/parse-string body true)
               body)]
    body))
