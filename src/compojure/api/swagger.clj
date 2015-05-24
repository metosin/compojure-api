(ns compojure.api.swagger
  (:require [clojure.set :refer [union]]
            [clojure.string :as cs]
            [clojure.walk :as walk]
            [compojure.api.common :refer :all]
            [compojure.api.routes :as routes]
            [compojure.api.meta :as m]
            [compojure.core :refer :all]
            [compojure.api.core :refer [GET*]]
            [compojure.api.middleware :as mw]
            [ring.util.http-response :refer [ok]]
            [schema-tools.core :as st]
            [ring.swagger.swagger2-schema :as ss]
            [potemkin :refer [import-vars]]
            [ring.swagger.common :refer :all]
            [ring.swagger.middleware :as rsm]
            [ring.swagger.core :as swagger]
            [ring.swagger.ui]
            [ring.swagger.swagger2 :as swagger2]
            [schema.core :as s]))

;;
;; Source Linking
;;

(defn- purge-symbol-or-var-meta [x]
  (if (symbol? x)
    (let [value (value-of (eval-re-resolve x))
          naked (if (var? value) (var-get value) value)]
      (meta naked))))

(defn- inline? [x] (:inline (purge-symbol-or-var-meta x)))
(defn- extract-source [x] (:source (purge-symbol-or-var-meta x)))

;;
;; Route peeling
;;

; TODO: #'wrap-routes
(def compojure-route?     #{#'GET #'POST #'PUT #'DELETE #'HEAD #'OPTIONS #'PATCH #'ANY})
(def compojure-context?   #{#'context})
(def compojure-letroutes? #{#'let-routes})
(def compojure-macro?     (union compojure-route? compojure-context? compojure-letroutes?))

(defn macroexpand-to-compojure [form]
  (walk/prewalk
    (fn [x]
      (cond
        (inline? x) (extract-source x)
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

; TODO: shoudn't eval code at compile-time
(defn parse-meta-data [container]
  (when-let [meta (m/unwrap-meta-container container)]
    (remove-empty-keys (eval meta))))

(defn route-metadata [body]
  (parse-meta-data (first (drop 2 body))))

(defn context-metadata [body]
  (parse-meta-data (first (drop 3 body))))

(defn merge-meta [& meta]
  (apply deep-merge (map #(or % {}) meta)))

;; this is needed in order for the swaggered-macro to work, can be
;; removed when it's removed.
(defn consume-empty-paths [routes]
  (reduce
    (fn [routes route]
      (if (and (is-a? CompojureRoutes route) (= (:p route) ""))
        (let [route-meta (:m route)
              childs (:c route)
              childs-with-meta (map
                                 #(update-in
                                   % [:m]
                                   (fn [m]
                                     (merge-meta route-meta (or m {}))))
                                 childs)]
          (into routes childs-with-meta))
        (conj routes route))) [] routes))

(defn filter-routes [c]
  (consume-empty-paths
    (filterv #(or (is-a? CompojureRoute %)
                  (is-a? CompojureRoutes %)) (flatten c))))

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

(defn strip-trailing-spaces [s] (cs/replace-first s #"(.)\/+$" "$1"))

(defn create-api-route [[ks v]]
  [{:method (keyword (name (first (keep second ks))))
    :uri (->> ks (map first) (map remove-param-regexes) cs/join strip-trailing-spaces)} v])

(defn extract-method [body]
  (-> body first str .toLowerCase keyword))

(defn create-paths [m {:keys [p b c] :as r}]
  (cond

    (is-a? CompojureRoute r)
    [[p (extract-method b)]
     [:endpoint {:meta (merge-meta m (:m r))
                 :body (rest b)}]]

    (is-a? CompojureRoutes r)
    [[p nil] (->> c
                  (map (partial create-paths (merge-meta m (:m r))))
                  (reduce (fn [acc [k v]]
                            (assoc-map-ordered acc k (if (get acc k)
                                                       ;; match first compojure route
                                                       (deep-merge v (get acc k))
                                                       v)))
                          (array-map)))]))

;;
;; ensure path parameters
;;

(defn path-params [s]
  (map (comp keyword second) (re-seq #":(.[^:|(/]*)[/]?" s)))

(defn string-path-parameters [uri]
  (let [params (path-params uri)]
    (if (seq params)
      (zipmap params (repeat String)))))

(defn- remove-keyword-namespaces [schema]
  (walk/prewalk
    (fn [x]
      (if (and (keyword? x) (namespace x))
        (keyword (name x))
        x))
    schema))

(defn ensure-path-parameters [uri route-with-meta]
  (if (seq (path-params uri))
    (update-in route-with-meta [:metadata :parameters :path]
               #(dissoc (merge (string-path-parameters uri)
                               (remove-keyword-namespaces %))
                        s/Keyword))
    route-with-meta))

;;
;; generate schema names
;;

(defn ensure-parameter-schema-names [route-with-meta]
  (if (get-in route-with-meta [:metadata :parameters :body])
    (update-in route-with-meta [:metadata :parameters :body]
               #(swagger/with-named-sub-schemas
                 (remove-keyword-namespaces %) "Body"))
    route-with-meta))

(defn ensure-return-schema-names [route-with-meta]
  (if (get-in route-with-meta [:metadata :responses])
    (update-in
      route-with-meta [:metadata :responses]
      (fn [responses]
        (into {} (map
                   (fn [[k v]]
                     [k (update-in v [:schema] swagger/with-named-sub-schemas "Response")])
                   responses))))
    route-with-meta))

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
       (apply deep-merge {})))

(defn swagger-info [body]
  [{:paths (extract-routes body)} body])

(defn- deprecated! [& args]
  (apply println (concat ["DEPRECATED:"] args)))

(defn base-path [request]
  (let [context (swagger/context request)]
    (if (= "" context) "/" context)))

;;
;; Public api
;;

(import-vars [ring.swagger.ui swagger-ui])

(defn select-swagger2-parameters
  "Validates the given Swagger 2.0 format against the Schema. Prints warnings to STDOUT
  if old input was used. Fails with missing 2.0 keys."
  [info]
  (let [mapping {:version [:info :version]
                 :title [:info :title]
                 :description [:info :description]
                 :termsOfServiceUrl [:info :termsOfService]
                 :license [:info :license :name]}
        old-keys (set (keys mapping))
        info (reduce
               (fn [info [k v]]
                 (if (old-keys k)
                   (do
                     (deprecated! "swagger-docs -" k "is deprecated, see docs for details.")
                     (-> info
                         (dissoc k)
                         (assoc-in (mapping k) v)))
                   info))
               info
               info)
        Schema (-> ss/Swagger
                   (st/select-keys [:info s/Keyword])
                   (st/assoc (s/optional-key :tags)
                             [{:name (s/either s/Str s/Keyword)
                               (s/optional-key :description) s/Str
                               ss/X- s/Any}]))]
    (st/select-schema Schema info)))

(defmacro swagger-docs
  "Route to serve the swagger api-docs. If the first
   parameter is a String, it is used as a url for the
   api-docs, otherwise \"/swagger.json\" will be used.
   Next Keyword value pairs OR a map for meta-data.
   Meta-data can be any valid swagger 2.0 data. Common
   case is to introduce API Info and Tags here:

       {:info {:version \"1.0.0\"
               :title \"Sausages\"
               :description \"Sausage description\"
               :termsOfService \"http://helloreverb.com/terms/\"
               :contact {:name \"My API Team\"
                         :email \"foo@example.com\"
                         :url \"http://www.metosin.fi\"}
               :license {:name: \"Eclipse Public License\"
                         :url: \"http://www.eclipse.org/legal/epl-v10.html\"}}
        :tags [{:name \"sausages\", :description \"Sausage api-set}]}"
  [& body]
  (let [[path key-values] (if (string? (first body))
                            [(first body) (rest body)]
                            ["/swagger.json" body])
        first-value (first key-values)
        extra-info (select-swagger2-parameters (if (map? first-value)
                                                 first-value
                                                 (apply hash-map key-values)))]
    `(routes
       (GET* ~path {:as request#}
         :no-doc true
         :name ::swagger
         (let [runtime-info# (rsm/get-swagger-data request#)
               base-path# {:basePath (base-path request#)}
               options# (:ring-swagger (mw/get-options request#))]
           (ok
             (let [swagger# (merge runtime-info#
                                   ~extra-info
                                   base-path#)
                   result# (swagger2/swagger-json swagger# options#)]
               result#)))))))

(defmacro swaggered
  "DEPRECATED. Use context* with :tags instead:
    (context* \"/api\"
      :tags [\"api\"]
      ...)"
  [api-name & body]
  (deprecated! "swaggered is deprecated and removed soon, see docs for details.")
  (let [[_ body] (extract-parameters body)]
    `(let-routes [] (constantly nil)
       (compojure.api.meta/meta-container
         {:tags [~(keyword api-name)]}
         (routes ~@body)))))

(defmethod routes/collect-routes :default [body]
  (swagger-info body))

(defn swagger-spec-path [api]
  (some-> api meta :lookup ::swagger first first))

(defn swagger-api? [api]
  (boolean (swagger-spec-path api)))
