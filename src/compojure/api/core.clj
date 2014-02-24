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
    (let [model-var  (swagger/resolve-model-var (if (sequential? body-model) (first body-model) body-model))
          parameters (-> parameters
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
                       (update-in [:parameters] vec))
          new-lets (into lets [{body-name :body-params} request])]
      [new-lets parameters])
    [lets parameters]))

(defn- restructured [method path arg body]
  (let [method-symbol (symbol (str (-> method meta :ns) "/" (-> method meta :name)))
        [parameters body] (extract-parameters body)
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

(defmacro GET*     [path arg & body] `(GET ~path ~arg ~@body))
(defmacro ANY*     [path arg & body] `(ANY ~path ~arg ~@body))
(defmacro HEAD*    [path arg & body] `(HEAD ~path ~arg ~@body))
(defmacro PATCH*   [path arg & body] `(PATCH ~path ~arg ~@body))
(defmacro DELETE*  [path arg & body] `(DELETE ~path ~arg ~@body))
(defmacro OPTIONS* [path arg & body] `(OPTIONS ~path ~arg ~@body))
(defmacro POST*    [path arg & body] (restructured #'POST path arg body))
(defmacro PUT*     [path arg & body] (restructured #'PUT path arg body))
