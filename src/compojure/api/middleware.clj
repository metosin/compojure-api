(ns compojure.api.middleware
  (:require [compojure.core :refer :all]
            [compojure.api.exception :as ex]
            [compojure.api.common :as common]
            [compojure.api.coercion :as coercion]
            [compojure.api.request :as request]
            [compojure.api.impl.logging :as logging]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.swagger.coerce :as coerce]

            [muuntaja.middleware]
            [muuntaja.core :as m]

            [ring.swagger.common :as rsc]
            [ring.util.http-response :refer :all])
  (:import [clojure.lang ArityException]
           [com.fasterxml.jackson.datatype.joda JodaModule]))

;;
;; Catch exceptions
;;

(defn- super-classes [^Class k]
  (loop [sk (.getSuperclass k), ks []]
    (if-not (= sk Object)
      (recur (.getSuperclass sk) (conj ks sk))
      ks)))

(defn- call-error-handler [default-handler handlers error request]
  (let [{:keys [type] :as data} (ex-data error)
        type (or (get ex/mapped-exception-types type) type)
        ex-class (class error)
        error-handler (or (get handlers type)
                          (get handlers ex-class)
                          (some
                            (partial get handlers)
                            (super-classes ex-class))
                          default-handler)]
    (try
      (error-handler error (assoc data :type type) request)
      (catch ArityException _
        (logging/log! :warn "Error-handler arity has been changed.")
        (error-handler error)))))

(defn wrap-exceptions
  "Catches all exceptions and delegates to correct error handler according to :type of Exceptions
  - **:handlers** - a map from exception type to handler
    - **:compojure.api.exception/default** - Handler used when exception type doesn't match other handler,
                                             by default prints stack trace."
  [handler {:keys [handlers]}]
  (let [default-handler (get handlers ::ex/default ex/safe-handler)
        rethrow-or-respond (fn [e request respond raise]
                             ;; FIXME: Used for validate
                             (if (::rethrow-exceptions? request)
                               (raise e)
                               (respond (call-error-handler default-handler handlers e request))))]
    (assert (ifn? default-handler) "Default exception handler must be a function.")
    (fn
      ([request]
       (try
         (handler request)
         (catch Throwable e
           (rethrow-or-respond e request identity #(throw %)))))
      ([request respond raise]
       (try
         (handler request respond (fn [e] (rethrow-or-respond e request respond raise)))
         (catch Throwable e
           (rethrow-or-respond e request respond raise)))))))

;;
;; Component integration
;;

(defn wrap-components
  "Assoc given components to the request."
  [handler components]
  (fn
    ([req]
     (handler (assoc req ::components components)))
    ([req respond raise]
     (handler (assoc req ::components components) respond raise))))

(defn get-components [req]
  (::components req))

;;
;; Options
;;

;; 1.1.x
(defn get-options
  "Extracts compojure-api options from the request."
  [request]
  (::options request))

(defn wrap-inject-data
  "Injects data into the request."
  [handler data]
  (fn
    ([request]
     (handler (common/fast-map-merge request data)))
    ([request respond raise]
     (handler (common/fast-map-merge request data) respond raise))))

;;
;; coercion
;;

(defn wrap-coercion [handler coercion]
  (fn
    ([request]
     (handler (coercion/set-request-coercion request coercion)))
    ([request respond raise]
     (handler (coercion/set-request-coercion request coercion) respond raise))))

;; 1.1.x
(def default-coercion-matchers
  {:body coerce/json-schema-coercion-matcher
   :string coerce/query-schema-coercion-matcher
   :response coerce/json-schema-coercion-matcher})

;; 1.1.x
(defn coercion-matchers [request]
  (let [options (get-options request)]
    (if (contains? options :coercion)
      (if-let [provider (:coercion options)]
        (provider request))
      default-coercion-matchers)))

;;
;; Muuntaja
;;

(defn encode?
  "Returns true if the response body is serializable: body is a
  collection or response has key :compojure.api.meta/serializable?"
  [_ response]
  (or (:compojure.api.meta/serializable? response)
      (coll? (:body response))))

(def default-muuntaja-options
  (assoc-in
    m/default-options
    [:formats "application/json" :opts :modules]
    [(JodaModule.)]))

(defn create-muuntaja
  ([]
   (create-muuntaja default-muuntaja-options))
  ([muuntaja-or-options]
   (let [opts #(assoc-in % [:http :encode-response-body?] encode?)]
     (cond

       (nil? muuntaja-or-options)
       nil

       (= ::default muuntaja-or-options)
       (m/create (opts default-muuntaja-options))

       (m/muuntaja? muuntaja-or-options)
       (-> muuntaja-or-options (m/options) (opts) (m/create))

       (map? muuntaja-or-options)
       (m/create (opts muuntaja-or-options))

       :else
       (throw
         (ex-info
           (str "Invalid :formats - " muuntaja-or-options)
           {:options muuntaja-or-options}))))))

;;
;; middleware
;;

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

;;
;; swagger-data
;;

(defn set-swagger-data
  "Add extra top-level swagger-data into a request.
  Data can be read with get-swagger-data."
  ([request data]
   (update request ::request/swagger (fnil conj []) data)))

(defn get-swagger-data
  "Reads and deep-merges top-level swagger-data from request,
  pushed in by set-swagger-data."
  [request]
  (apply rsc/deep-merge (::request/swagger request)))

(defn wrap-swagger-data
  "Middleware that adds top level swagger-data into request."
  [handler data]
  (fn
    ([request]
     (handler (set-swagger-data request data)))
    ([request respond raise]
     (handler (set-swagger-data request data) respond raise))))

;;
;; Api Middleware
;;

(def api-middleware-defaults
  {::api-middleware-defaults true
   :formats ::default
   :exceptions {:handlers {:ring.util.http-response/response ex/http-response-handler
                           ::ex/request-validation ex/request-validation-handler
                           ::ex/request-parsing ex/request-parsing-handler
                           ::ex/response-validation ex/response-validation-handler
                           ::ex/default ex/safe-handler}}
   :middleware nil
   :coercion coercion/default-coercion
   :ring-swagger nil})

(defn api-middleware-options [options]
  (rsc/deep-merge api-middleware-defaults options))

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

  - **:formatter**                 either :ring-middleware-format or :muuntaja.
                                   During 2.x pre-releases, this will be a required key, unless
                                   :formats is provided, which is equivalent to setting to :muuntaja.
                                   Stable 2.x releases will default to :ring-middleware-format if
                                   not provided or :format is set, unless :formats is provided,
                                   which is equivalent to setting to :muuntaja.
                                   Stable 2.x will print a deprecation warning if implicitly
                                   or explicitly set to :ring-middleware-format.

  - **:exceptions**                for *compojure.api.middleware/wrap-exceptions* (nil to unmount it)
      - **:handlers**                Map of error handlers for different exception types, type refers to `:type` key in ExceptionInfo data.

  - **:formats**                   for Muuntaja middleware. Value can be a valid muuntaja options-map,
                                   a Muuntaja instance or nil (to unmount it). See
                                   https://github.com/metosin/muuntaja/blob/master/doc/Configuration.md for details.

  - **:middleware**                vector of extra middleware to be applied last (just before the handler).

  - **:ring-swagger**              options for ring-swagger's swagger-json method.
                                   e.g. `{:ignore-missing-mappings? true}`

  - **:coercion**                  A function from request->type->coercion-matcher, used
                                   in endpoint coercion for types :body, :string and :response.
                                   Defaults to `compojure.api.middleware/default-coercion`
                                   Setting value to nil disables all coercion.

  - **:components**                Components which should be accessible to handlers using
                                   :components restructuring. (If you are using api,
                                   you might want to take look at using wrap-components
                                   middleware manually.). Defaults to nil (middleware not mounted)."
  ([handler]
   (throw (ex-info (str "ERROR: Please set `:formatter :muuntaja` in the options map of `api-middleware.\n"
                        "e.g., (api-middleware <handler> {:formatter :muuntaja})\n"
                        "To prepare for backwards compatibility with compojure-api 1.x, the formatting library must be \n"
                        "explicitly chosen if not configured by `:format` (ring-middleware-format) or\n"
                        "`:formats` (muuntaja). Once 2.x is stable, the default will be `:formatter :ring-middleware-format`.")
                   {}))
   (api-middleware handler api-middleware-defaults))
  ([handler options]
   (when (and (::api-middleware-defaults options)
              (not (:formatter options))
              (not (System/getProperty "compojure.api.middleware.global-default-formatter")))
     (throw (ex-info (str "ERROR: Please set `:formatter :muuntaja` in the options map of `api-middleware.\n"
                          "e.g., (api-middleware <handler> {:formatter :muuntaja})\n"
                          "To prepare for backwards compatibility with compojure-api 1.x, the formatting library must be\n"
                          "explicitly chosen if not configured by `:format` (ring-middleware-format) or\n"
                          ":formats (muuntaja). Once 2.x is stable, the default will be `:formatter :ring-middleware-format`.\n"
                          "To globally override the default formatter, use -Dcompojure.api.middleware.global-default-formatter=:muuntaja")
                     {})))
   (let [formatter (or (:formatter options)
                       (when (or (contains? options :formats)
                                 (= (System/getProperty "compojure.api.middleware.global-default-formatter")
                                    ":muuntaja"))
                         :muuntaja)
                       (throw (ex-info (str "ERROR: Please set `:formatter :muuntaja` in the options map of `api-middleware.\n"
                                            "e.g., (api-middleware <handler> {:formatter :muuntaja})\n"
                                            "To prepare for backwards compatibility with compojure-api 1.x, the formatting library must be\n"
                                            "explicitly chosen if not configured by `:format` (ring-middleware-format) or\n"
                                            ":formats (muuntaja). Once 2.x is stable, the default will be `:formatter :ring-middleware-format`.\n"
                                            "To globally override the default formatter, use -Dcompojure.api.middleware.global-default-formatter=:muuntaja")
                                       {}))
                       ;; TODO 2.x stable
                       :ring-middleware-format)
         _ (assert (= :muuntaja formatter)
                   (str "Invalid :formatter: " (pr-str formatter) ". Must be :muuntaja."))
         options (api-middleware-options options)
         {:keys [exceptions components formats middleware ring-swagger coercion]} options
         muuntaja (create-muuntaja formats)]

     ;; 1.2.0+
     (assert (not (contains? options :format))
             (str "ERROR: Option [:format] is not used with 2.* version.\n"
                  "Compojure-api uses now Muuntaja insted of ring-middleware-format,\n"
                  "the new formatting options for it should be under [:formats]. See\n"
                  "[[api-middleware]] documentation for more details.\n"))

     (-> handler
         (cond-> middleware ((compose-middleware middleware)))
         (cond-> components (wrap-components components))
         (cond-> muuntaja (wrap-swagger-data {:consumes (m/decodes muuntaja)
                                              :produces (m/encodes muuntaja)}))
         (wrap-inject-data
           (cond-> {::request/coercion coercion}
                   muuntaja (assoc ::request/muuntaja muuntaja)
                   ring-swagger (assoc ::request/ring-swagger ring-swagger)))
         (cond-> muuntaja (muuntaja.middleware/wrap-params))
         ;; all but request-parsing exceptions (to make :body-params visible)
         (cond-> exceptions (wrap-exceptions
                              (update exceptions :handlers dissoc ::ex/request-parsing)))
         (cond-> muuntaja (muuntaja.middleware/wrap-format-request muuntaja))
         ;; just request-parsing exceptions
         (cond-> exceptions (wrap-exceptions
                              (update exceptions :handlers select-keys [::ex/request-parsing])))
         (cond-> muuntaja (muuntaja.middleware/wrap-format-response muuntaja))
         (cond-> muuntaja (muuntaja.middleware/wrap-format-negotiate muuntaja))

         ;; these are really slow middleware, 4.5µs => 9.1µs (+100%)

         ;; 7.8µs => 9.1µs (+27%)
         wrap-keyword-params
         ;; 7.1µs => 7.8µs (+23%)
         wrap-nested-params
         ;; 4.5µs => 7.1µs (+50%)
         wrap-params))))

(defn wrap-format
  "Muuntaja format middleware. Can be safely mounted on top of multiple api

  - **:formats**                   for Muuntaja middleware. Value can be a valid muuntaja options-map,
                                   a Muuntaja instance or nil (to unmount it). See
                                   https://github.com/metosin/muuntaja/blob/master/doc/Configuration.md for details."
  ([handler]
   (wrap-format handler {:formats ::default}))
  ([handler options]
   (let [options (rsc/deep-merge {:formats ::default} options)
         muuntaja (create-muuntaja (:formats options))]

     (cond-> handler
             muuntaja (-> (wrap-swagger-data {:consumes (m/decodes muuntaja)
                                              :produces (m/encodes muuntaja)})
                          (muuntaja.middleware/wrap-format-request muuntaja)
                          (muuntaja.middleware/wrap-format-response muuntaja)
                          (muuntaja.middleware/wrap-format-negotiate muuntaja))))))
