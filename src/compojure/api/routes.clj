(ns compojure.api.routes
  (:require [compojure.core :refer :all]
            [clojure.string :as string]
            [cheshire.core :as json]
            [compojure.api.middleware :as mw]
            [compojure.api.impl.logging :as logging]
            [ring.swagger.common :as rsc]
            [clojure.string :as str]
            [linked.core :as linked]
            [schema.core :as s])
  (:import [clojure.lang AFn IFn]))

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

(defn- path-params [s]
  (map (comp keyword second) (re-seq #":(.[^:|(/]*)[/]?" s)))

(defn- string-path-parameters [uri]
  (let [params (path-params uri)]
    (if (seq params)
      (zipmap params (repeat String)))))

(defn- ensure-path-parameters [path info]
  (if (seq (path-params path))
    (update-in info [:parameters :path] #(dissoc (merge (string-path-parameters path) %) s/Keyword))
    info))

(defn ->ring-swagger [routes]
  {:paths (reduce
            (fn [acc [path method info]]
              (update-in
                acc [path method]
                (fn [old-info]
                  (let [info (or old-info info)]
                    (ensure-path-parameters path info)))))
            (linked/map)
            routes)})

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
;; Bidirectional-routing
;;

(defn- un-quote [s]
  (str/replace s #"^\"(.+(?=\"$))\"$" "$1"))

(defn- path-string [s params]
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
      (path-string path params)
      path)))

(defmacro path-for
  "Extracts the lookup-table from request and finds a route by name."
  [route-name & [params]]
  `(path-for* ~route-name ~'+compojure-api-request+ ~params))

;;
;; Route records
;;

(def ^:dynamic *fail-on-missing-route-info* false)

(defprotocol Routing
  (get-routes [handler]))

(defn- ->path [path]
  (if-not (= path "/") path))

(defn- ->paths [p1 p2]
  (->path (str p1 (->path p2))))

(defrecord Route [path method info childs handler]
  Routing
  (get-routes [_]
    (if (seq childs)
      (vec
        (for [[p m i] (mapcat get-routes (filter (partial satisfies? Routing) childs))]
          [(->paths path p) m (rsc/deep-merge info i)]))
      [[path method info]]))

  IFn
  (invoke [_ request]
    (handler request))
  (applyTo [this args]
    (AFn/applyToHelper this args)))

(defn create [path method info childs handler]
  (when-let [invalid-childs (seq (remove (partial satisfies? Routing) childs))]
    (let [message "Not all child routes satisfy compojure.api.routing/Routing."
          data {:path path
                :method method
                :info info
                :childs childs
                :invalid invalid-childs}]
      (if *fail-on-missing-route-info*
        (throw (ex-info message data))
        (logging/log! :warn message data))))
  (->Route path method info childs handler))
