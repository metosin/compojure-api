(ns compojure.api.routes
  (:require [compojure.core :refer :all]
            [clojure.string :as string]
            [cheshire.core :as json]
            [compojure.api.middleware :as mw]
            [clojure.string :as str]
            [linked.core :as linked]))

(defn- un-quote [s]
  (str/replace s #"^\"(.+(?=\"$))\"$" "$1"))

(defn ->path [s params]
  (-> s
      (str/replace #":([^/]+)" " :$1 ")
      (str/split #" ")
      (->> (map
             (fn [[head :as token]]
               (if (= head \:)
                 (let [key (keyword (subs token 1))
                       value (key params)]
                   (if value
                     (un-quote (json/generate-string value))
                     (throw
                       (IllegalArgumentException.
                         (str "Missing path-parameter " key " for path " s)))))
                 token)))
           (apply str))))

(defn- duplicates [seq]
  (for [[id freq] (frequencies seq)
        :when (> freq 1)] id))

(defn route-lookup-table [swagger]
  (let [entries (for [[path endpoints] (:paths swagger)
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

(defn ->ring-swagger [routes]
  {:paths (->> routes
               (map (fn [[path method info]] [path {method info}]))
               (into (linked/map)))})

;;
;; Endpoint Trasformers
;;

(defn strip-no-doc-endpoints
  "Endpoint transformer, strips all endpoints that have :x-no-doc true."
  [endpoint]
  (if-not (some-> endpoint :x-no-doc true?)
    endpoint))

(defn non-nil-routes [endpoint]
  (or endpoint {}))

;;
;; Public API
;;

(defmulti collect-routes identity)

(defn route-vector-to-route-map [v]
  {:paths (into (linked/map) (concat v))})

(defn path-for*
  "Extracts the lookup-table from request and finds a route by name."
  [route-name request & [params]]
  (let [[path details] (some-> request
                               mw/get-options
                               :lookup
                               route-name
                               first)
        path-params (:params details)]
    (if (seq path-params)
      (->path path params)
      path)))

(defmacro path-for
  "Extracts the lookup-table from request and finds a route by name."
  [route-name & [params]]
  `(path-for* ~route-name ~'+compojure-api-request+ ~params))
