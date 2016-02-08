(ns compojure.api.api
  (:require [compojure.api.core :as c]
            [compojure.api.swagger :as swagger]
            [compojure.api.middleware :as middleware]
            [compojure.api.routes :as routes]
            [compojure.api.common :as common]
            [ring.swagger.common :as rsc]
            [compojure.api.meta :as meta]
            [ring.swagger.middleware :as rsm]))

(def api-defaults
  (merge
    middleware/api-middleware-defaults
    {:api {:invalid-routes-fn routes/log-invalid-child-routes}
     :swagger nil}))

(defn
  ^{:doc (str
  "Returns a ring handler wrapped in compojure.api.middleware/api-middlware.
  Creates the route-table at run-time and injects that into the request via
  middlewares. Api and the mounted api-middleware can be configured by
  optional options map as the first parameter:

      (api
        {:formats [:json :edn]}
        (context \"/api\" []
          ...))

  ### direct api options:

  - **:api**                       All api options are under `:api`.
    - **:invalid-routes-fn**         A 2-arity function taking handler and a sequence of
                                     invalid routes (not satisfying compojure.api.route.Routing)
                                     setting value to nil ignores invalid routes completely.
                                     defaults to `compojure.api.routes/log-invalid-child-routes`
  - **:swagger**                   Options to configure the Swagger-routes. Defaults to nil.
                                   See `compojure.api.swagger/swagger-routes` for details.

  ### api-middleware options

  " (:doc (meta #'compojure.api.middleware/api-middleware)))}
  api
  [& body]
  (let [[options handlers] (common/extract-parameters body false)
        options (rsc/deep-merge api-defaults options)
        handler (apply c/routes (concat [(swagger/swagger-routes (:swagger options))] handlers))
        routes (routes/get-routes handler (:api options))
        paths (-> routes routes/ring-swagger-paths swagger/transform-operations)
        lookup (routes/route-lookup-table routes)
        swagger-data (get-in options [:swagger :data])
        api-handler (-> handler
                        (cond-> swagger-data (rsm/wrap-swagger-data swagger-data))
                        (middleware/api-middleware (dissoc options :api :swagger))
                        (middleware/wrap-options {:paths paths
                                                           :coercer (meta/memoized-coercer)
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
