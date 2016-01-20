(ns compojure.api.api
  (:require [compojure.api.core :as core]
            [compojure.api.swagger :as swagger]
            [compojure.api.middleware :as middleware]
            [compojure.api.routes :as routes]
            [compojure.api.common :as common]
            [clojure.tools.macro :as macro]
            [ring.swagger.common :as rsc]))

(def api-defaults
  (merge
    middleware/api-middleware-defaults
    {:api {:invalid-routes-fn routes/log-invalid-child-routes}}))

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

  ### api-middleware options

  " (:doc (meta #'compojure.api.middleware/api-middleware)))}
  api
  [& body]
  (let [[options handlers] (common/extract-parameters body)
        options (rsc/deep-merge api-defaults options)
        handler (apply core/routes handlers)
        routes (routes/get-routes handler (:api options))
        paths (-> routes routes/ring-swagger-paths swagger/transform-operations)
        lookup (routes/route-lookup-table routes)
        api-handler (-> handler
                        (middleware/api-middleware (dissoc options :api))
                        (middleware/wrap-options {:paths paths
                                                  :lookup lookup}))]
    (routes/create nil nil {} [handler] api-handler)))

(defmacro
  ^{:doc (str
  "Defines an api. The name may optionally be followed by a doc-string
  and metadata map.

  API middleware options:

  " (:doc (meta #'compojure.api.middleware/api-middleware)))}
  defapi
  [name & body]
  (let [[name body] (macro/name-with-attributes name body)]
    `(def ~name (api ~@body))))
