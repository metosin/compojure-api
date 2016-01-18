(ns compojure.api.middleware
  (:require [compojure.core :refer :all]
            [compojure.api.exception :as ex]
            [compojure.api.impl.logging :as logging]
            [ring.middleware.format-params :refer [wrap-restful-params]]
            [ring.middleware.format-response :refer [wrap-restful-response]]
            ring.middleware.http-response
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.swagger.common :as rsc]
            [ring.swagger.middleware :as rsm]
            [ring.swagger.coerce :as coerce]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [com.fasterxml.jackson.core JsonParseException]
           [org.yaml.snakeyaml.parser ParserException]
           [clojure.lang ArityException]))

;;
;; Catch exceptions
;;

(def rethrow-exceptions? ::rethrow-exceptions?)

(defn- call-error-handler [error-handler error data request]
  (try
    (error-handler error data request)
    (catch ArityException _
      (logging/log! :warn "Error-handler arity has been changed.")
      (error-handler error))))

(defn wrap-exceptions
  "Catches all exceptions and delegates to correct error handler according to :type of Exceptions
  - **:handlers** - a map from exception type to handler
    - **:compojure.api.exception/default** - Handler used when exception type doesn't match other handler,
                                             by default prints stack trace."
  [handler {:keys [handlers]}]
  (let [default-handler (get handlers ::ex/default ex/safe-handler)]
    (assert (fn? default-handler) "Default exception handler must be a function.")
    (fn [request]
      (try
        (handler request)
        (catch Throwable e
          (let [{:keys [type] :as data} (ex-data e)
                type (or (get ex/legacy-exception-types type) type)
                handler (or (get handlers type) default-handler)]
            ; FIXME: Used for validate
            (if (rethrow-exceptions? request)
              (throw e)
              (call-error-handler handler e data request))))))))

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
  {:body coerce/json-schema-coercion-matcher
   :string coerce/query-schema-coercion-matcher
   :response coerce/json-schema-coercion-matcher})

(def no-response-coercion
  (constantly (dissoc default-coercion-matchers :response)))

(defn get-coercion-matcher-provider [request]
  (if-let [provider (:coercion (get-options request))]
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

(defn handle-req-error [^Throwable e handler request]
  ;; Ring-middleware-format catches all exceptions in req handling,
  ;; i.e. (handler req) is inside try-catch. If r-m-f was changed to catch only
  ;; exceptions from parsing the request, we wouldn't need to check the exception class.
  (if (or (instance? JsonParseException e) (instance? ParserException e))
    (throw (ex-info "Error parsing request" {:type ::ex/request-parsing} e))
    (throw e)))

(defn serializable?
  "Predicate which returns true if the response body is serializable.
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
   :exceptions {:handlers {::ex/request-validation ex/request-validation-handler
                           ::ex/request-parsing ex/request-parsing-handler
                           ::ex/response-validation ex/response-validation-handler
                           ::ex/default ex/safe-handler}}
   :coercion (constantly default-coercion-matchers)
   :ring-swagger nil})

;; TODO: test all options! (https://github.com/metosin/compojure-api/issues/137)
(defn api-middleware
  "Opinionated chain of middlewares for web apis. Takes optional options-map.

  ### Exception handlers

  An error handler is a function of exception, ex-data and request to response.

  When defining these options, it is suggested to use alias for the exceptions namespace,
  e.g. `[compojure.api.exception :as ex]`.

  Default:

      {::ex/request-validation  ex/request-validation-handler
       ::ex/request-parsing     ex/request-parsing-handler
       ::ex/response-validation ex/response-validation-handler
       ::ex/default             ex/safe-handler}

  Note: Because the handlers are merged into default handlers map, to disable default handler you
  need to provide `nil` value as handler.

  Note: To catch Schema errors use `{:schema.core/error ex/schema-error-handler}`.

  ### Options

  - **:exceptions**                for *compojure.api.middleware/wrap-exceptions*
      - **:handlers**                Map of error handlers for different exception types, type refers to `:type` key in ExceptionInfo data.

  - **:format**                    for ring-middleware-format middlewares
      - **:formats**                 sequence of supported formats, e.g. `[:json-kw :edn]`
      - **:params-opts**             for *ring.middleware.format-params/wrap-restful-params*,
                                     e.g. `{:transit-json {:handlers readers}}`
      - **:response-opts**           for *ring.middleware.format-params/wrap-restful-response*,
                                     e.g. `{:transit-json {:handlers writers}}`

  - **:ring-swagger**              options for ring-swagger's swagger-json method.
                                   e.g. `{:ignore-missing-mappings? true}`

  - **:coercion**                  A function from request->type->coercion-matcher, used
                                   in endpoint coercion for :json, :query and :response.
                                   Defaults to `(constantly compojure.api.middleware/default-coercion-matchers)`

  - **:components**                Components which should be accessible to handlers using
                                   :components restructuring. (If you are using api,
                                   you might want to take look at using wrap-components
                                   middleware manually.)"
  [handler & [options]]
  (let [options (rsc/deep-merge api-middleware-defaults options)
        {:keys [exceptions format components]} options
        {:keys [formats params-opts response-opts]} format]
    ; Break at compile time if there are deprecated options
    ; These three have been deprecated with 0.23
    (assert (not (:error-handler (:validation-errors options)))
            (str "ERROR: Option: [:validation-errors :error-handler] is no longer supported, "
                 "use {:exceptions {:handlers {:compojure.api.middleware/request-validation your-handler}}} instead."
                 "Also note that exception-handler arity has been changed."))
    (assert (not (:catch-core-errors? (:validation-errors options)))
            (str "ERROR: Option [:validation-errors :catch-core-errors?] is no longer supported, "
                 "use {:exceptions {:handlers {:schema.core/error compojure.api.exception/schema-error-handler}}} instead."
                 "Also note that exception-handler arity has been changed."))
    (assert (not (:exception-handler (:exceptions options)))
            (str "ERROR: Option [:exceptions :exception-handler] is no longer supported, "
                 "use {:exceptions {:handlers {:compojure.api.exception/default your-handler}}} instead."
                 "Also note that exception-handler arity has been changed."))
    (assert (not (map? (:coercion options)))
            (str "ERROR: Option [:coercion] should be a funtion of request->type->matcher, got a map instead."
                 "From 1.0.0 onwards, you should wrap your type->matcher map into a request-> function. If you "
                 "want to apply the matchers for all request types, wrap your option with 'constantly'"))
    (-> handler
        (cond-> components (wrap-components components))
        ring.middleware.http-response/wrap-http-response
        (rsm/wrap-swagger-data {:produces (->mime-types (remove response-only-mimes formats))
                                :consumes (->mime-types formats)})
        (wrap-options (select-keys options [:ring-swagger :coercion]))
        (wrap-restful-params
          {:formats (remove response-only-mimes formats)
           :handle-error handle-req-error
           :format-options params-opts})
        (wrap-exceptions exceptions)
        (wrap-restful-response
          {:formats formats
           :predicate serializable?
           :format-options response-opts})
        wrap-keyword-params
        wrap-nested-params
        wrap-params)))
