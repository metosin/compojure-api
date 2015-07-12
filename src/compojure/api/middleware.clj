(ns compojure.api.middleware
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.format-params :refer [wrap-restful-params]]
            [ring.middleware.format-response :refer [wrap-restful-response]]
            ring.middleware.http-response
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.swagger.common :refer [deep-merge]]
            [ring.swagger.middleware :as rsm]
            [ring.swagger.coerce :as rsc]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
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

(def rethrow-exceptions? ::rethrow-exceptions?)

(defn default-exception-handler [^Exception e]
  (.printStackTrace e)
  (internal-server-error {:type "unknown-exception"
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
        (if (rethrow-exceptions? request)
          (throw e)
          (exception-handler e))))))

;;
;; Component integration
;;

(defn wrap-components
  "Assoc given components to the request."
  [handler components]
  (fn [req]
    (handler
      (assoc req ::components components))))

(defn get-components [req]
  (::components req))

;;
;; Ring-swagger options
;;

(defn wrap-options
  "Injects compojure-api options into the request."
  [handler options]
  (fn [request]
    (handler (update-in request [::options] merge options))))

(defn get-options
  "Extracts compojure-api options from the request."
  [request]
  (::options request))

;;
;; coercion
;;

(s/defschema CoercionType (s/enum :body :string :response))

(def default-coercion-matchers
  {:body rsc/json-schema-coercion-matcher
   :string rsc/query-schema-coercion-matcher
   :response rsc/json-schema-coercion-matcher})

(def no-response-coercion
  (dissoc default-coercion-matchers :response))

(defn get-coercion-matcher-provider [request]
  (let [provider (or (:coercion (get-options request))
                     (fn [_] default-coercion-matchers))]
    (provider request)))

(defn wrap-coercion [handler coercion]
  (fn [request]
    (handler (assoc-in request [::options :coercion] coercion))))

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

(defn ->mime-types [formats] (map mime-types formats))

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
  {:format {:formats [:json-kw :yaml-kw :edn :transit-json :transit-msgpack]
            :params-opts {}
            :response-opts {}}
   :validation-errors {:error-handler nil
                       :catch-core-errors? nil}
   :exceptions {:exception-handler default-exception-handler}
   :ring-swagger nil})

;; TODO: test all options! (https://github.com/metosin/compojure-api/issues/137)
(defn api-middleware
  "Opinionated chain of middlewares for web apis. Takes options-map, with namespaces
   options for the used middlewares (see middlewares for full details on options):

   - **:exceptions**                for *compojure.api.middleware/wrap-exceptions*
       - **:exception-handler**       function to handle uncaught exceptions

   - **:validation-errors**         for *ring.swagger.middleware/wrap-validation-errors*
       - **:error-handler**           function to handle ring-swagger schema exceptions
       - **:catch-core-errors?**      whether to catch also `:schema.core/errors`

   - **:format**                    for ring-middleware-format middlewares
       - **:formats**                 sequence of supported formats, e.g. `[:json-kw :edn]`
       - **:param-opts**              for *ring.middleware.format-params/wrap-restful-params*,
                                      e.g. `{:transit-json {:options {:handlers readers}}}`
       - **:response-opts**           for *ring.middleware.format-params/wrap-restful-response*,
                                      e.g. `{:transit-json {:handlers writers}}`

   - **:ring-swagger**              options for ring-swagger's swagger-json method.
                                    e.g. `{:ignore-missing-mappings? true}`

   - **:coercion**                  A function from request->type->coercion-matcher, used
                                    in enpoint coersion for :json, :query and :response.
                                    Defaults to `compojure.api.middleware/default-coercion-matchers`

   - **:components**                Components which should be accessible to handlers using
                                    :components restructuring. (If you are using defapi,
                                    you might want to take look at using wrap-components
                                    middleware manually.)"
  [handler & [options]]
  (let [options (deep-merge api-middleware-defaults options)
        {:keys [exceptions validation-errors format components]} options
        {:keys [formats params-opts response-opts]} format]
    (-> handler
        (cond-> components (wrap-components components))
        ring.middleware.http-response/wrap-http-response
        (rsm/wrap-validation-errors validation-errors)
        (wrap-exceptions exceptions)
        (rsm/wrap-swagger-data {:produces (->mime-types (remove response-only-mimes formats))
                                :consumes (->mime-types formats)})
        (wrap-options (select-keys options [:ring-swagger :coercion]))
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
