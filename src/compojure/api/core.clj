(ns compojure.api.core
  (:require [clojure.tools.macro :refer [name-with-attributes]]
            [compojure.api.meta :as meta]
            [compojure.api.middleware :as mw]
            [compojure.api.routes :as routes]
            [compojure.core :refer :all]
            [potemkin :refer [import-vars]]
            [ring.swagger.common :refer [extract-parameters]]
            [clojure.walk :as walk]
            backtick))

(defn api-middleware-with-routes
  "Returns a compojure.api.middleware/api-middlware wrapped handler,
  which publishes the swagger route-data via ring-swagger and route
  lookup table via wrap-options. Returned handler retains the original
  meta-data."
  [handler options]
  (let [meta (-> handler meta (assoc :options options))]
    (-> handler
        (mw/api-middleware options)
        (mw/wrap-options (select-keys meta [:routes :lookup]))
        (with-meta meta))))

(defmacro api
  "Returns a ring handler wrapped in compojure.api.middleware/api-middlware.
   Creates the route-table at compile-time and passes that into the request via
   ring-swagger middlewares. The mounted api-middleware can be configured by
   optional options map as the first parameter:

       (api
         {:formats [:json :edn]}
         (context* \"/api\" []
           ...))

   ... see compojure.api.middleware/api-middleware for possible options."
  [& body]
  (let [[opts body] (extract-parameters body)]
    `(api-middleware-with-routes
       (routes/api-root ~@body)
       ~opts)))

(defmacro defapi
  "Returns a ring handler wrapped in a `api`. Behind the scenes,
   creates the route-table at compile-time and passes that into the request via
   ring-swagger middlewares. The mounted api-middleware can be configured by
   optional options map as the first parameter:

       (defapi app
         {:formats [:json :edn]}
         (context* \"/api\" []
           ...))

   ... see compojure.api.middleware/api-middleware for possible options."
  [name & body]
  `(def ~name
     (api ~@body)))

(import-vars [compojure.api.meta routes* middlewares])

(defmacro defroutes*
  "Define a Ring handler function from a sequence of routes. The name may
  optionally be followed by a doc-string and metadata map. Generates an
  extra private Var with `_` + name to the given namespace holding the
  actual routes, caller doesn't have to care about this. Accessing defroutes*
  over Var add tiny run-time penalty, but allows massive better development
  speed as the defroutes* can be compiled seperately."
  [name & routes]
  (let [source (drop 2 &form)
        [name routes] (name-with-attributes name routes)
        route-sym (symbol (str "_" name))
        source `(backtick/syntax-quote ~source)
        ;; #125, muddle local bindings with defroutes name
        source (walk/prewalk-replace {name (gensym name)} source)
        route-meta {:source source
                    :inline true}]
    `(do
       (def ~route-sym (with-meta (routes ~@routes) ~route-meta))
       (alter-meta! (var ~route-sym) assoc :private true)
       (def ~name (var ~route-sym)))))

(defmacro GET*     [& args] (meta/restructure #'GET     args nil))
(defmacro ANY*     [& args] (meta/restructure #'ANY     args nil))
(defmacro HEAD*    [& args] (meta/restructure #'HEAD    args nil))
(defmacro PATCH*   [& args] (meta/restructure #'PATCH   args nil))
(defmacro DELETE*  [& args] (meta/restructure #'DELETE  args nil))
(defmacro OPTIONS* [& args] (meta/restructure #'OPTIONS args nil))
(defmacro POST*    [& args] (meta/restructure #'POST    args nil))
(defmacro PUT*     [& args] (meta/restructure #'PUT     args nil))
(defmacro context* [& args] (meta/restructure #'context args {:routes? true}))
