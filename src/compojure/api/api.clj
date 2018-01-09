(ns compojure.api.api
  (:require [compojure.api.core :as c]
            [compojure.api.swagger :as swagger]
            [compojure.api.middleware :as mw]
            [compojure.api.request :as request]
            [compojure.api.routes :as routes]
            [compojure.api.common :as common]
            [compojure.api.request :as request]
            [compojure.api.coercion.schema :as schema-coercion]
            [ring.swagger.common :as rsc]
            [ring.swagger.middleware :as rsm]))

(def api-defaults
  (merge
    mw/api-middleware-defaults
    {:api {:invalid-routes-fn routes/log-invalid-child-routes
           :disable-api-middleware? false}
     :swagger {:ui nil, :spec nil}}))

(defn
  ^{:doc (str
  "Returns a ring handler wrapped in compojure.api.middleware/api-middlware.
  Creates the route-table at api creation time and injects that into the request via
  middlewares. Api and the mounted api-middleware can be configured by optional
  options map as the first parameter:

      (api
        {:formats [:json-kw :edn :transit-msgpack :transit-json]
         :exceptions {:handlers {:compojure.api.exception/default my-logging-handler}}
         :api {:invalid-routes-fn (constantly nil)}
         :swagger {:spec \"/swagger.json\"
                   :ui \"/api-docs\"
                   :data {:info {:version \"1.0.0\"
                                 :title \"My API\"
                                 :description \"the description\"}}}}
        (context \"/api\" []
          ...))

  ### direct api options:

  - **:api**                       All api options are under `:api`.
     - **:invalid-routes-fn**        A 2-arity function taking handler and a sequence of
                                     invalid routes (not satisfying compojure.api.route.Routing)
                                     setting value to nil ignores invalid routes completely.
                                     defaults to `compojure.api.routes/log-invalid-child-routes`
     - **:disable-api-middleware?**  boolean to disable the `api-middleware` from api.
  - **:swagger**                   Options to configure the Swagger-routes. Defaults to nil.
                                   See `compojure.api.swagger/swagger-routes` for details.

  ### api-middleware options

  See `compojure.api.middleware/api-middleware` for more available options.

  " (:doc (meta #'compojure.api.middleware/api-middleware)))}
  api
  [& body]
  (let [[options handlers] (common/extract-parameters body false)
        options (rsc/deep-merge api-defaults options)
        handler (apply c/routes (concat [(swagger/swagger-routes (:swagger options))] handlers))
        partial-api-route (routes/map->Route
                            {:childs [handler]
                             :info {:coercion (:coercion options)}})
        routes (routes/get-routes partial-api-route (:api options))
        paths (-> routes routes/ring-swagger-paths swagger/transform-operations)
        lookup (routes/route-lookup-table routes)
        swagger-data (get-in options [:swagger :data])
        enable-api-middleware? (not (get-in options [:api :disable-api-middleware?]))
        api-middleware-options (mw/api-middleware-options (dissoc options :api :swagger))
        api-handler (-> handler
                        (cond-> swagger-data (rsm/wrap-swagger-data swagger-data))
                        (cond-> enable-api-middleware? (mw/api-middleware
                                                         api-middleware-options))
                        (mw/wrap-inject-data
                          {::request/paths paths
                           ::request/lookup lookup}))]
    (assoc partial-api-route :handler api-handler)))
