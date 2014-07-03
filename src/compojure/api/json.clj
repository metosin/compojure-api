(ns compojure.api.json
  "Pimped version of https://github.com/weavejester/ring-json (C) weavejester.
   Might use https://github.com/ngrunwald/ring-middleware-format later."
  (:require [ring.util.http-response :refer [content-type bad-request]]
            [cheshire.core :as cheshire]
            [ring.middleware.format-response :refer [wrap-json-response]]
            [clojure.walk16 :as walk]
            [clojure.java.io :as io])
  (:import [com.fasterxml.jackson.core JsonParseException]))

;; JSON standard date format according to
;; http://stackoverflow.com/questions/10286204/the-right-json-date-format
(def ^{:dynamic true} *json-date-format* "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

(defn json-request?
  "Checks from request content-type weather it's JSON."
  [{:keys [content-type]}]
  (and
    content-type
    (not (empty? (re-find #"^application/(vnd.+)?json" content-type)))))

(defn json-request-support
  [handler & [{:keys [keywords?] :or {keywords? true}}]]
  (fn [{:keys [character-encoding content-type body] :as request}]
    (try
      (let [request (if-not (and body (json-request? request))
                      request
                      (let [json (cheshire/parse-stream (io/reader body :encoding (or character-encoding "utf-8")) keywords?)]
                        (cond
                          (sequential? json) (-> request
                                               (assoc :body (vec json))
                                               (assoc :body-params (vec json)))
                          (map? json) (-> request
                                        (assoc :body json)
                                        (assoc :body-params json)
                                        (assoc :json-params json)
                                        (update-in [:params] merge json))
                          :else request)))
            request (update-in request [:meta :consumes] concat ["application/json"])]
        (handler request))
      (catch JsonParseException jpe
        (bad-request {:type "json-parse-exception"
                      :message (.getMessage jpe)})))))


(defn serializable?
  "Predicate that returns true whenever the response body is serializable."
  [_ {:keys [body] :as response}]
  (when response
    (or (coll? body)
        (and (:compojure.api.meta/serializable? response)
             (not
               (or
                 (instance? java.io.File body)
                 (instance? java.io.InputStream body)))))))

(defn json-support
  [handler]
  (-> handler
      json-request-support
      (wrap-json-response :predicate serializable?)))
