(ns compojure.api.swagger
  (:require [clojure.set :refer [union]]
            [clojure.string :as st]
            [clojure.walk :as walk]
            [compojure.api.common :refer :all]
            [compojure.api.routes :as routes]
            [compojure.api.meta :as m]
            [compojure.core :refer :all]
            [compojure.api.core :refer [GET*]]
            [ring.util.http-response :refer [ok]]
            [potemkin :refer [import-vars]]
            [ring.swagger.common :refer :all]
            [ring.swagger.core :as swagger]
            [ring.swagger.swagger2 :as swagger2]
            ring.swagger.ui
            [schema.core :as s]))

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

(defrecord CompojureRoute [p m b])
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
  (apply deep-merge (map #(or % {}) meta)))

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
              (compojure-route? rm)     (->CompojureRoute p {} x)
              (compojure-context? rm)   (->CompojureRoutes p (context-metadata x) (filter-routes x))
              (compojure-letroutes? rm) (->CompojureRoutes "" (context-metadata x) (filter-routes x))
              :else                     x)))
        x))
    form))

(defn remove-param-regexes [p] (if (vector? p) (first p) p))

(defn strip-trailing-spaces [s] (st/replace-first s #"(.)\/+$" "$1"))

(defn create-api-route [[ks v]]
  [{:method (keyword (name (first (keep second ks))))
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
               #(dissoc (merge (string-path-parameters uri) %) s/Keyword))
    route-with-meta))

;;
;; generate schema names
;;

(defn ensure-parameter-schema-names [route-with-meta]
  (if (get-in route-with-meta [:metadata :parameters :body])
    (update-in route-with-meta [:metadata :parameters :body]
               #(swagger/with-named-sub-schemas % "Body"))
    route-with-meta))

(defn ensure-return-schema-names [route-with-meta]
  ;; TODO: static ensure?
  route-with-meta)

;;
;; routes
;;

(defn ->swagger2 [route]
  {(:uri route) {(:method route) (dissoc (:metadata route) :method :uri)}})

(defn attach-meta-data-to-route [[{:keys [uri] :as route} [_ {:keys [body meta]}]]]
  (let [meta (merge-meta meta (route-metadata body))
        route-with-meta (if-not (empty? meta) (assoc route :metadata meta) route)]
    (->> route-with-meta
         (ensure-path-parameters uri)
         ensure-parameter-schema-names
         ensure-return-schema-names
         ->swagger2)))

(defn peel [x]
  (or (and (seq? x) (= 1 (count x)) (first x)) x))

(defn ensure-routes-in-root [body]
  (if (seq? body)
    (->CompojureRoutes "" {} (filter-routes body))
    body))

(defn remove-hidden-routes [routes]
  (remove (fn [route] (some-> route vals first vals first :hidden true?)) routes))

(defn extract-routes [body]
  (->> body
       peel
       macroexpand-to-compojure
       collect-compojure-routes
       ensure-routes-in-root
       (create-paths {})
       (apply array-map)
       path-vals
       (map create-api-route)
       (map attach-meta-data-to-route)
       remove-hidden-routes
       (apply deep-merge {})))

(defn swagger-info [body]
  (let [[parameters body] (extract-parameters body)
        routes  (extract-routes body)
        details (assoc parameters :paths routes)]
    [details body]))

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
        ;; TODO: to swagger2.
        parameters (apply hash-map key-values)]
    `(routes
       (GET* ~path {:as request#}
         :hidden true
         (let [produces# (-> request# :meta :produces (or []))
               consumes# (-> request# :meta :consumes (or []))
               parameters# {:produces produces#
                            :consumes consumes#}]
           (ok
             (let [swagger# (merge parameters#
                                   (~routes/+compojure-api-routes+ "default"))
                   result# (swagger2/swagger-json swagger#)]
               result#)))))))

(defmacro swaggered
  "Defines a swagger-api. Takes api-name, optional
   Keyword value pairs or a single Map for meta-data
   and a normal route body. Macropeels the body and
   extracts route, model and endpoint meta-datas."
  [api-name & body]
  (let [[_ body] (extract-parameters body)]
    `(let-routes [] (constantly nil)
       (compojure.api.meta/meta-container
         {:tags [~(keyword api-name)]}
         (routes ~@body)))))

(defmethod routes/collect-routes :default [body]
  (swagger-info body))
