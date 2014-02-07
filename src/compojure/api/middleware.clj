(ns compojure.api.middleware
  (:require [clojure.walk :as walk]
            [compojure.handler :as compojure]
            [ring.util.response :refer [response content-type redirect]]
            [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [compojure.route :as route]
            [compojure.core :refer :all]
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
        (println e)
        (println (ex-data e))
        {:status 400
         :headers {}
         :body (ex-data e)}))))

(defn api-middleware
  "opinionated chain of middlewares for web apis."
  [handler]
  (-> handler
    ex-info-support
    json-support
    compojure/api))
