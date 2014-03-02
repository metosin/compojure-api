(ns compojure.api.core
  (:require [compojure.core :refer :all]
            [compojure.api.pimp]
            [compojure.api.middleware :as mw]
            [ring.util.response :as response]
            [ring.swagger.core :as swagger]
            [ring.swagger.schema :as schema]
            [ring.swagger.common :refer :all]))

;;
;; Smart Destructuring
;;

(defn- restructure-body [request lets parameters]
  (if-let [[body-name body-model body-meta] (:body parameters)]
    (let [model-var (swagger/resolve-model-var (if (sequential? body-model) (first body-model) body-model))
          new-lets (into lets [body-name `(schema/coerce! ~model-var (:body-params ~request))])
          new-parameters (-> parameters
                           (dissoc :body)
                           swagger/resolve-model-vars
                           (update-in [:parameters] conj
                             (merge
                               {:name (-> model-var name-of .toLowerCase)
                                :description ""
                                :required "true"}
                               body-meta
                               {:paramType "body"
                                :type (if (sequential? body-model) [model-var] model-var)}))
                           (update-in [:parameters] vec))]
      [new-lets new-parameters])
    [lets parameters]))

(defn- restructure [method args]
  (let [[path & args] args
        [arg args] (if (vector? (first args)) [(first args) (rest args)] [[] args])
        method-symbol (symbol (str (-> method meta :ns) "/" (-> method meta :name)))
        [parameters body] (extract-parameters args)
        request (gensym)
        [lets parameters] (restructure-body request [] parameters)]
    `(fn [~request]
       (let ~lets
         ((~method-symbol ~path ~arg ~parameters ~@body) ~request)))))

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
