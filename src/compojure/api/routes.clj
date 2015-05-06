(ns compojure.api.routes
  (:require [compojure.core :refer :all]))

(defmulti collect-routes identity)

(defmacro api-root [& body]
  (let [[routes body] (collect-routes body)]
    `(with-meta (routes ~@body) '~routes)))
