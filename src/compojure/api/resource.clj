(ns compojure.api.resource
  (:require [compojure.api.routes :as routes]
            [compojure.api.meta :as meta]
            [schema.core :as s]))

(def ^:private +mappings+
  {:methods #{:get :head :patch :delete :options :post :put}
   :parameters {:query [:query-params :string true]
                :body [:body-params :body false]
                :formData [:form-params :string true]
                :header [:header-params :string true]
                :path [:path-params :string true]}})

; TODO: tests
; TODO: validate input against ring-swagger schema, fail for missing handlers
(defn resource
  "Created a nested compojure-api Route from an enchanced ring-swagger operations map.
  Will do both request- and response-coercion based on those definitions.

  Enchancements:

  1) special key `:handler` either under operations or at top-level. Value should be a
  ring-handler function, responsible for the actual request processing. Handler lookup
  order is the followin: operations-level, top-level, exception.

  2) at top-level, one can add any ring-swagger operation definitions, which will be
  shared for all operations. Top-level request-coercion will be applied before operation
  level coercion and top-level response-coercion will be applied after operation level
  coercion. All other definitions will be accumulated into operation info using normal
  compojure-api rules."
  [info]
  (let [root-info (dissoc (reduce dissoc info (:methods +mappings+)) :handler)
        child-info (dissoc (select-keys info (:methods +mappings+)) :handler)
        childs (map (fn [[method info]] (routes/create "/" method info nil nil)) child-info)
        coerce-request (fn [request ks]
                         (reduce-kv
                           (fn [request k [v type open?]]
                             (if-let [schema (get-in info (concat ks [:parameters k]))]
                               (let [schema (if open? (assoc schema s/Keyword s/Any) schema)]
                                 (update request v merge (meta/coerce! schema v type request)))
                               request))
                           request
                           (:parameters +mappings+)))
        coerce-response (fn [response request ks]
                          (meta/coerce-response! request response (get-in info (concat ks [:responses]))))
        resolve-handler (fn [request-method]
                          (or
                            (get-in info [:handler])
                            (get-in info [request-method :handler])
                            (throw (ex-info (str "No handler defined for" request-method) info))))
        handler (fn [{:keys [request-method] :as request}]
                  (-> ((resolve-handler request-method)
                        (-> request
                            (coerce-request [])
                            (coerce-request [request-method])))
                      (coerce-response request [request-method])
                      (coerce-response request [])))]
    (routes/create nil nil root-info childs handler)))


(comment
  (resource
    {:description "shared description, can be overridden"
     :get {:parameters {:query {:x Long, :y Long}}
           :description "overridden description"
           :summary "get endpoint"
           :responses {200 {:schema Pizza
                            :description "pizza"}
                       404 {:description "Ohnoes."}}}
     :post {:parameters {:body Pizza}
            :responses {200 {:schema Pizza
                             :description "pizza"}}}
     :head {:summary "head"}
     :patch {:summary "patch"}
     :delete {:summary "delete"}
     :options {:summary "options"}
     :put {:summary "put"}
     :handler (fn [request]
                (ok {:liberate "me!"}))}))

