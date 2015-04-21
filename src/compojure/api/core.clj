(ns compojure.api.core
  (:require [clojure.tools.macro :refer [name-with-attributes]]
            [compojure.api.meta :refer [restructure]]
            [compojure.api.middleware :refer [api-middleware]]
            [compojure.api.routes :as routes]
            [compojure.core :refer :all]
            [potemkin :refer [import-vars]]
            [ring.swagger.common :refer [extract-parameters]]
            [backtick :refer [syntax-quote]]))

(defmacro defapi
  "Defines a ring handler wrapped in compojure.api.middleware/api-middlware.
   Defines a local var +routes+ which is used to store the route tables. Currently
   there can be only one defapi in one namespace. The mounted api-middleware can
   be configured by options map as the first parameter:

      (defapi app
        {:options 123}
        ...)

   ... see compojure.api.middleware/api-middleware for possible options."
  [name & body]
  (let [[opts body] (extract-parameters body)]
    `(defroutes ~name
       (api-middleware
         (routes/api-root ~@body)
         ~opts))))

(import-vars [compojure.api.meta middlewares])

(defmacro defroutes*
  "Define a Ring handler function from a sequence of routes. The name may
  optionally be followed by a doc-string and metadata map."
  [name & routes]
  (let [source (drop 2 &form)
        [name routes] (name-with-attributes name routes)]
    `(def ~name (with-meta (routes ~@routes) {:source (syntax-quote ~source)
                                              :inline true}))))

(defmacro GET*     [& args] (restructure #'GET     args))
(defmacro ANY*     [& args] (restructure #'ANY     args))
(defmacro HEAD*    [& args] (restructure #'HEAD    args))
(defmacro PATCH*   [& args] (restructure #'PATCH   args))
(defmacro DELETE*  [& args] (restructure #'DELETE  args))
(defmacro OPTIONS* [& args] (restructure #'OPTIONS args))
(defmacro POST*    [& args] (restructure #'POST    args))
(defmacro PUT*     [& args] (restructure #'PUT     args))
(defmacro context* [& args] (restructure #'context args {:body-wrap 'compojure.core/routes}))
