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

(defn api-declaration [details basepath]
  (response
    {:apiVersion "1.0.0"
     :swaggerVersion "1.2"
     :basePath basepath
     :resourcePath "" ;; TODO: should be supported?
     :produces ["application/json"]
     :models (apply schema/transform-models (:models details))
     :apis (map
             (fn [[{:keys [method uri]} {:keys [return]}]]
               {:path (swagger-path uri)
                :operations
                [{:method (-> method name .toUpperCase)
                  :summary ""
                  :notes ""
                  :type (or (name-of return) "json")
                  :nickname uri
                  :parameters (map
                                (fn [path-parameter]
                                  {:name (name path-parameter)
                                   :description ""
                                   :required true
                                   :type "string"
                                   :paramType "path"})
                                (extract-path-parameters uri)
                                )}]})
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

(defn- macroexpand-to-compojure [form]
  (walk/postwalk
    (fn [x]
      (if (and (seq? x) (> (count x) 1))
        (do
          (if (and
                (symbol? (first x))
                (compojure-macro? (resolve (first x))))
            (filter (comp not nil?) x)
            (macroexpand x)))
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
              (compojure-route? rm)     (->CompojureRoute  p  x)
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
      CompojureRoute  [[p (extract-method b)] b]
      CompojureRoutes [[p nil] (->> c (map create-paths) ->map)])))

(defn extract-return-model [body]
  (some-> body first meta :return resolve))

(defn route-definition [[route body]]
  [route (remove-empty-keys {:return (extract-return-model body)})])

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

(defn extract-parameters [form]
  (let [parameters (->> form (take-while (comp not list?)) (apply hash-map))
        form (drop (* 2 (count parameters)) form)]
    [parameters form]))

(defn extract-models [routes]
  (->> routes vals (keep :return) set vec))

;;
;; Compojure-Swagger public api
;;

(defmacro swaggered [name & body]
  (let [[parameters body] (extract-parameters body)
        routes  (extract-routes body)
        models  (extract-models routes)
        details (merge parameters {:routes routes
                                   :models models})]
    (println routes)
    (swap! swagger assoc name details)
    `(routes ~@body)))
