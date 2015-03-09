(ns compojure.api.swagger
  (:require [clojure.set :refer [union]]
            [clojure.string :as st]
            [clojure.walk :as walk]
            [compojure.api.common :refer :all]
            [compojure.api.routes :as routes]
            [compojure.api.meta :as m]
            [compojure.core :refer :all]
            [plumbing.core :refer [fn->]]
            [potemkin :refer [import-vars]]
            [ring.swagger.common :refer :all]
            [ring.swagger.core :as swagger]
            [ring.swagger.impl :as swagger-impl]
            [ring.swagger.schema :as schema]
            ring.swagger.ui
            [schema.core :as s]))

;;
;; Schema helpers
;;

(defn direct-or-contained [f x]
  (if (swagger-impl/valid-container? x) (f (first x)) (f x)))

;;
;; Route peeling
;;

; TODO: #'wrap-routes
(def compojure-route?     #{#'GET #'POST #'PUT #'DELETE #'HEAD #'OPTIONS #'PATCH #'ANY})
(def compojure-context?   #{#'context})
(def compojure-letroutes? #{#'let-routes})
(def compojure-macro?     (union compojure-route? compojure-context? compojure-letroutes?))

(defn inline? [x] (and (symbol? x) (-> x eval-re-resolve value-of meta :inline)))

(defn macroexpand-to-compojure [form]
  (walk/prewalk
    (fn [x]
      (cond
        (inline? x) (-> x value-of meta :source) ;; resolve the syms!
        (seq? x)    (let [sym (first x)]
                      (if (and
                            (symbol? sym)
                            (or
                              (compojure-macro? (eval-re-resolve sym))
                              (m/meta-container? (eval-re-resolve sym))))
                        (filter (comp not nil?) x)
                        (let [result (macroexpand-1 x)]
                          ;; stop if macro expands to itself
                          (if (= result x) result (list result)))))
        :else x))
    form))

(defrecord CompojureRoute [p b])
(defrecord CompojureRoutes [p m c])

(defn is-a?
  "like instanceof? but compares .toString of a classes"
  [c x] (= (str c) (str (class x))))

(defn parse-meta-data [container]
  (when-let [meta (m/unwrap-meta-container container)]
    (let [meta (update-in meta [:return] eval)
          meta (reduce
                (fn [acc x]
                  (update-in acc [:parameters x] eval))
                meta
                (-> meta :parameters keys))]
      (remove-empty-keys meta))))

(defn route-metadata [body]
  (parse-meta-data (first (drop 2 body))))

(defn context-metadata [body]
  (parse-meta-data (first (drop 3 body))))

(defn merge-meta [& meta]
  (apply deep-merge meta))

(defn filter-routes [c]
  (filterv #(or (is-a? CompojureRoute %)
                (is-a? CompojureRoutes %)) (flatten c)))

(defn collect-compojure-routes [form]
  (walk/postwalk
    (fn [x]
      (or
        (and
          (seq? x)
          (let [[m p] x
                rm (and (symbol? m) (eval-re-resolve m))]
            (cond
              (compojure-route? rm)     (->CompojureRoute p x)
              (compojure-context? rm)   (->CompojureRoutes p (context-metadata x) (filter-routes x))
              (compojure-letroutes? rm) (->CompojureRoutes "" (context-metadata x) (filter-routes x))
              :else                     x)))
        x))
    form))

(defn remove-param-regexes [p] (if (vector? p) (first p) p))

(defn strip-trailing-spaces [s] (st/replace-first s #"(.)\/+$" "$1"))

(defn create-api-route [[ks v]]
  [{:method (keyword (.getName (first (keep second ks))))
    :uri (->> ks (map first) (map remove-param-regexes) st/join strip-trailing-spaces)} v])

(defn extract-method [body]
  (-> body first str .toLowerCase keyword))

(defn create-paths [m {:keys [p b c] :as r}]
  (cond
    (is-a? CompojureRoute r)  [[p (extract-method b)] [:endpoint {:meta m :body (rest b)}]]
    (is-a? CompojureRoutes r) [[p nil] (reduce (partial apply assoc-map-ordered) {}
                                               (map (partial
                                                     create-paths
                                                     (merge-meta m (:m r))) c))]))

;;
;; ensure path parameters
;;

(defn path-params [s]
  (map (comp keyword second) (re-seq #":(.[^:|(/]*)[/]?" s)))

(defn string-path-parameters [uri]
  (let [params (path-params uri)]
    (if (seq params)
      (zipmap params (repeat String)))))

(defn ensure-path-parameters [uri route-with-meta]
  (if (seq (path-params uri))
    (update-in route-with-meta [:metadata :parameters :path]
               (partial merge (string-path-parameters uri)))
    route-with-meta))

;;
;; generate schema names
;;

(defn with-name [type schema]
  (if schema
    (swagger-impl/update-schema
     schema
     #(s/schema-with-name % (gensym (->CamelCase (name type)))))))

(defn ensure-parameter-schema-names [route-with-meta]
  (reduce
   (fn [acc type]
     (if (get-in acc [:metadata :parameters type])
       (update-in
        acc [:metadata :parameters type]
        (partial with-name type))
       acc))
   route-with-meta [:path :body :query :header]))

(defn ensure-return-schema-names [route-with-meta]
  (if-let [return (get-in route-with-meta [:metadata :return])]
    (if-not (or (direct-or-contained schema/named-schema? return)
                (direct-or-contained (comp not map?) return))
      (update-in
       route-with-meta [:metadata :return]
       swagger-impl/update-schema
       (partial with-name "return"))
      route-with-meta)
    route-with-meta))

;;
;;
;;

(defn attach-meta-data-to-route [[{:keys [uri] :as route} [_ {:keys [body meta]}]]]
  (let [meta (merge-meta meta (route-metadata body))
        route-with-meta (if-not (empty? meta) (assoc route :metadata meta) route)]
    (->> route-with-meta
         (ensure-path-parameters uri)
         ensure-parameter-schema-names
         ensure-return-schema-names)))

(defn peel [x]
  (or (and (seq? x) (= 1 (count x)) (first x)) x))

(defn ensure-routes-in-root [body]
  (if (seq? body)
    (->CompojureRoutes "" {} (filter-routes body))
    body))

(defn extract-routes [body]
  (->> body
       peel
       macroexpand-to-compojure
       collect-compojure-routes
       #_(#(do (println "*********") (./aprint %) %))
       ensure-routes-in-root
       (create-paths {})
       (apply array-map)
       path-vals
       (map create-api-route)
       (mapv attach-meta-data-to-route)
       reverse))

(defn swagger-info [body]
  (let [[parameters body] (extract-parameters body)
        routes  (extract-routes body)
        details (assoc parameters :routes routes)]
    [details body]))

(defn convert-parameters-to-swagger-12 [routes]
  (let [->12parameter (fn [[k v]] {:type k :model v})]
    (into
     (empty routes)
     (for [[name info] routes]
       [name (update-in
              info [:routes]
              (fn [routes]
                (mapv
                 (fn [route]
                   (update-in
                    route [:metadata :parameters]
                    (partial mapv ->12parameter)))
                 routes)))]))))
;;
;; Public api
;;

(import-vars [ring.swagger.ui swagger-ui])

(defmacro swagger-docs
  "Route to serve the swagger api-docs. If the first
   parameter is a String, it is used as a url for the
   api-docs, othereise \"/api/api-docs\" will be used.
   Next Keyword value pairs for meta-data. Valid keys:

   :title :description :termsOfServiceUrl
   :contact :license :licenseUrl"
  [& body]
  (let [[path key-values] (if (string? (first body))
                            [(first body) (rest body)]
                            ["/api/api-docs" body])
        parameters (apply hash-map key-values)]
    `(routes
      (GET ~path []
        (swagger/api-listing ~parameters @~routes/+routes-sym+))
      (GET ~(str path "/:api") {{api# :api} :route-params :as request#}
        (let [produces# (-> request# :meta :produces (or []))
              consumes# (-> request# :meta :consumes (or []))
              parameters# (merge ~parameters {:produces produces#
                                              :consumes consumes#})]
          (swagger/api-declaration
            parameters#
            (convert-parameters-to-swagger-12  @~routes/+routes-sym+)
            api#
            (swagger/basepath request#)))))))

(defmacro swaggered
  "Defines a swagger-api. Takes api-name, optional
   Keyword value pairs or a single Map for meta-data
   and a normal route body. Macropeels the body and
   extracts route, model and endpoint meta-datas."
  [name & body]
  (let [[details body] (swagger-info body)
        name (st/replace (str (eval name)) #" " "")]
    `(do
       (swap! ~routes/+routes-sym+ assoc-map-ordered ~name '~details)
       (routes ~@body))))
