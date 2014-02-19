(ns compojure.api.core
  (:require [compojure.core :refer :all]
            [compojure.api.pimp]
            [compojure.api.middleware :as mw]
            [ring.util.response :as response]
            [ring.swagger.core :as swagger]
            [ring.swagger.schema :as schema]
            [ring.swagger.common :refer :all]))

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

(defmacro GET* [path arg & body]     `(GET ~path ~arg ~@body))
(defmacro ANY* [path arg & body]     `(ANY ~path ~arg ~@body))
(defmacro HEAD* [path arg & body]    `(HEAD ~path ~arg ~@body))
(defmacro PATCH* [path arg & body]   `(PATCH ~path ~arg ~@body))
(defmacro DELETE* [path arg & body]  `(DELETE ~path ~arg ~@body))
(defmacro OPTIONS* [path arg & body] `(OPTIONS ~path ~arg ~@body))

; TODO: Extract behavior into composable Routelets

(defmacro POST* [path arg & body]
  (let [[parameters body] (extract-parameters body)]
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
                         (update-in [:parameters] vec))]
        `(fn [req#]
           (let [{~body-name :body-params} req#]
             ((POST ~path ~arg ~parameters ~@body) req#))))
      `(POST ~path ~arg ~parameters ~@body))))

(defmacro PUT* [path arg & body]
  (let [[parameters body] (extract-parameters body)]
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
                         (update-in [:parameters] vec))]
        `(fn [req#]
           (let [{~body-name :body-params} req#]
             ((PUT ~path ~arg ~parameters ~@body) req#))))
      `(PUT ~path ~arg ~parameters ~@body))))
