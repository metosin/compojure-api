(ns compojure.api.core
  (:require [compojure.core :refer :all]
            [compojure.api.middleware :as mw]
            [compojure.api.common :refer :all]
            [ring.util.response :as response]
            [ring.swagger.core :as swagger]
            [ring.swagger.schema :as schema]
            [ring.swagger.common :refer :all]
            [schema.core :as s]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.tools.macro :refer [name-with-attributes]]))

;;
;; Smart Destructuring
;;

(defn- restructure-body [request lets parameters]
  (if-let [[value model model-meta] (:body parameters)]
    (let [model-var (swagger/resolve-model-var (if (or (set? model) (sequential? model)) (first model) model))
          new-lets (into lets [value `(schema/coerce! ~model (:body-params ~request) :json)])
          new-parameters (-> parameters
                           (dissoc :body)
                           swagger/resolve-model-vars
                           (update-in [:parameters] conj
                             (merge
                               {:name (-> model-var name-of .toLowerCase)
                                :description ""
                                :required "true"}
                               model-meta
                               {:paramType "body"
                                :type (swagger/resolve-model-vars model)}))
                           (update-in [:parameters] vec))]
      [new-lets new-parameters])
    [lets parameters]))

;; TODO: generates bad arrays
(defn- query-model-parameters [model]
  (doall
    (for [[k v ] model
          :let [rk (s/explicit-schema-key k)]]
      (merge
        (swagger/type-of v)
        {:name (name rk)
         :description ""
         :required (s/required-key? k)
         :paramType "query"}))))

(defn- restructure-query-params [request lets parameters]
  (if-let [[value model model-meta] (:query parameters)]
    (let [model-var (swagger/resolve-model-var (if (or (set? model) (sequential? model)) (first model) model))
          new-lets (into lets [value `(schema/coerce! ~model (keywordize-keys (:query-params ~request)) :query)])
          new-parameters (-> parameters
                           (dissoc :query)
                           swagger/resolve-model-vars
                           (update-in [:parameters] concat
                             (query-model-parameters (value-of model-var)))
                           (update-in [:parameters] vec))]
      [new-lets new-parameters])
    [lets parameters]))

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

(defn- restructure [method [path arg & args]]
  (let [method-symbol (symbol (str (-> method meta :ns) "/" (-> method meta :name)))
        [parameters body] (extract-parameters args)
        body (restructure-validation parameters body)
        request (gensym)
        [lets parameters] (restructure-body request [] parameters)
        [lets parameters] (restructure-query-params request lets parameters)]
    `(fn [~request]
       ((~method-symbol ~path ~arg (meta-container ~parameters (let ~lets ~@body))) ~request))))

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
