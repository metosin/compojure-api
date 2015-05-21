(ns compojure.api.routes
  (:require [compojure.core :refer :all]
            [plumbing.core :refer [for-map]]))

(defmulti collect-routes identity)

(defn route-lookup-table [routes]
  (for-map [[path endpoints] (:paths routes)
            [method {:keys [x-name]}] endpoints
            :when x-name]
    x-name {path method}))

(defmacro api-root [& body]
  (let [[routes body] (collect-routes body)
        lookup (route-lookup-table routes)]
    `(with-meta (routes ~@body) {:routes '~routes
                                 :lookup ~lookup})))
