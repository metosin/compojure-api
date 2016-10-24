(ns compojure.api.routes
  (:require [compojure.core :refer :all]
            [clojure.string :as string]
            [cheshire.core :as json]
            [compojure.api.middleware :as mw]
            [compojure.api.impl.logging :as logging]
            [compojure.api.common :as common]
            [ring.swagger.common :as rsc]
            [clojure.string :as str]
            [linked.core :as linked]
            [compojure.response]
            [schema.core :as s])
  (:import (clojure.lang AFn IFn Var)))

;;
;; Route records
;;

(defn- ->path [path]
  (if-not (= path "/") path))

(defn- ->paths [p1 p2]
  (->path (str (->path p1) (->path p2))))

(defprotocol Routing
  (-get-routes [handler options]))

(extend-protocol Routing
  nil
  (-get-routes [_ _] [])
  Var
  (-get-routes [this options]
    (-get-routes @this options)))

(defn filter-routes [{:keys [childs] :as handler} {:keys [invalid-routes-fn]}]
  (let [[valid-childs invalid-childs] (common/group-with (partial satisfies? Routing) childs)]
    (when (and invalid-routes-fn invalid-childs)
      (invalid-routes-fn handler invalid-childs))
    valid-childs))

(defn get-routes
  ([handler]
   (get-routes handler nil))
  ([handler options]
   (mapv
     (fn [route]
       (update-in route [0] (fn [uri] (if (str/blank? uri) "/" uri))))
     (-get-routes handler options))))

(defrecord Route [path method info childs handler]
  Routing
  (-get-routes [this options]
    (let [valid-childs (filter-routes this options)]
      (if (seq childs)
        (vec
          (for [[p m i] (mapcat #(-get-routes % options) valid-childs)]
            [(->paths path p) m (rsc/deep-merge info i)]))
        (into [] (if path [[path method info]])))))

  compojure.response/Renderable
  (render [_ {:keys [uri request-method]}]
    (throw
      (ex-info
        (str "\ncompojure.api.routes/Route can't be returned from endpoint "
             (-> request-method name str/upper-case) " \"" uri "\". "
             "For nested routes, use `context` instead: (context \"path\" []  ...)\n")
        {:request-method request-method
         :path path
         :method method
         :uri uri})))

  IFn
  (invoke [_ request]
    (handler request))
  (applyTo [this args]
    (AFn/applyToHelper this args)))

(defn create [path method info childs handler]
  (->Route path method info childs handler))

;;
;; Invalid route handlers
;;

(defn fail-on-invalid-child-routes
  [handler invalid-childs]
  (throw (ex-info "Not all child routes satisfy compojure.api.routing/Routing."
                  (merge (select-keys handler [:path :method]) {:invalid (vec invalid-childs)}))))

(defn log-invalid-child-routes [handler invalid-childs]
  (logging/log! :warn (str "Not all child routes satisfy compojure.api.routing/Routing. "
                           (select-keys handler [:path :method]) ", invalid child routes: "
                           (vec invalid-childs))))


;;
;; Swagger paths
;;

(defn- path-params
  "Finds path-parameter keys in an uri.
  Regex copied from Clout and Ring-swagger."
  [s]
  (map (comp keyword second) (re-seq #":([\p{L}_][\p{L}_0-9-]*)" s)))

(defn- string-path-parameters [uri]
  (let [params (path-params uri)]
    (if (seq params)
      (zipmap params (repeat String)))))

(defn- ensure-path-parameters [path info]
  (if (seq (path-params path))
    (update-in info [:parameters :path] #(dissoc (merge (string-path-parameters path) %) s/Keyword))
    info))

(defn ring-swagger-paths [routes]
  {:paths
   (reduce
     (fn [acc [path method info]]
       (update-in
         acc [path method]
         (fn [old-info]
           (let [info (or old-info info)]
             (ensure-path-parameters path info)))))
     (linked/map)
     routes)})

;;
;; Route lookup
;;

(defn- duplicates [seq]
  (for [[id freq] (frequencies seq)
        :when (> freq 1)] id))

(defn route-lookup-table [routes]
  (let [entries (for [[path endpoints] (-> routes ring-swagger-paths :paths)
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
      (throw (ex-info
               (str "Found multiple routes with same name: "
                    (string/join "," duplicate-route-names))
               {:entries entries})))
    (into {} entries)))

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
