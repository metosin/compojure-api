(ns compojure.api.middleware
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.api.exception :as ex]
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

(defn wrap-exceptions
  "Catches all exceptions and delegates to right error handler accoring to :type of Exceptions
   - **:error-handlers** - a map from exception type to handler
     - **:compojure.api.exception/default** - Handler used when exception type doesn't match other handler,
                                              by default prints stack trace."
  [handler {:keys [error-handlers]}]
  (let [default-handler (get error-handlers ::ex/default ex/safe-handler)]
    (assert (fn? default-handler) "Default exception handler must be a function.")
    (fn [request]
      (try+
        (handler request)
        (catch (get % :type) {:keys [type] :as e}
          (let [type (or (get ex/legacy-exception-types type) type)]
            (if-let [handler (get error-handlers type)]
              (handler e type request)
              (default-handler (:throwable &throw-context) type request))))
        (catch Object _
          ; FIXME: Used for validate
          (if (rethrow-exceptions? request)
            (throw+)
            (default-handler (:throwable &throw-context) ::exception request)))))))

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

(defn handle-req-error [^Throwable e handler request]
  (if (or (instance? JsonParseException e) (instance? ParserException e))
    (throw+ {:type ::ex/request-validation} e)
    (throw+ e)))

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
   :exceptions {:error-handlers {::ex/request-validation  ex/request-validation-handler
                                 ::ex/response-validation ex/response-validation-handler
                                 ::ex/default             ex/safe-handler}}
   :ring-swagger nil})

;; TODO: test all options! (https://github.com/metosin/compojure-api/issues/137)
(defn api-middleware
  "Opinionated chain of middlewares for web apis. Takes options-map, with namespaces
   options for the used middlewares (see middlewares for full details on options):

   - **:exceptions**                for *compojure.api.middleware/wrap-exceptions*
       - **:error-handlers**          map of error handlers for different error types.
                                      An error handler is a function of type specific error object (eg. schema.utils.ErrorContainer or java.lang.Exception), error type and request -> response
                                      Default:
                                      {:compojure.api.exception/request-validation  compojure.api.exception/request-validation-handler
                                       :compojure.api.exception/response-validation compojure.api.exception/response-validation-handler
                                       :compojure.api.exception/default             compojure.api.exception/safe-handler}
                                      Note: Adding alias for exception namespace makes it easier to define these options.

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
        {:keys [exceptions format components]} options
        {:keys [formats params-opts response-opts]} format]
    ; Break at compile time if there are deprecated options
    (assert (not (:error-handler (:validation-errors options))) "Deprecated option: [:validation-errors :error-handler], use [:exceptions :error-handlers :compojure.api.middleware/request-validation] instead.")
    (assert (not (:catch-core-errors? (:validation-errors options))) "Deprecated option: [:validation-errors :catch-core-errors?], use [:exceptions :error-handlers :compojure.api.exception/request-validation] instead.")
    (assert (not (:exception-handler (:exceptions options))) "Deprecated option: [:exceptions :exception-handler], use [:exceptions :error-handlers :compojure.api.exception/default] instead.")
    (-> handler
        (cond-> components (wrap-components components))
        ring.middleware.http-response/wrap-http-response
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
        (wrap-exceptions exceptions)
        wrap-keyword-params
        wrap-nested-params
        wrap-params)))
