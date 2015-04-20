(ns compojure.api.test-utils
  (:require [cheshire.core :as cheshire]
            [clojure.string :as str])
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

(defn extract-schema-name [ref-str]
  (last (str/split ref-str #"/")))

(defn find-definition [spec ref]
  (let [schema-name (keyword (extract-schema-name ref))]
    (get-in spec [:definitions schema-name])))

