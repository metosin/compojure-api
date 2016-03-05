(ns compojure.api.resource
  (:require [compojure.api.routes :as routes]
            [compojure.api.coerce :as coerce]
            [schema.core :as s]))

(def ^:private +mappings+
  {:methods #{:get :head :patch :delete :options :post :put}
   :parameters {:query-params [:query :string true]
                :body-params [:body :body false]
                :form-params [:formData :string true]
                :header-params [:header :string true]
                :path-params [:path :string true]}})

; TODO: should swagger :body be mapped into :body or :body-params?
; TODO: validate input against ring-swagger schema, fail for missing handlers
(defn resource
  "Creates a nested compojure-api Route from an enchanced ring-swagger operations map.
  Applies both request- and response-coercion based on those definitions.

  Enchancements:

  1) :parameters use ring request keys (query-params, path-params, ...) instead of
  swagger-params (query, path, ...). This keeps things simple as ring keys are used in
  the handler when destructuring the request.

  2) special key `:handler` either under operations or at top-level. Value should be a
  ring-handler function, responsible for the actual request processing. Handler lookup
  order is the followin: operations-level, top-level, exception.

  3) at top-level, one can add any ring-swagger operation definitions, which will be
  shared for all operations. Top-level request-coercion will be applied before operation
  level coercion and top-level response-coercion will be applied after operation level
  coercion. All other definitions will be accumulated into operation info using normal
  compojure-api rules.

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
  [info]
  (let [swaggerize (fn [info]
                     (as-> info info
                           (reduce-kv
                             (fn [acc ring-key [swagger-key]]
                               (if-let [schema (get-in acc [:parameters ring-key])]
                                 (update acc :parameters #(-> % (dissoc ring-key) (assoc swagger-key schema)))
                                 acc))
                             info
                             (:parameters +mappings+))
                           (dissoc info :handler)))
        root-info (reduce dissoc info (:methods +mappings+))
        child-info (select-keys info (:methods +mappings+))
        childs (map
                 (fn [[method info]]
                   (routes/create "/" method (swaggerize info) nil nil))
                 child-info)
        coerce-request (fn [request ks]
                         (reduce-kv
                           (fn [request ring-key [_ type open?]]
                             (if-let [schema (get-in info (concat ks [:parameters ring-key]))]
                               (let [schema (if open? (assoc schema s/Keyword s/Any) schema)]
                                 (update request ring-key merge (coerce/coerce! schema ring-key type request)))
                               request))
                           request
                           (:parameters +mappings+)))
        coerce-response (fn [response request ks]
                          (coerce/coerce-response! request response (get-in info (concat ks [:responses]))))
        resolve-handler (fn [request-method]
                          (or
                            (get-in info [request-method :handler])
                            (get-in info [:handler])
                            (throw (ex-info (str "No handler defined for " request-method) info))))
        handler (fn [{:keys [request-method] :as request}]
                  (-> ((resolve-handler request-method)
                        (-> request
                            (coerce-request [])
                            (coerce-request [request-method])))
                      (coerce-response request [request-method])
                      (coerce-response request [])))]
    (routes/create nil nil (swaggerize root-info) childs handler)))
