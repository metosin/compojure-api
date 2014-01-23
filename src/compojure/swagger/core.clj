(ns compojure.swagger.core
  (:require [clojure.walk :as walk]
            [clojure.string :as s]
            [ring.util.response :refer :all]
            [clojure.set :refer [union]]
            [ring.swagger.common :refer :all]
            [ring.swagger.core :as swagger]
            [compojure.route :as route]
            [compojure.core :refer :all]))

;;
;; Evil Global State
;;

(defonce swagger (atom {}))

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
      (if (seq? x)
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
  [(swagger/->Route
     (first (keep second ks))
     (->> ks (map first) (apply str))
     {}) v])

(defn extract-method [body]
  (-> body first str .toLowerCase keyword))

(defn create-paths [{:keys [p b c] :as r}]
  (apply array-map
    (condp = (class r)
      CompojureRoute  (let [route-meta (meta r)
                            method-meta (meta (first b))
                            parameter-meta (first (extract-parameters (drop 3 b)))
                            metadata (merge route-meta method-meta parameter-meta)
                            new-body [(with-meta (first b) metadata) (rest b)]]
                        [[p (extract-method b)] new-body])
      CompojureRoutes [[p nil] (->> c (map create-paths) ->map)])))

(defn transform-parameters [parameters]
  (let [parameters (map swagger/purge-model-vars parameters)]
    (if-not (empty? parameters) parameters)))

(defn route-metadata [body]
  (remove-empty-keys
    (let [{:keys [body return parameters] :as meta} (or (meta (first body)) {})]
      (merge meta {:parameters (transform-parameters parameters)
                   :return (some-> return swagger/purge-model-var)}))))

(defn attach-meta-data-to-route [[route body]]
  (assoc route :metadata (route-metadata body)))

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
    (map attach-meta-data-to-route)
    reverse))

(defn extract-models [routes]
  (let [return-models (->> routes (map :metadata) (keep :return))
        parameter-models (->> routes (map :metadata) (mapcat :parameters) (keep :type))]
    (-> return-models
      (into parameter-models)
      distinct
      vec)))

(defn path-to-index [path] (s/replace (str path "/index.html") #"//" "/"))

;;
;; Compojure-Swagger public api
;;

(defn swagger-ui
  ([] (swagger-ui "/"))
  ([path]
    (routes
      (GET path [] (redirect (path-to-index path)))
      (route/resources path {:root "swagger-ui"}))))

(defn swagger-docs [path & key-values]
  (let [parameters (apply hash-map key-values)]
    (routes
      (GET path []
        (swagger/api-listing parameters @swagger))
      (GET (str path "/:api") {{api :api} :route-params :as request}
        (if-let [details (@swagger (name api))]
          (swagger/api-declaration parameters (swagger/extract-basepath request) details))))))

(defmacro swaggered [name & body]
  (let [[parameters body] (extract-parameters body)
        routes  (extract-routes body)
        models  (extract-models routes)
        details (merge parameters {:routes routes
                                   :models models})]
    (swap! swagger assoc name details)
    `(routes ~@body)))
