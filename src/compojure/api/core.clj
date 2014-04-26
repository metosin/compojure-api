(ns compojure.api.core
  (:require [compojure.core :refer :all]
            [compojure.api.middleware :as mw]
            [compojure.api.common :refer :all]
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

;;
;; Smart Destructurors
;;

(defmulti restructure-param
  "Restructures a key value pair in smart routes. By default the key
   is consumed form the :parameters map in acc. k = given key, v = value."
  (fn [k v acc] k))

(defmethod restructure-param :default
  "Defaulting to pushing back the parameter"
  [k v acc] (update-in acc [:parameters] assoc k v))

(defmethod restructure-param :return
  [k model {:keys [parameters body] :as acc}]
  (let [returned-form (last body)
        body (butlast body)
        validated-return-form `(let [validator# (partial schema/coerce! ~model)
                                     return-value# ~returned-form]
                                 (if (response/response? return-value#)
                                   (update-in return-value# [:body] validator#)
                                   (validator# return-value#)))]
    (assoc acc
           :parameters (assoc parameters k (swagger/resolve-model-vars model))
           :body (concat body [validated-return-form]))))

(defmethod restructure-param :body
  [k [value model model-meta] {:keys [lets parameters] :as acc}]
  (let [model-var (resolve-model-var model)]
    (assoc acc
           :lets (into lets [value `(schema/coerce!
                                      ~model
                                      (:body-params ~+compojure-api-request+)
                                      :json)])
           :parameters (update-in parameters [:parameters] conj
                                  {:type :body
                                   :model (swagger/resolve-model-vars model)
                                   :meta model-meta}))))

(defmethod restructure-param :query
  [k [value model model-meta] {:keys [lets parameters] :as acc}]
  (let [model-var (resolve-model-var model)]
    (assoc acc
           :lets (into lets [value `(schema/coerce!
                                      ~model
                                      (keywordize-keys
                                        (:query-params ~+compojure-api-request+))
                                      :query)])
           :parameters (update-in parameters [:parameters] conj
                                  {:type :query
                                   :model (swagger/resolve-model-vars model)
                                   :meta model-meta}))))

(defmethod restructure-param :body-params
  [k body-params {:keys [lets letks parameters] :as acc}]
  "restructures body-params by plumbing letk notation. Generates
   synthetic defs for the models. Example:
   :body-params [id :- Long name :- String]"
  (let [schema (strict (fnk-schema body-params))
        model-name (gensym "body-")
        _ (eval `(schema/defmodel ~model-name ~schema))
        coerced-model (gensym)]
    (assoc acc
           :lets (into lets [coerced-model `(schema/coerce!
                                              ~schema
                                              (:body-params ~+compojure-api-request+)
                                              :json)])
           :parameters (update-in parameters [:parameters] conj
                                  {:type :body
                                   :model (eval `(var ~model-name))})
           :letks (into letks [body-params coerced-model]))))

(defmethod restructure-param :query-params
  [k query-params {:keys [lets letks parameters] :as acc}]
  "restructures query-params by plumbing letk notation. Generates
   synthetic defs for the models. Example:
   :query-params [id :- Long name :- String]"
  (let [schema (fnk-schema query-params)
        model-name (gensym "query-params-")
        _ (eval `(def ~model-name ~schema))
        coerced-model (gensym)]
    (assoc acc
           :lets (into lets [coerced-model `(schema/coerce!
                                              ~schema
                                              (keywordize-keys
                                                (:query-params ~+compojure-api-request+))
                                              :query)])
           :parameters (update-in parameters [:parameters] conj
                                  {:type :query
                                   :model (eval `(var ~model-name))})
           :letks (into letks [query-params coerced-model]))))

(defmethod restructure-param :path-params
  [k path-params {:keys [lets letks parameters] :as acc}]
  "restructures path-params by plumbing letk notation. Generates
   synthetic defs for the models. Example:
   :path-params [id :- Long name :- String]"
  (let [schema (fnk-schema path-params)
        model-name (gensym "path-params-")
        _ (eval `(def ~model-name ~schema))
        coerced-model (gensym)]
    (assoc acc
           :lets (into lets [coerced-model `(schema/coerce!
                                              ~schema
                                              (:route-params ~+compojure-api-request+)
                                              :query)])
           :parameters (update-in parameters [:parameters] conj
                                  {:type :path
                                   :model (eval `(var ~model-name))})
           :letks (into letks [path-params coerced-model]))))

;;
;; Main
;;

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

(defn- restructure [method [path arg & args]]
  (let [method-symbol (symbol (str (-> method meta :ns) "/" (-> method meta :name)))
        [parameters body] (extract-parameters args)
        [lets letks] [[] []]
        [lets arg-with-request] (destructure-compojure-api-request lets arg)
        {:keys [lets
                letks
                parameters
                body]} (reduce
                         (fn [{:keys [lets letks parameters body]} [k v]]
                           (let [parameters (dissoc parameters k)
                                 acc (map-of lets letks parameters body)]
                             (restructure-param k v acc)))
                         (map-of lets letks parameters body)
                         parameters)]
    `(~method-symbol
       ~path
       ~arg-with-request
       (meta-container ~parameters
                       (let ~lets
                         (letk ~letks
                           ~@body))))))

;;
;; routes
;;

(defmacro defapi [name & body]
  `(defroutes ~name
     (mw/api-middleware
       (routes ~@body))))

(defmacro with-middleware [middlewares & body]
  `(routes
     (reduce
       (fn [handler# middleware#]
         (middleware# handler#))
       (routes ~@body)
       ~middlewares)))

(defmacro defroutes*
  "Define a Ring handler function from a sequence of routes. The name may
  optionally be followed by a doc-string and metadata map."
  [name & routes]
  (let [source (drop 2 &form)
        [name routes] (name-with-attributes name routes)]
    `(def ~name (with-meta (routes ~@routes) {:source '~source
                                              :inline true}))))

;;
;; Methods
;;

(defmacro GET*     [& args] (restructure #'GET     args))
(defmacro ANY*     [& args] (restructure #'ANY     args))
(defmacro HEAD*    [& args] (restructure #'HEAD    args))
(defmacro PATCH*   [& args] (restructure #'PATCH   args))
(defmacro DELETE*  [& args] (restructure #'DELETE  args))
(defmacro OPTIONS* [& args] (restructure #'OPTIONS args))
(defmacro POST*    [& args] (restructure #'POST    args))
(defmacro PUT*     [& args] (restructure #'PUT     args))
