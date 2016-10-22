(ns compojure.api.middleware
  (:require [compojure.core :refer :all]
            [compojure.api.exception :as ex]
            [compojure.api.impl.logging :as logging]
            [ring.middleware.http-response]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            [muuntaja.middleware]
            [muuntaja.core]
            [ring.swagger.common :as rsc]
            [ring.swagger.middleware :as rsm]
            [ring.swagger.coerce :as coerce]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [clojure.lang ArityException]))

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
                type (or (get ex/mapped-exception-types type) type)
                handler (or (get handlers type) default-handler)]
            ; FIXME: Used for validate
            (if (rethrow-exceptions? request)
              (throw e)
              (call-error-handler handler e (assoc data :type type) request))))))))

;;
;; Component integration
;;

(defn wrap-components
  "Assoc given components to the request."
  [handler components]
  (fn [req]
    (handler (assoc req ::components components))))

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

(defn coercion-matchers [request]
  (let [options (get-options request)]
    (if (contains? options :coercion)
      (if-let [provider (:coercion options)]
        (provider request))
      default-coercion-matchers)))

(def coercion-request-ks [::options :coercion])

(defn wrap-coercion [handler coercion]
  (fn [request]
    (handler (assoc-in request coercion-request-ks coercion))))

;;
;; Muuntaja
;;

(defn encode?
  "Returns true if the response body is serializable: body is a
  collection or response has key :compojure.api.meta/serializable?"
  [_ response]
  (or (:compojure.api.meta/serializable? response)
      (coll? (:body response))))

(defn create-muuntaja [options]
  (if options
    (muuntaja.core/create
      (->
        (if (= ::defaults options)
          muuntaja.core/default-options
          options)
        (assoc :encode? encode?)))))

;;
;; Api Middleware
;;

(def api-middleware-defaults
  {:formats ::defaults
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

  - **:exceptions**                for *compojure.api.middleware/wrap-exceptions* (nil to unmount it)
      - **:handlers**                Map of error handlers for different exception types, type refers to `:type` key in ExceptionInfo data.

  - **:formats**                   for Muuntaja middleware. Value can be a valid muuntaja options-map,
                                   a created Muuntaja or nil (to unmount it). See
                                   https://github.com/metosin/muuntaja/wiki/Configuration for details.

  - **:ring-swagger**              options for ring-swagger's swagger-json method.
                                   e.g. `{:ignore-missing-mappings? true}`

  - **:coercion**                  A function from request->type->coercion-matcher, used
                                   in endpoint coercion for :body, :string and :response.
                                   Defaults to `(constantly compojure.api.middleware/default-coercion-matchers)`
                                   Setting value to nil disables all coercion

  - **:components**                Components which should be accessible to handlers using
                                   :components restructuring. (If you are using api,
                                   you might want to take look at using wrap-components
                                   middleware manually.). Defaults to nil (middleware not mounted)."
  ([handler]
   (api-middleware handler nil))
  ([handler options]
   (let [options (rsc/deep-merge api-middleware-defaults options)
         {:keys [exceptions components formats]} options
         muuntaja (create-muuntaja formats)]

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

     ;; 1.2.0+
     (assert (not (map? (:format options)))
             (str "ERROR: Option [:format] is not used with 1.2.0 or later. Compojure-api uses now Muuntaja insted of"
                  "ring-middleware-format and the new formatting options for it should be under [:formats]. See "
                  "'(doc compojure.api.middleware/api-middleware)' for more details."))

     (cond-> handler
             components (wrap-components components)
             true ring.middleware.http-response/wrap-http-response
             muuntaja (rsm/wrap-swagger-data (select-keys muuntaja [:consumes :produces]))
             true (wrap-options (select-keys options [:ring-swagger :coercion]))
             muuntaja (muuntaja.middleware/wrap-format-request muuntaja)
             exceptions (wrap-exceptions exceptions)
             muuntaja (muuntaja.middleware/wrap-format-response muuntaja)
             muuntaja (muuntaja.middleware/wrap-format-negotiate muuntaja)
             true wrap-keyword-params
             true wrap-nested-params
             true wrap-params))))

(defn middleware-fn [middleware]
  (if (vector? middleware)
    (let [[f & arguments] middleware]
      #(apply f % arguments))
    middleware))

(defn compose-middleware [middleware]
  (->> middleware
       (keep identity)
       (map middleware-fn)
       (apply comp identity)))
