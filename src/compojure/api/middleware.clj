(ns compojure.api.middleware
  (:require [clojure.walk16 :as walk]
            [ring.util.response :refer [response content-type redirect]]
            [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.util.http-response :refer [bad-request]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            ring.middleware.http-response
            ring.swagger.middleware
            [compojure.api.json :refer :all]))

(defn keywordize-request
  "keywordizes all ring-request keys recursively."
  [handler]
  (fn [request]
    (handler
      (walk/keywordize-keys request))))

(defroutes public-resource-routes
  (GET "/" [] (redirect "/index.html"))
  (route/resources "/"))

(defn public-resources
  "serves public resources for missed requests"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (or response
        ((route/resources "/") request)))))

;; make this better, Slingshot?
(defn ex-info-support
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (bad-request (ex-data e))))))

(defn api-middleware
  "opinionated chain of middlewares for web apis."
  [handler]
  (-> handler
      ring.middleware.http-response/catch-response
      ring.swagger.middleware/catch-validation-errors
      json-support
      ex-info-support
      wrap-keyword-params
      wrap-nested-params
      wrap-params))
