(ns compojure.api.api
  (:require [compojure.api.core :as core]
            [compojure.api.swagger :as swagger]
            [compojure.api.middleware :as middleware]
            [compojure.api.routes :as routes]
            [compojure.api.common :as common]
            [clojure.tools.macro :as macro]
            [ring.swagger.swagger2 :as swagger2]))

(defn
  ^{:doc (str
  "Returns a ring handler wrapped in compojure.api.middleware/api-middlware.
  Creates the route-table at run-time and passes that into the request via
  ring-swagger middlewares. The mounted api-middleware can be configured by
  optional options map as the first parameter:

      (api
        {:formats [:json :edn]}
        (context \"/api\" []
          ...))

  API middleware options:

  " (:doc (meta #'compojure.api.middleware/api-middleware)))}
  api
  [& body]
  (let [[options handlers] (common/extract-parameters body)
        handler (apply core/routes handlers)
        routes (routes/get-routes handler)
        paths (-> routes routes/ring-swagger-paths swagger/transform-operations)
        lookup (routes/route-lookup-table handler)
        api-handler (-> handler
                        (middleware/api-middleware options)
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
