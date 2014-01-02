(ns compojure.api.dsl
  (:require [compojure.core :refer :all]
            [compojure.api.common :refer :all]))

(defmacro GET* [path arg & body]
  (let [[parameters [body]] (extract-fn-parameters body)]
    `(with-meta (GET ~path ~arg ~@body) ~parameters)))
