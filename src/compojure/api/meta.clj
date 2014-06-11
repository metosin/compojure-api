(ns compojure.api.meta
  (:require [compojure.api.common :refer :all]
            [compojure.core :refer [routes]]
            [ring.util.response :as response]
            [plumbing.core :refer [letk]]
            [plumbing.fnk.impl :as fnk-impl]
            [ring.swagger.core :as swagger]
            [ring.swagger.schema :as schema]
            [ring.swagger.common :refer :all]
            [schema.core :as s]
            [clojure.walk16 :refer [keywordize-keys]]
            [clojure.tools.macro :refer [name-with-attributes]]))

(def +compojure-api-request+
  "lexically bound ring-request for handlers."
  '+compojure-api-request+)

(defn strict [schema]
  (dissoc schema 'schema.core/Keyword))

(defn fnk-schema [bind]
  (:input-schema
    (fnk-impl/letk-input-schema-and-body-form
      nil (with-meta bind {:schema s/Any}) [] nil)))

(defn resolve-model-var [model]
  (swagger/resolve-model-var
    (if (or (set? model) (sequential? model))
      (first model)
      model)))

(defn body-coercer-middleware [handler model]
  (fn [request]
    (if-let [response (handler request)]
      (assoc response :body (schema/coerce! model (:body response))))))

;;
;; Extension point
;;

(defmulti restructure-param
  "Restructures a key value pair in smart routes. By default the key
   is consumed form the :parameters map in acc. k = given key, v = value."
  (fn [k v acc] k))

(defmethod restructure-param :default
  [k v acc]
  "By default assoc in the k v to acc"
  (update-in acc [:parameters] assoc k v))

;;
;; Default implementation
;;

(defmethod restructure-param :return
  [k model {:keys [body] :as acc}]
  "Defines a return type and coerced the return value of a body against it."
  (-> acc
      (update-in [:parameters] assoc k (swagger/resolve-model-vars model))
      (update-in [:middlewares] conj `(body-coercer-middleware ~model))))

(defmethod restructure-param :body
  [_ [value model model-meta] acc]
  "reads body-params into a enchanced let. First parameter is the let symbol,
   second is the Model to coerced! against, third parameter is optional meta-
   data for the model. Examples:
   :body [user User]
   :body [user User {:key \"value\""
  (let [model-var (resolve-model-var model)]
    (-> acc
        (update-in [:lets] into [value `(schema/coerce!
                                          ~model
                                          (:body-params ~+compojure-api-request+)
                                          :json)])
        (update-in [:parameters :parameters] conj {:type :body
                                                   :model (swagger/resolve-model-vars model)
                                                   :meta model-meta}))))

(defmethod restructure-param :query
  [_ [value model model-meta] acc]
  "reads query-params into a enchanced let. First parameter is the let symbol,
   second is the Model to coerced! against, third parameter is optional meta-
   data for the model. Examples:
   :query [user User]
   :query [user User {:key \"value\""
  (let [model-var (resolve-model-var model)]
    (-> acc
        (update-in [:lets] into [value `(schema/coerce!
                                          ~model
                                          (keywordize-keys
                                            (:query-params ~+compojure-api-request+))
                                          :query)])
        (update-in [:parameters :parameters] conj {:type :query
                                                   :model (swagger/resolve-model-vars model)
                                                   :meta model-meta}))))

(defmethod restructure-param :body-params
  [_ body-params acc]
  "restructures body-params with plumbing letk notation. Generates
   synthetic defs for the models. Example:
   :body-params [id :- Long name :- String]"
  (let [schema (strict (fnk-schema body-params))
        model-name (gensym "body-")
        _ (eval `(schema/defmodel ~model-name ~schema))
        coerced-model (gensym)]
    (-> acc
        (update-in [:lets] into [coerced-model `(schema/coerce!
                                                  ~schema
                                                  (:body-params ~+compojure-api-request+)
                                                  :json)])
        (update-in [:parameters :parameters] conj {:type :body
                                                   :model (eval `(var ~model-name))})
        (update-in [:letks] into [body-params coerced-model]))))

(defmethod restructure-param :query-params
  [_ query-params acc]
  "restructures query-params with plumbing letk notation. Generates
   synthetic defs for the models. Example:
   :query-params [id :- Long name :- String]"
  (let [schema (fnk-schema query-params)
        model-name (gensym "query-params-")
        _ (eval `(def ~model-name ~schema))
        coerced-model (gensym)]
    (-> acc
        (update-in [:lets] into [coerced-model `(schema/coerce!
                                                  ~schema
                                                  (keywordize-keys
                                                    (:query-params ~+compojure-api-request+))
                                                  :query)])
        (update-in [:parameters :parameters] conj {:type :query
                                                   :model (eval `(var ~model-name))})
        (update-in [:letks] into [query-params coerced-model]))))

(defmethod restructure-param :path-params
  [_ path-params acc]
  "restructures path-params by plumbing letk notation. Generates
   synthetic defs for the models. Example:
   :path-params [id :- Long name :- String]"
  (let [schema (fnk-schema path-params)
        model-name (gensym "path-params-")
        _ (eval `(def ~model-name ~schema))
        coerced-model (gensym)]
    (-> acc
        (update-in [:lets] into [coerced-model `(schema/coerce!
                                                  ~schema
                                                  (:route-params ~+compojure-api-request+)
                                                  :query)])
        (update-in [:parameters :parameters] conj {:type :path
                                                   :model (eval `(var ~model-name))})
        (update-in [:letks] into [path-params coerced-model]))))

(defmethod restructure-param :middlewares
  [k middlewares acc]
  "Applies the given vector of middlewares for the route from left to right"
  (assert (and (vector? middlewares) (every? (comp ifn? eval) middlewares)))
  (update-in acc [:middlewares] into (reverse middlewares)))

;;
;; Api
;;

(defmacro middlewares
  "Wraps routes with given middlewares using thread-first macro."
  [middlewares & body]
  (let [middlewares (reverse middlewares)]
    `(-> (routes ~@body)
         ~@middlewares)))

(defn- destructure-compojure-api-request [lets arg]
  (cond
    (vector? arg) [lets (into arg [:as +compojure-api-request+])]
    (map? arg) (if-let [as (:as arg)]
                 [(conj lets +compojure-api-request+ as) arg]
                 [lets (merge arg [:as +compojure-api-request+])])
    (symbol? arg) [(conj lets +compojure-api-request+ arg) arg]
    :else (throw
            (RuntimeException.
              (str "unknown compojure destruction synxax: " arg)))))

(defn restructure [method [path arg & args]]
  (let [method-symbol (symbol (str (-> method meta :ns) "/" (-> method meta :name)))
        [parameters body] (extract-parameters args)
        [lets letks middlewares] [[] [] []]
        [lets arg-with-request] (destructure-compojure-api-request lets arg)
        {:keys [lets
                letks
                middlewares
                parameters
                body]} (reduce
                         (fn [{:keys [lets letks middlewares parameters body]} [k v]]
                           (let [parameters (dissoc parameters k)
                                 acc (map-of lets letks middlewares parameters body)]
                             (restructure-param k v acc)))
                         (map-of lets letks parameters body)
                         parameters)
        body `(~method-symbol
                ~path
                ~arg-with-request
                (meta-container ~parameters
                                (let ~lets
                                  (letk ~letks
                                    ~@body))))]
    (if (empty? middlewares)
      body
      `(middlewares [~@middlewares] ~body))))
