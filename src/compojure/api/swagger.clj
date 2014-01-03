(ns compojure.api.swagger
  (:require [clojure.walk :as walk]
            [ring.util.response :refer :all]
            [clout.core :as clout]
            [clojure.string :as s]
            [clojure.set :refer [union]]
            [compojure.api.common :refer :all]
            [compojure.api.schema :as schema]
            [compojure.core :refer :all]))

;;
;; Evil Global State
;;

(defonce swagger (atom {}))

;;
;; Swagger-json generation
;;

(defrecord Route [method uri])

(defn extract-basepath
  [{:keys [scheme server-name server-port]}]
  (str (name scheme) "://" server-name ":" server-port))

(defn extract-path-parameters [path]
  (-> path clout/route-compile :keys))

(defn swagger-path [path]
  (s/replace path #":([^/]+)" "{$1}"))

(def top-level-keys [:apiVersion])
(def info-keys      [:title :description :termsOfServiceUrl :contact :license :licenseUrl])

(defn api-listing [parameters]
  (response
    (merge
      {:apiVersion "1.0.0"
       :swaggerVersion "1.2"
       :apis (map
               (fn [[api details]]
                 {:path (str "/" (name api))
                  :description (:description details)})
               @swagger)
       :info (select-keys parameters info-keys)}
      (select-keys parameters top-level-keys))))

(defn generate-nick [uri] uri)

(defn api-declaration [details basepath]
  (response
    {:apiVersion "1.0.0"
     :swaggerVersion "1.2"
     :basePath basepath
     :resourcePath "" ;; TODO: should be supported?
     :produces ["application/json"]
     :models (apply schema/transform-models (:models details))
     :apis (map
             (fn [[{:keys [method uri]} {:keys [return summary notes nickname parameters]}]]
               {:path (swagger-path uri)
                :operations
                [{:method (-> method name .toUpperCase)
                  :summary (or summary "")
                  :notes (or notes "")
                  :type (or (name-of return) "json")
                  :nickname (or nickname (generate-nick uri))
                  :parameters (into
                                parameters
                                (map
                                  (fn [path-parameter]
                                    {:name (name path-parameter)
                                     :description ""
                                     :required true
                                     :type "string"
                                     :paramType "path"})
                                  (extract-path-parameters uri)))}]})
             (:routes details))}))

;;
;; Swagger-docs public api
;;

(defn swagger-docs [path & key-values]
  (let [parameters (apply hash-map key-values)]
    (routes
      (GET path [] (api-listing parameters))
      (GET (str path "/:api") {{api :api} :route-params :as request}
        (when-let [details (@swagger (keyword api))]
          (let [basepath (extract-basepath request)]
            (api-declaration details basepath)))))))

;;
;; Compojure-Swagger
;;

(def compojure-route?     #{#'GET #'POST #'PUT #'DELETE #'HEAD #'OPTIONS #'PATCH #'ANY})
(def compojure-context?   #{#'context})
(def compojure-letroutes? #{#'let-routes})
(def compojure-macro?     (union compojure-route? compojure-context? compojure-letroutes?))
(def with-meta?           #{#'with-meta})

(defn- macroexpand-to-compojure [form]
  (walk/prewalk
    (fn [x]
      (if (and (seq? x) (> (count x) 1))
        (do
          (if (and
                (symbol? (first x))
                (compojure-macro? (resolve (first x))))
            (filter (comp not nil?) x)
            (macroexpand-1 x)))
        x))
    form))

(defrecord CompojureRoute [p b])
(defrecord CompojureRoutes [p c])

(defn filter-routes [c]
  (filter #(#{CompojureRoute CompojureRoutes} (class %)) (flatten c)))

(defn collect-compojure-routes [form]
  (walk/postwalk
    (fn [x]
      (or
        (and
          (seq? x)
          (let [[m p] x
                rm (and (symbol? m) (resolve m))]
            (cond
              (with-meta? rm)           (eval x)
              (compojure-route? rm)     (->CompojureRoute p x)
              (compojure-context? rm)   (->CompojureRoutes p  (filter-routes x))
              (compojure-letroutes? rm) (->CompojureRoutes "" (filter-routes x)))))
        x))
    form))

(defn create-api-route [[ks v]]
  [(->Route
     (first (keep second ks))
     (->> ks (map first) (apply str))) v])

(defn extract-method [body]
  (-> body first str .toLowerCase keyword))

(defn create-paths [{:keys [p b c] :as r}]
  (apply array-map
    (condp = (class r)
      CompojureRoute  (let [metadata (merge (meta r) (meta (first b)))
                            new-body [(with-meta (first b) metadata) (rest b)]]
                        [[p (extract-method b)] new-body])
      CompojureRoutes [[p nil] (->> c (map create-paths) ->map)])))

;; TODO: resolve all symbols here!
(defn transform-parameters [parameters]
  (let [parameters (for [{:keys [type] :as parameter} parameters]
                     parameter)]
    (if-not (empty? parameters) parameters)))

(defn route-metadata [body]
  (remove-empty-keys
    (let [{:keys [body return parameters] :as meta} (or (meta (first body)) {})]
      (merge meta {:parameters (transform-parameters parameters)
                   :return (some-> return resolve)}))))

(defn route-definition [[route body]]
  [route (route-metadata body)])

(defn peel [x]
  (or (and (seq? x) (= 1 (count x)) (first x)) x))

(defn extract-routes [body]
  (->> body
    peel
    macroexpand-to-compojure
    collect-compojure-routes
    create-paths
    path-vals
    (map create-api-route)
    (map route-definition)
    reverse
    ->map))

; TODO: don't resolve here
(defn extract-models [routes]
  (let [return-models (->> routes vals (keep :return))
        parameter-models (->> routes vals (mapcat :parameters) (keep :type) (map resolve))]
    (-> return-models
      (into parameter-models)
      set
      vec)))

;;
;; Compojure-Swagger public api
;;

(defmacro swaggered [name & body]
  (let [[parameters body] (extract-fn-parameters body)
        routes  (extract-routes body)
        models  (extract-models routes)
        details (merge parameters {:routes routes
                                   :models models})]
    (swap! swagger assoc name details)
    `(routes ~@body)))
