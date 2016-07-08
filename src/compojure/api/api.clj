(ns compojure.api.api
  (:require [compojure.api.core :as c]
            [compojure.api.swagger :as swagger]
            [compojure.api.middleware :as middleware]
            [compojure.api.routes :as routes]
            [compojure.api.common :as common]
            [compojure.api.coerce :as coerce]
            [ring.swagger.common :as rsc]
            [ring.swagger.middleware :as rsm]))

(def api-defaults
  (merge
    middleware/api-middleware-defaults
    {:api {:invalid-routes-fn routes/log-invalid-child-routes}
     :swagger {:ui nil, :spec nil}}))

(defn
  ^{:doc (str
  "Returns a ring handler wrapped in compojure.api.middleware/api-middlware.
  Creates the route-table at api creation time and injects that into the request via
  middlewares. Api and the mounted api-middleware can be configured by optional
  options map as the first parameter:

      (api
        {:formats [:json-kw :edn :transit-msgpack :transit-json]
         :exceptions {:compojure.api.exception/default my-logging-handler}
         :invalid-routes-fn (constantly nil)
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
  - **:swagger**                   Options to configure the Swagger-routes. Defaults to nil.
                                   See `compojure.api.swagger/swagger-routes` for details.

  - **:handler-wont-404**          Normally, api checks URIs for swagger-ui routes before
                                   delegating to your supplied handler. This is necessary because
                                   otherwise, swagger-ui requests would pass through your handler,
                                   finding no match, and your handler would return a 404, meaning
                                   that compojure would never check the swagger-ui routes.
                                   You can slightly improve performance of your api routes if you
                                   promise that your routes will not return a 404 for paths you
                                   don't recognize, for example in a setup like:

                                   (-> (api {:handler-wont-404 true} (GET ...))
                                       (routes (constantly {:status 404 :body \"Not found\"})))

                                   If you do, api will check your routes for matches before looking
                                   for a matching swagger-ui route.

  ### api-middleware options

  " (:doc (meta #'compojure.api.middleware/api-middleware)))}
  api
  [& body]
  (let [[options handlers] (common/extract-parameters body false)
        options (rsc/deep-merge api-defaults options)
        handler (apply c/routes
                       (if (:handler-wont-404 options)
                         (concat handlers [(swagger/swagger-routes (:swagger options))])
                         (concat [(swagger/swagger-routes (:swagger options))] handlers)))
        routes (routes/get-routes handler (:api options))
        paths (-> routes routes/ring-swagger-paths swagger/transform-operations)
        lookup (routes/route-lookup-table routes)
        swagger-data (get-in options [:swagger :data])
        api-handler (-> handler
                        (cond-> swagger-data (rsm/wrap-swagger-data swagger-data))
                        (middleware/api-middleware (dissoc options :api :swagger))
                        (middleware/wrap-options {:paths paths
                                                  :coercer (coerce/memoized-coercer)
                                                  :lookup lookup}))]
    (routes/create nil nil {} [handler] api-handler)))

(defmacro
  ^{:doc (str
  "Defines an api.

  API middleware options:

  " (:doc (meta #'compojure.api.middleware/api-middleware)))}
  defapi
  [name & body]
  {:style/indent 1}
  `(def ~name (api ~@body)))
