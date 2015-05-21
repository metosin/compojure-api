(ns compojure.api.routes
  (:require [compojure.core :refer :all]
            [plumbing.core :refer [for-map]]))

(defmulti collect-routes identity)

(defn route-lookup-table [routes]
  (for-map [[path endpoints] (:paths routes)
            [method {:keys [x-name parameters]}] endpoints
            :let [params (:path parameters)]
            :when x-name]
    x-name {path (merge
                   {:method method}
                   (if params
                     {:params params}))}))

(defmacro api-root [& body]
  (let [[routes body] (collect-routes body)
        lookup (route-lookup-table routes)]
    `(with-meta (routes ~@body) {:routes '~routes
                                 :lookup ~lookup})))
