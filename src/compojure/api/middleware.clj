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
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [clojure.lang ArityException]
           [com.fasterxml.jackson.core JsonParseException]
           [org.yaml.snakeyaml.parser ParserException]
           [com.fasterxml.jackson.datatype.joda JodaModule]))

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

(def no-response-coercion
  (constantly (dissoc default-coercion-matchers :response)))

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
;; ring-middleware-format stuff
;;

(def ^:private default-mime-types
  {:json "application/json"
   :json-kw "application/json"
   :edn "application/edn"
   :clojure "application/clojure"
   :yaml "application/x-yaml"
   :yaml-kw "application/x-yaml"
   :yaml-in-html "text/html"
   :transit-json "application/transit+json"
   :transit-msgpack "application/transit+msgpack"})

(defn mime-types
  [format]
  (get default-mime-types format
       (some-> format :content-type)))

(def ^:private response-only-mimes #{:clojure :yaml-in-html})

(defn ->mime-types [formats] (keep mime-types formats))

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

(def api-middleware-defaults-v1
  {:format {:formats [:json-kw :yaml-kw :edn :transit-json :transit-msgpack]
            :params-opts {}
            :response-opts {}}
   :exceptions {:handlers {::ex/request-validation ex/request-validation-handler
                           ::ex/request-parsing ex/request-parsing-handler
                           ::ex/response-validation ex/response-validation-handler
                           ::ex/default ex/safe-handler}}
   :coercion (constantly default-coercion-matchers)
   :ring-swagger nil})

(def api-middleware-defaults-v2
  {:formats ::default
   :exceptions {:handlers {:ring.util.http-response/response ex/http-response-handler
                           ::ex/request-validation ex/request-validation-handler
                           ::ex/request-parsing ex/request-parsing-handler
                           ::ex/response-validation ex/response-validation-handler
                           ::ex/default ex/safe-handler}}
   :middleware nil
   :coercion coercion/default-coercion
   :ring-swagger nil})

(defn api-middleware-options-v1 [options]
  (rsc/deep-merge api-middleware-defaults-v1 options))

(defn api-middleware-options-v2 [options]
  (rsc/deep-merge api-middleware-defaults-v2 options))

(defonce rmf-format->muuntaja-formats (atom {}))

(comment
  (do @rmf-format->muuntaja-formats
    ))

(defn- format->formats [format]
  (when-some [{:keys [formats params-opts response-opts]} format]
    (assert (empty? params-opts) (pr-str params-opts))
    (assert (empty? response-opts) (pr-str params-opts))
    (reduce (fn [m k]
              (case k
                (let [formats (get @rmf-format->muuntaja-formats k)]
                  (assert formats (str "Unknown translation for :formats, please ensure compojure.api.middleware.rmf-muuntaja-adapter is loaded: "
                                       (pr-str k)
                                       "\n" (vec (keys @rmf-format->muuntaja-formats))))
                  formats)))
            {} formats)))

(defn- v1->v2 [options]
  (let [{:keys [format] :as options} (api-middleware-options-v1 options)]
    (-> m/default-options
        (assoc :formats (when (some? format)
                          (update m/default-options :formats into (format->formats format))))
        (dissoc :format))))

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
                                   a Muuntaja instance or nil (to unmount it). See
                                   https://github.com/metosin/muuntaja/blob/master/doc/Configuration.md for details.
                                   Cannot be combined with :format.

  - **:format**                    for ring-middleware-format middlewares (nil to unmount it). Cannot be combined with :formats.
      - **:formats**                 sequence of supported formats, e.g. `[:json-kw :edn]`
      - **:params-opts**             for *ring.middleware.format-params/wrap-restful-params*,
                                     e.g. `{:transit-json {:handlers readers}}`
      - **:response-opts**           for *ring.middleware.format-params/wrap-restful-response*,
                                     e.g. `{:transit-json {:handlers writers}}`

  - **:middleware**                vector of extra middleware to be applied last (just before the handler).

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
  ([handler] (api-middleware handler nil))
  ([handler options]
   (let [v1? (or (contains? options :format)
                 (seq @rmf-format->muuntaja-formats))
         _ (prn "rmf" @rmf-format->muuntaja-formats)
         _ (assert v1? "Please require compojure.api.middleware.rmf-muuntaja-adapter for future compatability")
         _ (when v1?
             (assert (not (contains? options :formats))
                     "Cannot combine :format and :formats"))
         options (if v1?
                   (v1->v2 options)
                   (api-middleware-options-v2 options))
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
