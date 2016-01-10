(ns compojure.api.core
  (:require [clojure.tools.macro :refer [name-with-attributes]]
            [compojure.api.meta :as meta]
            [compojure.api.middleware :as mw]
            [compojure.api.common :refer [extract-parameters]]
            [compojure.api.routes :as routes]
            [compojure.core :refer :all]
            [potemkin :refer [import-vars]]
            [clojure.tools.macro :as macro]
            [compojure.api.routing :as r]
            [ring.swagger.swagger2 :as rss]))

(import-vars [compojure.api.meta routes* undocumented* middlewares])

(defn api
  "Returns a ring handler wrapped in compojure.api.middleware/api-middlware.
   Creates the route-table at run-time and passes that into the request via
   ring-swagger middlewares. The mounted api-middleware can be configured by
   optional options map as the first parameter:

       (api
         {:formats [:json :edn]}
         (context* \"/api\" []
           ...))

   ... see compojure.api.middleware/api-middleware for possible options."
  [& body]
  (let [[options handlers] (extract-parameters body)
        handler (apply routes* handlers)
        swagger (-> handler r/get-routes routes/->ring-swagger)
        lookup (routes/route-lookup-table swagger)
        swagger (->> swagger
                     (rss/transform-operations routes/non-nil-routes)
                     (rss/transform-operations routes/strip-no-doc-endpoints))
        api-handler (-> handler
                        (mw/api-middleware options)
                        (mw/wrap-options {:routes swagger
                                          :lookup lookup}))]
    (r/route nil :any {} [handler] api-handler)))

(defmacro defapi [name & body]
  `(def ~name (api ~@body)))

(defmacro defroutes*
  "Define a Ring handler function from a sequence of routes. The name may
  optionally be followed by a doc-string and metadata map."
  [name & routes]
  (let [[name routes] (macro/name-with-attributes name routes)]
    `(def ~name (routes* ~@routes))))

(defmacro let-routes*
  "Takes a vector of bindings and a body of routes. Equivalent to:
  (let [...] (routes* ...))"
  [bindings & body]
  `(let ~bindings (routes* ~@body)))

(defmacro GET* [& args] (meta/restructure #'GET args nil))
(defmacro ANY* [& args] (meta/restructure #'ANY args nil))
(defmacro HEAD* [& args] (meta/restructure #'HEAD args nil))
(defmacro PATCH* [& args] (meta/restructure #'PATCH args nil))
(defmacro DELETE* [& args] (meta/restructure #'DELETE args nil))
(defmacro OPTIONS* [& args] (meta/restructure #'OPTIONS args nil))
(defmacro POST* [& args] (meta/restructure #'POST args nil))
(defmacro PUT* [& args] (meta/restructure #'PUT args nil))
(defmacro context* [& args] (meta/restructure #'context args {:routes? true}))
