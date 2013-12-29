(ns compojure.api.swagger
  (:require [clojure.walk :as walk]
            [ring.util.response :refer :all]
            [clout.core :as clout]
            [clojure.string :as s]
            [clojure.set :refer [union]]
            [clojure.pprint :refer :all]
            [compojure.api.common :refer :all]
            [compojure.core :refer :all]))

;;
;; Evil Global State
;;

(defonce swagger (atom {}))

;;
;; Swagger-json generation
;;

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
     ;;:models []
     :apis (map
             (fn [[path method]]
               {:path (swagger-path path)
                :operations
                [{:method (-> method name .toUpperCase)
                  :summary ""
                  :notes ""
                  :type "json"
                  :nickname path
                  :parameters (map
                                (fn [path-parameter]
                                  {:name (name path-parameter)
                                   :description ""
                                   :required true
                                   :type "string"
                                   :paramType "path"})
                                (extract-path-parameters path)
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
      (if (seq? x)
        (do
          (if (and
                (symbol? (first x))
                (compojure-macro? (resolve (first x))))
            (filter (comp not nil?) x)
            (macroexpand x)))
        x))
    form))

(defrecord Route [p b])
(defrecord Routes [p c])

(defn filter-routes [c]
  (filter #(#{Route Routes} (class %)) (flatten c)))

(defn collect-compojure-routes [form]
  (walk/postwalk
    (fn [x]
      (or
        (and
          (seq? x)
          (let [[m p] x
                rm (and (symbol? m) (resolve m))]
            (cond
              (compojure-route? rm)     (->Route  p  x)
              (compojure-context? rm)   (->Routes p  (filter-routes x))
              (compojure-letroutes? rm) (->Routes "" (filter-routes x)))))
        x))
    form))

(defn merge-path-vals [v]
  (map (fn [[ks v]] [(apply str ks) v]) v))

; FIXME: Route body should not be walked, recur smartly
(defn create-paths [m]
  (apply array-map
    (walk/postwalk
      (map-defn [{:keys [p b c] :as x}]
        (cond
          b [p b]
          c [p (->map c)]
          :else x))
      m)))

(defn peel [x] (or (and (seq? x) (= 1 (count x)) (first x)) x))

(defn get-routes [body]
  (->> body
    peel
    macroexpand-to-compojure
    collect-compojure-routes
    prewalk-record->map
    create-paths
    path-vals
    merge-path-vals
    ->map))

(defn extract-parameters [form]
  (let [parameters (->> form (take-while (comp not list?)) (apply hash-map))
        form (drop (* 2 (count parameters)) form)]
    [parameters form]))

(defn extract-method [body]
  (-> body first str .toLowerCase keyword))

;;
;; Compojure-Swagger public api
;;

(defmacro swaggered [name & body]
  (let [[parameters body] (extract-parameters body)
        routes  (get-routes body)
        _       (doseq [[method :as route] (vals routes)] (println route "\n ->" (meta method)))
        routes  (->map (for [[p b] routes] [p (extract-method b)]))
        details (assoc parameters :routes routes)]
    (println details)
    (swap! swagger assoc name details)
    `(routes ~@body)))
