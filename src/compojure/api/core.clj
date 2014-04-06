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
            [clojure.walk :refer [keywordize-keys]]
            [clojure.tools.macro :refer [name-with-attributes]]))

(def +compojure-api-request+ '+compojure-api-request+)

(defn fnk-schema [bind]
  (:input-schema
    (fnk-impl/letk-input-schema-and-body-form
      nil (with-meta bind {:schema s/Any}) [] nil)))

;;
;; Smart Destructurors
;;

(defn- restructure-validation [parameters body]
  (if-let [model (:return parameters)]
    (let [returned-form (last body)
          body (butlast body)
          validated-return-form `(let [validator# (partial schema/coerce! ~model)
                                       return-value# ~returned-form]
                                   (if (response/response? return-value#)
                                     (update-in return-value# [:body] validator#)
                                     (validator# return-value#)))]
      (concat body [validated-return-form]))
    body))

(defn- restructure-body [lets letks parameters]
  (if-let [[value model model-meta] (:body parameters)]
    (let [model-var (swagger/resolve-model-var (if (or (set? model) (sequential? model)) (first model) model))
          new-lets (into lets [value `(schema/coerce! ~model (:body-params ~+compojure-api-request+) :json)])
          new-parameters (-> parameters
                           (dissoc :body)
                           (update-in [:parameters] conj
                             {:type :body
                              :model (swagger/resolve-model-vars model)
                              :meta model-meta}))]
      [new-lets letks new-parameters])
    [lets letks parameters]))

(defn- restructure-query [lets letks parameters]
  (if-let [[value model model-meta] (:query parameters)]
    (let [model-var (swagger/resolve-model-var (if (or (set? model) (sequential? model)) (first model) model))
          new-lets (into lets [value `(schema/coerce! ~model (keywordize-keys (:query-params ~+compojure-api-request+)) :query)])
          new-parameters (-> parameters
                           (dissoc :query)
                           (update-in [:parameters] conj
                             {:type :query
                              :model (swagger/resolve-model-vars model)
                              :meta model-meta}))]
      [new-lets letks new-parameters])
    [lets letks parameters]))

(defn- restructure-query-params
  "restructures query-params by plumbing letk notation. Generates
   synthetic defs for the models. Example:
   :query-params [id :- Long name :- String]"
  [lets letks parameters]
  (if-let [query-params (:query-params parameters)]
    (let [schema (fnk-schema query-params)
          model-name (gensym "query-params-")
          _ (eval `(def ~model-name ~schema))
          coerced-model (gensym)
          new-lets (into lets [coerced-model `(schema/coerce! ~schema (keywordize-keys (:query-params ~+compojure-api-request+)) :query)])
          new-parameters (-> parameters
                           (dissoc :query-params)
                           (update-in [:parameters] conj
                              {:type :query
                               :model (eval `(var ~model-name))}))]
      [new-lets (into letks [query-params coerced-model]) new-parameters])
    [lets letks parameters]))

(defn- restructure-path-params
  "restructures path-params by plumbing letk notation. Generates
   synthetic defs for the models. Example:
   :path-params [id :- Long name :- String]"
  [lets letks parameters]
  (if-let [path-params (:path-params parameters)]
    (let [schema (fnk-schema path-params)
          model-name (gensym "path-params-")
          _ (eval `(def ~model-name ~schema))
          coerced-model (gensym)
          new-lets (into lets [coerced-model `(schema/coerce! ~schema (:route-param ~+compojure-api-request+) :query)])
          new-parameters (-> parameters
                           (dissoc :path-params)
                           (update-in [:parameters] conj
                              {:type :path
                               :model (eval `(var ~model-name))}))]
      [new-lets (into letks [path-params coerced-model]) new-parameters])
    [lets letks parameters]))

(defn- restructure-return [lets letks parameters]
  [lets letks (update-in parameters [:return] swagger/resolve-model-vars)])

(defn- vectorize-parameters [lets letks parameters]
  [lets letks (if (:parameters parameters)
                  (update-in parameters [:parameters] vec)
                  parameters)])

;;
;; Main
;;

; test me
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

; test me
(defn- restructure [method [path arg & args]]
  (let [method-symbol (symbol (str (-> method meta :ns) "/" (-> method meta :name)))
        [parameters body] (extract-parameters args)
        body (restructure-validation parameters body)
        [lets letks parameters] (reduce
                                  (fn [[lets letks parameters] f]
                                    (f lets letks parameters))
                                  [[] [] parameters]
                                  [restructure-return
                                   restructure-body
                                   restructure-query
                                   restructure-query-params
                                   restructure-path-params
                                   vectorize-parameters])
        [lets args-with-request] (destructure-compojure-api-request lets arg)]
    `(~method-symbol
       ~path
       ~args-with-request
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
