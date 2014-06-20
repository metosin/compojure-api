(ns compojure.api.core
  (:require [potemkin :refer [import-vars]]
            [compojure.core :refer :all]
            [compojure.api.middleware :refer [api-middleware]]
            [compojure.api.meta :refer [restructure]]
            [clojure.tools.macro :refer [name-with-attributes]]))

(def +routes-sym+ '+routes+)

(defmacro with-routes [& body]
  `(do (def ~+routes-sym+ (atom {}))
       (routes ~@body)))

(defmacro get-routes []
  `(if-let [data# ~+routes-sym+]
     @data#
     (throw (IllegalStateException. "no +routes+ bound in this namespace."))))

(defmacro defapi [name & body]
  `(defroutes ~name
     (api-middleware
       (with-routes ~@body))))

(import-vars [compojure.api.meta middlewares])

(defmacro defroutes*
  "Define a Ring handler function from a sequence of routes. The name may
  optionally be followed by a doc-string and metadata map."
  [name & routes]
  (let [source (drop 2 &form)
        [name routes] (name-with-attributes name routes)]
    `(def ~name (with-meta (routes ~@routes) {:source '~source
                                              :inline true}))))

(defmacro GET*     [& args] (restructure #'GET     args))
(defmacro ANY*     [& args] (restructure #'ANY     args))
(defmacro HEAD*    [& args] (restructure #'HEAD    args))
(defmacro PATCH*   [& args] (restructure #'PATCH   args))
(defmacro DELETE*  [& args] (restructure #'DELETE  args))
(defmacro OPTIONS* [& args] (restructure #'OPTIONS args))
(defmacro POST*    [& args] (restructure #'POST    args))
(defmacro PUT*     [& args] (restructure #'PUT     args))
