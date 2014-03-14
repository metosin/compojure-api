(ns compojure.api.json
  "Pimped version of https://github.com/weavejester/ring-json (C) weavejester.
   Might use https://github.com/ngrunwald/ring-middleware-format later."
  (:require [ring.util.response :refer [content-type]]
            [cheshire.core :as cheshire]
            cheshire.generate
            [clojure.walk :as walk]
            [clojure.java.io :as io]))

;; JSON standard date format according to
;; http://stackoverflow.com/questions/10286204/the-right-json-date-format
(def ^{:dynamic true} *json-date-format* "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")


(defn json-request?
  "Checks from request content-type weather it's JSON."
  [{:keys [content-type] :as request}]
  (and
    content-type
    (not (empty? (re-find #"^application/(vnd.+)?json" content-type)))))

(defn json-request-support
  [handler & [{:keys [keywords?] :or {keywords? true}}]]
  (fn [{:keys [character-encoding content-type body] :as request}]
    (handler
      (if-not (and body (json-request? request))
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
            :else request))))))

(defn json-response-support
  [handler]
  (fn [request]
    (let [{:keys [body] :as response} (handler request)]
      (if (coll? body)
        (-> response
          (content-type "application/json; charset=utf-8")
          (update-in [:body] cheshire/generate-string {:date-format *json-date-format*}))
        response))))

(defn encode-with-pr-str
  [cls]
  (cheshire.generate/add-encoder cls
    (fn [c g]
      (.writeString g (pr-str c)))))

(defn add-schema-encoders []
  (doseq [cls [java.lang.Class
               schema.core.AnythingSchema
               schema.core.EqSchema
               schema.core.EnumSchema
               schema.core.Predicate
               schema.core.Protocol
               schema.core.Maybe
               schema.core.NamedSchema
               schema.core.Either
               schema.core.Both
               schema.core.ConditionalSchema
               schema.core.Recursive
               schema.core.MapEntry
               schema.core.Record
               schema.core.FnSchema
               schema.core.OptionalKey
               schema.core.RequiredKey
               schema.core.One]]
    (encode-with-pr-str cls)))

(defn json-support
  [handler]
  (add-schema-encoders)
  (-> handler json-request-support json-response-support))
