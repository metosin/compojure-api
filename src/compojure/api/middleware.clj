(ns compojure.api.middleware
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.format-params :refer [wrap-restful-params]]
            [ring.middleware.format-response :refer [wrap-restful-response]]
            ring.middleware.http-response
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.api.common :refer [deep-merge]]
            ring.swagger.middleware
            [ring.util.http-response :refer :all])
  (:import [com.fasterxml.jackson.core JsonParseException]
           [org.yaml.snakeyaml.parser ParserException]))

;;
;; Public resources
;;

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

;;
;; Catch exceptions
;;

(defn default-exception-handler [^Exception e]
  (.printStackTrace e)
  (internal-server-error {:type  "unknown-exception"
                          :class (.getName (.getClass e))}))

(defn wrap-exceptions
  "Catches all exceptions. Accepts the following options:

  :exception-handler - a function to handle the exception. defaults
                       to default-exception-handler"
  [handler & [{:keys [exception-handler]
               :or {exception-handler default-exception-handler}}]]
  {:pre [(fn? exception-handler)]}
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (exception-handler e)))))

;;
;; ring-middleware-format stuff
;;

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
    (instance? JsonParseException e)
    (bad-request {:type "json-parse-exception"
                  :message (.getMessage e)})

    (instance? ParserException e)
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

;;
;; Api Middleware
;;

(def api-middleware-defaults
  {:format {:formats [:json-kw :yaml-kw :edn :transit-json :transit-msgpack]
            :params-opts {}
            :response-opts {}}
   :validation-errors {:error-handler nil
                       :catch-core-errors? nil}
   :exceptions {:exception-handler default-exception-handler}
   :defaults "https://github.com/ring-clojure/ring-defaults/blob/master/src/ring/middleware/defaults.clj#L20"})

; TODO: document all options
(defn api-middleware
  "Opinionated chain of middlewares for web apis. Takes options-map, with namespaces
   options for the used middlewares. These include:

   :exceptions        - for compojure.api.middleware/wrap-exceptions
   :validation-errors - for ring.swagger.middleware/wrap-validation-errors
   :format            - format, params-opts, response-opts for ring-middleware-format middlewares"
  [handler & [options]]
  (let [options (deep-merge api-middleware-defaults options)
        {:keys [formats params-opts response-opts]} (:format options)]
    (-> handler
        ring.middleware.http-response/wrap-http-response
        (ring.swagger.middleware/wrap-validation-errors (:validation-errors options))
        (wrap-exceptions (:exceptions options))
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
        wrap-params)))
