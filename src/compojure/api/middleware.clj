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
            [slingshot.slingshot :refer [try+ throw+]]
            [schema.core :as s]
            [schema.utils :as su])
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

(defn unknown-exception-body [^Exception e]
  {:type "unknown-exception"
   :class (.getName (.getClass e))})

(defn print-stack-trace-exception-handler [^Exception e error-type request]
  (.printStackTrace e)
  (internal-server-error (unknown-exception-body e)))

(defn stringify-error [error]
  (if (su/error? error)
    (rsm/stringify-error (su/error-val error))
    (str error)))

(defn create-errors-body [error]
    {:errors (stringify-error error)})

(defn internal-server-error-handler [error error-type request]
  (internal-server-error (create-errors-body error)))

(defn bad-request-error-handler [error error-type request]
  (bad-request (create-errors-body error)))

(defn- deprecated! [& args]
  (apply println (concat ["DEPRECATED in compojure.api.middleware:"] args)))

(defn support-deprecated-error-handler-config
  [{:keys [error-handlers exception-handler error-handler catch-core-errors?]}]
  (let [request-validation-error-handler (if (fn? error-handler)
                                           (do
                                             (deprecated! "{:validation-errors} config is deprecated, use {:exceptions {:error-handlers {:compojure.api.middleware/request-validation}}} instead. See docs for details")
                                             {::request-validation error-handler})
                                           {})
        catch-core-errors (if catch-core-errors?
                            (do
                              (deprecated! "{:validation-errors} config is deprecated, use {:exceptions {:error-handlers {:schema.core/error}}} instead. See docs for details")
                              {:schema.core/error (if (fn? error-handler) error-handler bad-request-error-handler)})
                            {})
        exception-handler (if (fn? exception-handler)
                                (do
                                  (deprecated! "{:exceptions {:exception-handler}} config is deprecated, use {:exceptions {:error-handlers {:compojure.api.middleware/exception}}} instead. See docs for details")
                                  {::exception exception-handler})
                                {})]
  (merge error-handlers request-validation-error-handler catch-core-errors exception-handler)))

(defn- call-error-handler [error-handler error error-type request]
  {:pre [(fn? error-handler)]}
  (try
    (error-handler error error-type request)
    (catch clojure.lang.ArityException e
      (deprecated! "error-handler function without error-type and request arguments is deprecated, see docs for details.")
      (error-handler error))))

(defn wrap-exceptions
  "Catches all exceptions and delegates to right error handler accoring to :type of Exceptions
    :error-handlers - a map from exception type to handler"
  [handler error-handlers]
  {:pre [(map? error-handlers) (contains? error-handlers ::response-validation) (contains? error-handlers ::request-validation) (contains? error-handlers ::exception)]}
  (fn [request]
    (try+
      (handler request)
      (catch [:type :ring.swagger.schema/validation] error-container
        (call-error-handler (::request-validation error-handlers)  error-container ::request-validation request))
      (catch [:type :compojure.api.meta/response-validation] error-container
        (call-error-handler (::response-validation error-handlers) error-container ::response-validation request))
      (catch (and (map? %) (contains? % :type) (contains? error-handlers (:type %))) error
        (let [type (:type error)]
          (call-error-handler (get error-handlers type) error type request)))
      (catch Object _
        (if (rethrow-exceptions? request)
          (throw (:throwable &throw-context))
          (call-error-handler (::exception error-handlers) (:throwable &throw-context) ::exception request))))))

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

(defn handle-req-error [error-handlers]
  {:pre [(map? error-handlers)]}
  (fn [^Throwable e handler request]
    (cond
      (instance? JsonParseException e)
      (call-error-handler (::request-validation error-handlers) e ::request-validation request)

      (instance? ParserException e)
      (call-error-handler (::request-validation error-handlers) e ::request-validation request)

      :else
      (call-error-handler (::exception error-handlers) e ::exception request))))

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
   :exceptions {:error-handlers {::request-validation bad-request-error-handler
                                 ::response-validation internal-server-error-handler
                                 ::exception print-stack-trace-exception-handler}}
   :ring-swagger nil})

;; TODO: test all options! (https://github.com/metosin/compojure-api/issues/137)
(defn api-middleware
  "Opinionated chain of middlewares for web apis. Takes options-map, with namespaces
   options for the used middlewares (see middlewares for full details on options):

   - **:exceptions**                for *compojure.api.middleware/wrap-exceptions*
       - **:error-handlers**          map of error handlers for different error types.
                                      An error handler is a function of type specific error object (eg. schema.utils.ErrorContainer or java.lang.Exception), error type and request -> response
                                      Default:
                                      {:compojure.api.middleware/request-validation compojure.api.middleware/bad-request-error-handler
                                       :compojure.api.middleware/response-validation compojure.api.middleware/internal-server-error-handler
                                       :compojure.api.middleware/exception compojure.api.middleware/print-stack-trace-exception-handler}

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
        {:keys [formats params-opts response-opts]} format
        error-handlers (support-deprecated-error-handler-config (merge exceptions validation-errors))]
    (-> handler
        (cond-> components (wrap-components components))
        ring.middleware.http-response/wrap-http-response
        (wrap-exceptions error-handlers)
        (rsm/wrap-swagger-data {:produces (->mime-types (remove response-only-mimes formats))
                                :consumes (->mime-types formats)})
        (wrap-options (select-keys options [:ring-swagger :coercion]))
        (wrap-restful-params
          (merge {:formats (remove response-only-mimes formats)
                  :handle-error (handle-req-error error-handlers)}
                 params-opts))
        (wrap-restful-response
          (merge {:formats formats
                  :predicate serializable?}
                 response-opts))
        wrap-keyword-params
        wrap-nested-params
        wrap-params)))
