(ns compojure.api.core
  (:require [clojure.walk :as walk]
            [compojure.handler :as compojure]
            [ring.util.response :refer [response content-type]]
            [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [compojure.core :refer :all]))

(defn json-request?
  "Checks from request content-type weather it's JSON."
  [{:keys [content-type] :as request}]
  (and
    content-type
    (not (empty? (re-find #"^application/(vnd.+)?json" content-type)))))

(defn wrap-json-body-and-params
  [handler]
  (fn [{:keys [character-encoding content-type body] :as request}]
    (handler
      (or
        (if (and body (json-request? request))
          (let [json (cheshire/parse-stream (io/reader body :encoding (or character-encoding "utf-8")))]
            (or
              (and (sequential? json)
                (-> request
                  (assoc :body (vec json))
                  (assoc :body-params (vec json))))
              (and (map? json)
                (-> request
                  (assoc :body json)
                  (assoc :body-params json)
                  (assoc :json-params json)
                  (update-in [:params] merge json))))))
        request))))

(defn wrap-json-response
  [handler]
  (fn [request]
    (let [{:keys [body] :as response} (handler request)]
      (if (coll? body)
        (-> response
          (content-type "application/json; charset=utf-8")
          (update-in [:body] cheshire/generate-string))
        response))))

(defn keywordize-request
  "keywordizes all ring-request keys recursively."
  [handler]
  (fn [request]
    (handler
      (walk/keywordize-keys request))))

(defn logged-request
  [handler]
  (fn [request]
    (println request)
    (handler request)))

(defmacro apiroutes [& body]
  `(api-middleware (routes ~@body)))

(defmacro defapi [name & body]
  `(defroutes ~name (apiroutes ~@body)))

(defmacro with-middleware [middlewares & body]
  `(routes
     (reduce
       (fn [handler# middleware#]
         (middleware# handler#))
       (routes ~@body)
       ~middlewares)))

(defn api-middleware
  "opinionated chain of middlewares for web apis."
  [handler]
  (-> handler
    keywordize-request
    wrap-json-response
    wrap-json-body-and-params
    compojure/api))
