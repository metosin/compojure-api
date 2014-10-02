(ns compojure.api.middleware
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.format-params :as format-params :refer [wrap-restful-params]]
            [ring.middleware.format-response :as format-response]
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
  (into {} (map (fn [[k {{:keys [type sub-type]} :enc-type}]]
                  [k (str type "/" sub-type)])
                format-response/format-encoders)))

(def ^:private response-only-mimes #{:clojure :yaml-in-html})

(defn wrap-publish-swagger-formats [handler & {:keys [response-formats request-formats]}]
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

;; Version which takes the predicate as a parameter
;; TODO: Try to get merged into ring-middleware-format
(defn wrap-restful-response
  "Wrapper that tries to do the right thing with the response *:body*
   and provide a solid basis for a RESTful API. It will serialize to
   JSON, YAML, Clojure, Transit or HTML-wrapped YAML depending on Accept header.
   See wrap-format-response for more details. Recognized formats are
   *:json*, *:json-kw*, *:edn* *:yaml*, *:yaml-in-html*, *:transit-json*,
   *:transit-msgpack*."
  [handler & {:keys [handle-error predicate formats charset binary?]
              :or {handle-error format-response/default-handle-error
                   predicate format-response/serializable?
                   charset format-response/default-charset-extractor
                   formats [:json :yaml :edn :clojure :yaml-in-html :transit-json :transit-msgpack]}}]
  (let [encoders (for [format formats
                       :when format
                       :let [encoder (if (map? format)
                                       format
                                       (get format-response/format-encoders (keyword format)))]
                       :when encoder]
                   encoder)]
    (format-response/wrap-format-response handler
                                          :predicate predicate
                                          :encoders encoders
                                          :binary? binary?
                                          :charset charset
                                          :handle-error handle-error)))

(defn api-middleware
  "opinionated chain of middlewares for web apis."
  [handler & [{:keys [formats]
               :or {formats [:json-kw :yaml-kw :edn :transit-json :transit-msgpack]}}]]
  (-> handler
      ring.middleware.http-response/catch-response
      ring.swagger.middleware/catch-validation-errors
      ex-info-support
      (wrap-publish-swagger-formats
        :request-formats (remove response-only-mimes formats)
        :response-formats formats)
      (wrap-restful-params
        :formats (remove response-only-mimes formats)
        :handle-error handle-req-error)
      (wrap-restful-response
        :formats formats
        :predicate serializable?)
      wrap-keyword-params
      wrap-nested-params
      wrap-params))
