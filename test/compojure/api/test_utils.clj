(ns compojure.api.test-utils
  (:require [cheshire.core :as cheshire])
  (:import [java.io InputStream]))

(defn read-body [body]
  (if (instance? InputStream body)
    (slurp body)
    body))

(defn parse-body [body]
  (let [body (read-body body)
        body (if (instance? String body)
               (cheshire/parse-string body true)
               body)]
    body))
