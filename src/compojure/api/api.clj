(ns compojure.api.api
  (:require [compojure.api.core :as core]
            [compojure.api.swagger :as swagger]
            [compojure.api.middleware :as middleware]
            [compojure.api.routes :as routes]
            [compojure.api.common :as common]
            [clojure.tools.macro :as macro]
            [ring.swagger.swagger2 :as swagger2]))

(defn api
  "Returns a ring handler wrapped in compojure.api.middleware/api-middlware.
   Creates the route-table at run-time and passes that into the request via
   ring-swagger middlewares. The mounted api-middleware can be configured by
   optional options map as the first parameter:

       (api
         {:formats [:json :edn]}
         (context \"/api\" []
           ...))

   ... see compojure.api.middleware/api-middleware for possible options."
  [& body]
  (let [[options handlers] (common/extract-parameters body)
        [options handlers] [(dissoc options :swagger)
                            (if-let [swagger-options (:swagger options)]
                              (into handlers [])
                              handlers
                              )]

        handler (apply core/routes handlers)
        swagger (routes/ring-swagger-paths handler)
        lookup (routes/route-lookup-table swagger)
        swagger (->> swagger
                     (swagger2/transform-operations routes/non-nil-routes)
                     (swagger2/transform-operations routes/strip-no-doc-endpoints))
        api-handler (-> handler
                        (middleware/api-middleware options)
                        ;; TODO: wrap just the handler
                        (middleware/wrap-options {:routes swagger
                                          :lookup lookup}))]
    (routes/create nil nil {} [handler] api-handler)))

(defmacro defapi
  "Define an api. The name may optionally be followed by a doc-string
  and metadata map."
  [name api]
  (let [[name api] (macro/name-with-attributes name api)]
    `(def ~name (api ~api))))
