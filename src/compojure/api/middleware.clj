(ns compojure.api.middleware
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.format-params :refer [wrap-restful-params]]
            [ring.middleware.format-response :refer [wrap-restful-response]]
            ring.middleware.http-response
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            ring.swagger.middleware
            [ring.util.http-response :refer :all]))

(defroutes public-resource-routes
  (GET "/" [] (found "/index.html"))
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

;; ring-middleware-format stuff
(def ^:private mime-types
  {:json "application/json"
   :json-kw "application/json"
   :edn "application/edn"
   :clojure "application/clojure"
   :yaml "application/x-yaml"
   :yaml-kw "application/x-yaml"
   :yaml-in-html "text/html"
   :transit-json "application/transit+json"
   :transit-msgpack "application/transit+msgpack"})

(def ^:private response-only-mimes #{:clojure :yaml-in-html})

(defn wrap-publish-swagger-formats [handler & [{:keys [response-formats request-formats]}]]
  (fn [request]
    (-> request
        (assoc-in [:meta :consumes] (map mime-types request-formats))
        (assoc-in [:meta :produces] (map mime-types response-formats))
        handler)))

(defn handle-req-error [e handler req]
  (cond
    (instance? com.fasterxml.jackson.core.JsonParseException e)
    (bad-request {:type "json-parse-exception"
                  :message (.getMessage e)})

    (instance? org.yaml.snakeyaml.parser.ParserException e)
    (bad-request {:type "yaml-parse-exception"
                  :message (.getMessage e)})

    :else
    (internal-server-error {:type (str (class e))
                            :message (.getMessage e)})))

(defn serializable?
  "Predicate which return true if the response body is serializable.
   That is, return type is set by :return compojure-api key or it's
   a collection."
  [_ {:keys [body] :as response}]
  (when response
    (or (:compojure.api.meta/serializable? response)
        (coll? body))))

(defn api-middleware
  "opinionated chain of middlewares for web apis."
  [handler & [{:keys [formats params-opts response-opts]
               :or {formats [:json-kw :yaml-kw :edn :transit-json :transit-msgpack]}}]]
  (-> handler
      ring.middleware.http-response/catch-response
      ring.swagger.middleware/wrap-validation-errors
      ex-info-support
      (wrap-publish-swagger-formats
        {:request-formats (remove response-only-mimes formats)
         :response-formats formats})
      (wrap-restful-params
        (merge {:formats (remove response-only-mimes formats)
                :handle-error handle-req-error}
               params-opts))
      (wrap-restful-response
        (merge {:formats formats
                :predicate serializable?}
               response-opts))
      wrap-keyword-params
      wrap-nested-params
      wrap-params))
