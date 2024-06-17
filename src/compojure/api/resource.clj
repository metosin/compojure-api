(ns compojure.api.resource
  (:require [compojure.api.routes :as routes]
            [compojure.api.coercion :as coercion]
            [compojure.api.methods :as methods]
            [ring.swagger.common :as rsc]
            [schema.core :as s]
            [plumbing.core :as p]
            [compojure.api.async]
            [compojure.api.middleware :as mw]
            [compojure.api.coercion.core :as cc]))

(def ^:private +mappings+
  {:methods methods/all-methods
   :parameters {:query-params [:query-params :query :string true]
                :body-params [:body-params :body :body false]
                :form-params [:form-params :formData :string true]
                :header-params [:headers :header :string true]
                :path-params [:route-params :path :string true]}})

(defn- swaggerize [info]
  (as-> info info
        (reduce-kv
          (fn [acc ring-key [_ swagger-key]]
            (if-let [schema (get-in acc [:parameters ring-key])]
              (update acc :parameters #(-> % (dissoc ring-key) (assoc swagger-key schema)))
              acc))
          info
          (:parameters +mappings+))
        (dissoc info :handler)))

(defn- inject-coercion [request info]
  (if (contains? info :coercion)
    (coercion/set-request-coercion request (:coercion info))
    request))

(defn- coerce-request [request info ks]
  (reduce-kv
    (fn [request ring-key [compojure-key _ type open?]]
      (if-let [model (get-in info (concat ks [:parameters ring-key]))]
        (let [coerced (coercion/coerce-request!
                        model compojure-key type (not= :body type) open? request)]
          (if open?
            (update request ring-key merge coerced)
            (assoc request ring-key coerced)))
        request))
    (inject-coercion request info)
    (:parameters +mappings+)))

(defn- coerce-response [response info request ks]
  (coercion/coerce-response! request response (get-in info (concat ks [:responses]))))

(defn- maybe-async [async? x]
  (if (and async? x) [x true]))

(defn- maybe-sync [x]
  (if x [x false]))

(defn- resolve-handler [info path-info route request-method async?]
  (and
    (or
      ;; directly under a context
      (= path-info "/")
      ;; under an compojure endpoint
      route
      ;; vanilla ring
      (nil? path-info))
    (let [[handler async] (or
                            (maybe-async async? (get-in info [request-method :async-handler]))
                            (maybe-sync (get-in info [request-method :handler]))
                            (maybe-async async? (get-in info [:async-handler]))
                            (maybe-sync (get-in info [:handler])))]
      (if handler
        [handler async]))))

(defn- middleware-chain [info request-method handler]
  (let [direct-mw (:middleware info)
        method-mw (:middleware (get info request-method))
        middleware (mw/compose-middleware (concat direct-mw method-mw))]
    (middleware handler)))

(defn- create-childs [info]
  (map
    (fn [[method info]]
      (routes/map->Route
        {:path "/"
         :method method
         :info {:public (swaggerize info)}}))
    (select-keys info (:methods +mappings+))))

(defn- handle-sync [info {:keys [request-method path-info :compojure/route] :as request}]
  (when-let [[raw-handler] (resolve-handler info path-info route request-method false)]
    (let [ks (if (contains? info request-method) [request-method] [])
          handler (middleware-chain info request-method raw-handler)]
      (-> (coerce-request request info ks)
          (handler)
          (compojure.response/render request)
          (coerce-response info request ks)))))

(defn- handle-async [info {:keys [request-method path-info :compojure/route] :as request} respond raise]
  (if-let [[raw-handler async?] (resolve-handler info path-info route request-method true)]
    (let [ks (if (contains? info request-method) [request-method] [])
          respond-coerced (fn [response]
                            (respond
                              (try (coerce-response response info request ks)
                                   (catch Throwable e (raise e)))))
          handler (middleware-chain info request-method raw-handler)]
      (try
        (as-> (coerce-request request info ks) $
              (if async?
                (handler $ #(compojure.response/send % $ respond-coerced raise) raise)
                (compojure.response/send (handler $) $ respond-coerced raise)))
        (catch Throwable e
          (raise e))))
    (respond nil)))

(defn- create-handler [info]
  (fn
    ([request]
     (handle-sync info request))
    ([request respond raise]
     (handle-async info request respond raise))))

(defn- merge-parameters-and-responses [info]
  (let [methods (select-keys info (:methods +mappings+))]
    (-> info
        (merge
          (p/for-map [[method method-info] methods
                      :let [responses (merge
                                        (:responses info)
                                        (:responses method-info))]]
            method (cond-> (->> method-info (rsc/deep-merge (select-keys info [:parameters])))
                           (seq responses) (assoc :responses responses)))))))

(defn- public-root-info [info]
  (-> (reduce dissoc info (:methods +mappings+))
      (dissoc :parameters :responses :coercion)))

;;
;; Public api
;;

(s/defschema Options
  {(s/optional-key :coercion) s/Any})

; TODO: validate input against ring-swagger schema, fail for missing handlers
; TODO: extract parameter schemas from handler fnks?
(defn resource
  "Creates a nested compojure-api Route from enchanced ring-swagger operations map.
  By default, applies both request- and response-coercion based on those definitions.

  Extra keys:

  - **:middleware**     Middleware in duct-format either at top-level or under methods.
                        Top-level mw are applied first if route matches, method-level
                        mw are applied next if method matches

  - **:coercion**       A function from request->type->coercion-matcher, used
                        in resource coercion for :body, :string and :response.
                        Setting value to `(constantly nil)` disables both request- &
                        response coercion. See tests and wiki for details.

  Enhancements to ring-swagger operations map:

  1) :parameters use ring request keys (query-params, path-params, ...) instead of
  swagger-params (query, path, ...). This keeps things simple as ring keys are used in
  the handler when destructuring the request.

  2) at resource root, one can add any ring-swagger operation definitions, which will be
  available for all operations, using the following rules:

    2.1) :parameters are deep-merged into operation :parameters
    2.2) :responses are merged into operation :responses (operation can fully override them)
    2.3) all others (:produces, :consumes, :summary,...) are deep-merged by compojure-api

  3) special keys `:handler` and/or `:async-handler` either under operations or at top-level.
  They should be 1-ary and 3-ary Ring handler functions, respectively, that are responsible
  for the actual request processing. Handler lookup order is the following:

    3.1) If called asynchronously, operations-level :async-handler
    3.2) Operations-level :handler
    3.3) If called asynchronously, top-level :async-handler
    3.4) Top-level :handler

  4) request-coercion is applied once, using deep-merged parameters for a given
  operation or resource-level if only resource-level handler is defined.

  5) response-coercion is applied once, using merged responses for a given
  operation or resource-level if only resource-level handler is defined.

  Note: Swagger operations are generated only from declared operations (:get, :post, ..),
  despite the top-level handler could process more operations.

  Example:

  (resource
    {:parameters {:query-params {:x Long}}
     :responses {500 {:schema {:reason s/Str}}}
     :get {:parameters {:query-params {:y Long}}
           :responses {200 {:schema {:total Long}}}
           :handler (fn [request]
                      (ok {:total (+ (-> request :query-params :x)
                                     (-> request :query-params :y))}))}
     :post {}
     :handler (constantly
                (internal-server-error {:reason \"not implemented\"}))})"
  [data]
  (let [data (merge-parameters-and-responses data)
        public-info (swaggerize (public-root-info data))
        info (merge {:public public-info} (select-keys data [:coercion]))
        childs (create-childs data)
        handler (create-handler data)]
    (routes/map->Route
      {:info info
       :childs childs
       :handler handler})))
