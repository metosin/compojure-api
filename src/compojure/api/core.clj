(ns compojure.api.core
  (:require [clojure.tools.macro :refer [name-with-attributes]]
            [compojure.api.meta :refer [restructure]]
            [compojure.api.middleware :refer [api-middleware]]
            [compojure.api.routes :as routes]
            [compojure.core :refer :all]
            [potemkin :refer [import-vars]]
            [ring.swagger.common :refer [extract-parameters]]
            [backtick :refer [syntax-quote]]))

(defmacro api
  "Returna a ring handler wrapped in compojure.api.middleware/api-middlware.
   Defines a local var +routes+ which is used to store the route tables. Currently
   there can be only one api in one namespace. The mounted api-middleware can
   be configured by options map as the first parameter:

      (api
        {:formats [:json :edn}
        (context* \"/api\" []
          ...))

   ... see compojure.api.middleware/api-middleware for possible options."
  [& body]
  (let [[opts body] (extract-parameters body)]
    `(api-middleware
       (routes/api-root ~@body)
       ~opts)))

(defmacro defapi
  "Defines a ring handler wrapped in compojure.api.core/api.
   Defines a local var +routes+ which is used to store the route tables. Currently
   there can be only one defapi in one namespace. The mounted api-middleware can
   be configured by options map as the first parameter:

      (defapi app
        {:formats [:json :edn}
        (context* \"/api\" []
          ...))

   ... see compojure.api.middleware/api-middleware for possible options."
  [name & body]
  `(def ~name
     (api ~@body
       (routes/api-root ~@body))))

(import-vars [compojure.api.meta middlewares])

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
        route-meta {:source `(syntax-quote ~source)
                    :inline true}]
    `(do
       (def ~route-sym (with-meta (routes ~@routes) ~route-meta))
       (alter-meta! (var ~route-sym) assoc :private true)
       (def ~name (var ~route-sym)))))

(defmacro GET*     [& args] (restructure #'GET     args))
(defmacro ANY*     [& args] (restructure #'ANY     args))
(defmacro HEAD*    [& args] (restructure #'HEAD    args))
(defmacro PATCH*   [& args] (restructure #'PATCH   args))
(defmacro DELETE*  [& args] (restructure #'DELETE  args))
(defmacro OPTIONS* [& args] (restructure #'OPTIONS args))
(defmacro POST*    [& args] (restructure #'POST    args))
(defmacro PUT*     [& args] (restructure #'PUT     args))
(defmacro context* [& args] (restructure #'context args {:body-wrap 'compojure.core/routes}))
