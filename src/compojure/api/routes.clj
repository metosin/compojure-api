(ns compojure.api.routes
  (:require [compojure.core :refer :all]))

(defmulti collect-routes identity)

(def +routes+ '+routes+)

(defmacro with-routes [& body]
  (let [[details body] (collect-routes body)]
    `(do
       (def ~+routes+ {"default" '~details})
       (routes ~@body))))

(defmacro get-routes []
  `(try
     (eval +routes+)
     (catch RuntimeException e#
       (throw
         (IllegalStateException.
           (str
             "Coudn't find a +compojure-api-routes+ var defined in this ns. "
             "You should wrap your api in a compojure.api.routes.root -macro "
             "to get your lovely routes collected."))))))
