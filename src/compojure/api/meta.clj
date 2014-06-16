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

(defn body-coercer-middleware [handler model]
  (fn [request]
    (if-let [response (handler request)]
      (assoc response :body (schema/coerce! model (:body response))))))

(defn src-coerce!
  "Return source code for coerce! for a schema with coercer type, extracted from a
   key in a ring request."
  [schema key type]
  `(schema/coerce!
     ~schema
     (keywordize-keys
       (~key ~+compojure-api-request+))
     ~type))

;;
;; Extension point
;;

(defmulti restructure-param
  "Restructures a key value pair in smart routes. By default the key
   is consumed form the :parameters map in acc. k = given key, v = value."
  (fn [k v acc] k))

(defmethod restructure-param :default [k v acc]
  "By default assoc in the k v to acc"
  (update-in acc [:parameters] assoc k v))

;;
;; Default implementation
;;

(defmethod restructure-param :return [k model acc]
  "Defines a return type and coerced the return value of a body against it."
  (-> acc
      (update-in [:parameters] assoc k (eval model))
      (update-in [:middlewares] conj `(body-coercer-middleware ~model))))

(defmethod restructure-param :body [_ [value model model-meta] acc]
  "reads body-params into a enchanced let. First parameter is the let symbol,
   second is the Model to coerced! against, third parameter is optional meta-
   data for the model. Examples:
   :body [user User]
   :body [user User {:key \"value\"}]"
  (-> acc
      (update-in [:lets] into [value (src-coerce! model :body-params :json)])
      (update-in [:parameters :parameters] conj {:type :body
                                                 :model (eval model)
                                                 :meta model-meta})))

(defmethod restructure-param :query [_ [value model model-meta] acc]
  "reads query-params into a enchanced let. First parameter is the let symbol,
   second is the Model to coerced! against, third parameter is optional meta-
   data for the model. Examples:
   :query [user User]
   :query [user User {:key \"value\"}]"
  (-> acc
      (update-in [:lets] into [value (src-coerce! model :query-params :query)])
      (update-in [:parameters :parameters] conj {:type :query
                                                 :model (eval model)
                                                 :meta model-meta})))

(defmethod restructure-param :body-params [_ body-params acc]
  "restructures body-params with plumbing letk notation. Example:
   :body-params [id :- Long name :- String]"
  (let [schema (eval (strict (fnk-schema body-params)))
        coerced-model (gensym)]
    (-> acc
        (update-in [:lets] into [coerced-model (src-coerce! schema :body-params :json)])
        (update-in [:parameters :parameters] conj {:type :body :model schema})
        (update-in [:letks] into [body-params coerced-model]))))

(defmethod restructure-param :query-params [_ query-params acc]
  "restructures query-params with plumbing letk notation. Example:
   :query-params [id :- Long name :- String]"
  (let [schema (eval (fnk-schema query-params))
        coerced-model (gensym)]
    (-> acc
        (update-in [:lets] into [coerced-model (src-coerce! schema :query-params :query)])
        (update-in [:parameters :parameters] conj {:type :query :model schema})
        (update-in [:letks] into [query-params coerced-model]))))

(defmethod restructure-param :path-params [_ path-params acc]
  "restructures path-params by plumbing letk notation. Example:
   :path-params [id :- Long name :- String]"
  (let [schema (eval (fnk-schema path-params))
        coerced-model (gensym)]
    (-> acc
        (update-in [:lets] into [coerced-model (src-coerce! schema :route-params :query)])
        (update-in [:parameters :parameters] conj {:type :path :model schema})
        (update-in [:letks] into [path-params coerced-model]))))

(defmethod restructure-param :middlewares [_ middlewares acc]
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
        [lets letks middlewares] [[] [] nil]
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
                         (map-of lets letks middlewares parameters body)
                         parameters)
        body `(do ~@body)
        body (if (seq letks) `(letk ~letks ~body) body)
        body (if (seq lets) `(let ~lets ~body) body)
        body (if (seq parameters) `(meta-container ~parameters ~body) body)
        body `(~method-symbol ~path ~arg-with-request ~body)]
    (if (seq middlewares) `(middlewares [~@middlewares] ~body) body)))
