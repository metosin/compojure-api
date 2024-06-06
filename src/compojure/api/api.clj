(ns compojure.api.api
  (:require [compojure.api.core :as c]
            [compojure.api.swagger :as swagger]
            [compojure.api.middleware :as mw]
            [compojure.api.request :as request]
            [compojure.api.routes :as routes]
            [compojure.api.common :as common]
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
        {:exceptions {:handlers {:compojure.api.exception/default my-logging-handler}}
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
        _ (assert (not (contains? options :format))
                  (str "ERROR: Option [:format] is not used with 2.* version.\n"
                       "Compojure-api uses now Muuntaja insted of ring-middleware-format,\n"
                       "the new formatting options for it should be under [:formats]. See\n"
                       "[[api-middleware]] documentation for more details.\n"))
        _ (when (and (not (:formatter options))
                     (not (contains? options :formats))
                     (not (System/getProperty "compojure.api.middleware.global-default-formatter")))
            (throw (ex-info (str "ERROR: Please set `:formatter :muuntaja` in the options map of `api`.\n"
                                 "e.g., (api {:formatter :muuntaja} routes...)\n"
                                 "To prepare for backwards compatibility with compojure-api 1.x, the formatting library must be\n"
                                 "explicitly chosen if not configured by `:format` (ring-middleware-format) or \n"
                                 "`:formats` (muuntaja). Once 2.x is stable, the default will be `:formatter :ring-middleware-format`.\n"
                                 "To globally override the formatter, use -Dcompojure.api.middleware.global-default-formatter=:muuntaja")
                            {})))
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
        api-middleware-options (dissoc (mw/api-middleware-options (assoc (dissoc options :api :swagger) ::via-api true))
                                       ::mw/api-middleware-defaults)
        api-handler (-> handler
                        (cond-> swagger-data (rsm/wrap-swagger-data swagger-data))
                        (cond-> enable-api-middleware? (mw/api-middleware
                                                         api-middleware-options))
                        (mw/wrap-inject-data
                          {::request/paths paths
                           ::request/lookup lookup}))]
    (assoc partial-api-route :handler api-handler)))

(defmacro
  ^{:superseded-by "api" 
    :deprecated "2.0.0"
    :doc (str
  "Deprecated: please use (def name (api ...body..))
  
  Defines an api.

  API middleware options:

  " (:doc (meta #'compojure.api.middleware/api-middleware)))}
  defapi
  [name & body]
  {:style/indent 1}
  `(def ~name (api ~@body)))
