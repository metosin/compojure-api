(ns compojure.api.middleware
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.format :refer [wrap-formats]]
            [ring.middleware.formatters :refer [get-existing-formatter encoder? decoder?]]
            ring.middleware.http-response
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.swagger.common :refer [deep-merge]]
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

(defn wrap-publish-swagger-formats [handler & [{:keys [formats] :as opts}]]
  (let [formats  (->> formats (map (partial get-existing-formatter opts)))
        consumes (->> formats (filter decoder?) (mapv :content-type))
        produces (->> formats (filter encoder?) (mapv :content-type))]
    (fn [request]
      (-> request
          (assoc-in [:meta :consumes] consumes)
          (assoc-in [:meta :produces] produces)
          handler))))

(defn handle-req-error [^Throwable e handler req]
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
  {:format {:formats [:json-kw :yaml-kw :edn :transit-json :transit-msgpack]}
   :validation-errors {:error-handler nil
                       :catch-core-errors? nil}
   :exceptions {:exception-handler default-exception-handler}})

(defn api-middleware
  "Opinionated chain of middlewares for web apis. Takes options-map, with namespaces
   options for the used middlewares (see middlewares for full details on options):

   :exceptions           - for compojure.api.middleware/wrap-exceptions
     :exception-handler  - function to handle uncaught exceptions
   :validation-errors    - for ring.swagger.middleware/wrap-validation-errors
     :error-handler      - function to handle ring-swagger schema exceptions
     :catch-core-errors? - whether to catch also :schema.core/errors
   :format               - for ring-middleware-format middleware
     :opts
       :*format-name*
     :charset"
  [handler & [options]]
  (let [options (deep-merge api-middleware-defaults options)
        format-opts (:format options)]
    (-> handler
        ring.middleware.http-response/wrap-http-response
        (ring.swagger.middleware/wrap-validation-errors (:validation-errors options))
        (wrap-exceptions (:exceptions options))
        (wrap-publish-swagger-formats format-opts)
        (wrap-formats (merge {:handle-error handle-req-error
                              :predicate serializable?}
                             format-opts))
        wrap-keyword-params
        wrap-nested-params
        wrap-params)))
