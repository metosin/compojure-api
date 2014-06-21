(ns compojure.api.routes
  (:require [compojure.core :refer :all]))

(def +routes-sym+ '+routes+)

(defmacro with-routes [& body]
  `(do (def ~+routes-sym+ (atom {}))
       (routes ~@body)))

(defmacro get-routes []
  `(if-let [data# ~+routes-sym+]
     @data#
     (throw (IllegalStateException. "no +routes+ bound in this namespace."))))
