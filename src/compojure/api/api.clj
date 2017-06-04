(ns compojure.api.api
  (:require [compojure.api.core :as c]
            [compojure.api.swagger :as swagger]
            [compojure.api.middleware :as middleware]
            [compojure.api.routes :as routes]
            [compojure.api.common :as common]
            [compojure.api.coercion.schema :as schema-coercion]
            [ring.swagger.common :as rsc]
            [ring.swagger.middleware :as rsm]))

(def api-defaults
  (merge
    middleware/api-middleware-defaults
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

  " (:doc (meta #'compojure.api.middleware/api-middleware)))}
  api
  [& body]
  (let [[options handlers] (common/extract-parameters body false)
        options (-> (rsc/deep-merge api-defaults options)
                    ;; [:formats :formats] can't be deep merged, else defaults always enables all the
                    ;; formats
                    (assoc-in [:formats :formats] (or (:formats (:formats options))
                                                      (:formats (:formats api-defaults)))))
        handler (apply c/routes (concat [(swagger/swagger-routes (:swagger options))] handlers))
        routes (routes/get-routes handler (:api options))
        paths (-> routes routes/ring-swagger-paths swagger/transform-operations)
        lookup (routes/route-lookup-table routes)
        swagger-data (get-in options [:swagger :data])
        enable-api-middleware? (not (get-in options [:api :disable-api-middleware?]))
        api-handler (cond-> handler
                            swagger-data (rsm/wrap-swagger-data swagger-data)
                            enable-api-middleware? (middleware/api-middleware
                                                     (dissoc options :api :swagger))
                            true (middleware/wrap-options
                                   {:paths paths
                                    :coercer (schema-coercion/memoized-coercer)
                                    :lookup lookup}))]
    (routes/map->Route
      {:childs [handler]
       :handler api-handler})))

(defmacro
  ^{:doc (str
  "Defines an api.

  API middleware options:

  " (:doc (meta #'compojure.api.middleware/api-middleware)))}
  defapi
  {:style/indent 1}
  [name & body]
  `(def ~name (api ~@body)))
