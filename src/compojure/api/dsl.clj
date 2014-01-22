(ns compojure.api.dsl
  (:require [compojure.core :refer :all]
            [compojure.api.pimp]
            [ring.util.response :as response]
            [ring.swagger.core :as swagger]
            [ring.swagger.schema :as schema]
            [ring.swagger.common :refer :all]))

;;
;; common
;;

(defn ok
  "status 200"
  [body] (response/response body))

(defn ->Long [s] (java.lang.Long/parseLong s))

;;
;; routes
;;

(defmacro GET* [path arg & body]     `(GET ~path ~arg ~@body))
(defmacro ANY* [path arg & body]     `(ANY ~path ~arg ~@body))
(defmacro HEAD* [path arg & body]    `(HEAD ~path ~arg ~@body))
(defmacro PATCH* [path arg & body]   `(PATCH ~path ~arg ~@body))
(defmacro DELETE* [path arg & body]  `(DELETE ~path ~arg ~@body))
(defmacro OPTIONS* [path arg & body] `(OPTIONS ~path ~arg ~@body))

(defmacro POST* [path arg & body]
  (let [[parameters body] (extract-parameters body)]
    (if-let [[body-name body-model body-meta] (:body parameters)]
      (let [parameters (-> parameters
                         (dissoc :body)
                         swagger/purge-model-vars
                         (update-in [:parameters] conj
                           (merge
                             {:name (-> body-model swagger/purge-model-var name-of .toLowerCase)
                              :description ""
                              :required "true"}
                             body-meta
                             {:paramType "body"
                              :type (swagger/purge-model-var body-model)}))
                         (update-in [:parameters] vec))]
        `(fn [req#]
           (let [{~body-name :params} req#]
             ((POST ~path ~arg ~parameters ~@body) req#))))
      `(POST ~path ~arg ~parameters ~@body))))

(defmacro PUT* [path arg & body]
  (let [[parameters body] (extract-parameters body)]
    (if-let [[body-name body-model body-meta] (:body parameters)]
      (let [parameters (-> parameters
                         (dissoc :body)
                         swagger/purge-model-vars
                         (update-in [:parameters] conj
                           (merge
                             {:name (-> body-model swagger/purge-model-var name-of .toLowerCase)
                              :description ""
                              :required "true"}
                             body-meta
                             {:paramType "body"
                              :type body-model}))
                         (update-in [:parameters] vec))]
        `(fn [req#]
           (let [{~body-name :params} req#]
             ((PUT ~path ~arg ~parameters ~@body) req#))))
      `(PUT ~path ~arg ~parameters ~@body))))
