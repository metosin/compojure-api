(ns compojure.api.routes
  (:require [compojure.core :refer :all]
            [clojure.string :as string]))

(defmulti collect-routes identity)

(defn- duplicates [seq]
  (for [[id freq] (frequencies seq)
        :when (> freq 1)] id))

(defn- route-lookup-table [routes]
  (let [entries (for [[path endpoints] (:paths routes)
                      [method {:keys [x-name parameters]}] endpoints
                      :let [params (:path parameters)]
                      :when x-name]
                  [x-name {path (merge
                                  {:method method}
                                  (if params
                                    {:params params}))}])
        route-names (map first entries)
        duplicate-route-names (duplicates route-names)]
    (when (seq duplicate-route-names)
      (throw (IllegalArgumentException.
               (str "Found multiple routes with same name: "
                    (string/join "," duplicate-route-names)))))
    (into {} entries)))

(defmacro api-root [& body]
  (let [[routes body] (collect-routes body)
        lookup (route-lookup-table routes)]
    `(with-meta (routes ~@body) {:routes '~routes
                                 :lookup ~lookup})))
