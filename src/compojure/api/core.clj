(ns compojure.api.core
  (:require [compojure.api.meta :as meta]
            [compojure.api.middleware :as mw]
            [compojure.api.common :refer [extract-parameters]]
            [compojure.api.routes :as routes]
            [clojure.tools.macro :as macro]
            [ring.swagger.swagger2 :as rss]))

(defn- ring-handler [handlers]
  (condp = (count handlers)
    0 (constantly nil)
    1 (first handlers)
    (fn [request] (some #(% request) handlers))))

(defn routes
  "Create a Ring handler by combining several handlers into one."
  [& handlers]
  (let [handlers (keep identity handlers)]
    (routes/create "" nil {} (vec handlers) (ring-handler handlers))))

(defmacro defroutes
  "Define a Ring handler function from a sequence of routes.
  The name may optionally be followed by a doc-string and metadata map."
  [name & routes]
  (let [[name routes] (macro/name-with-attributes name routes)]
    `(def ~name (routes ~@routes))))

(defmacro let-routes
  "Takes a vector of bindings and a body of routes. Equivalent to:
  (let [...] (routes ...))"
  [bindings & body]
  `(let ~bindings (routes ~@body)))

(defn undocumented [& handlers]
  (let [handlers (keep identity handlers)]
    (routes/create "" nil {} nil (ring-handler handlers))))

(defmacro middlewares
  "Wraps routes with given middlewares using thread-first macro."
  [middlewares & body]
  (let [middlewares (reverse middlewares)
        routes? (> (count body) 1)]
    `(let [body# ~(if routes? `(routes ~@body) (first body))]
       (routes/create "" nil {} [body#] (-> body# ~@middlewares)))))

(defmacro context [& args] (meta/restructure nil      args {:routes 'routes}))

(defmacro GET     [& args] (meta/restructure :get     args nil))
(defmacro ANY     [& args] (meta/restructure nil      args nil))
(defmacro HEAD    [& args] (meta/restructure :head    args nil))
(defmacro PATCH   [& args] (meta/restructure :patch   args nil))
(defmacro DELETE  [& args] (meta/restructure :delete  args nil))
(defmacro OPTIONS [& args] (meta/restructure :options args nil))
(defmacro POST    [& args] (meta/restructure :post    args nil))
(defmacro PUT     [& args] (meta/restructure :put     args nil))

;;
;; api
;;

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
  (let [[options handlers] (extract-parameters body)
        handler (apply routes handlers)
        swagger (routes/ring-swagger-paths handler)
        lookup (routes/route-lookup-table swagger)
        swagger (->> swagger
                     (rss/transform-operations routes/non-nil-routes)
                     (rss/transform-operations routes/strip-no-doc-endpoints))
        api-handler (-> handler
                        (mw/api-middleware options)
                        ;; TODO: wrap just the handler
                        (mw/wrap-options {:routes swagger
                                          :lookup lookup}))]
    (routes/create nil nil {} [handler] api-handler)))

(defmacro defapi
  "Define an api. The name may optionally be followed by a doc-string
  and metadata map."
  [name api]
  (let [[name api] (macro/name-with-attributes name api)]
    `(def ~name (api ~api))))
