(ns compojure.api.pimp
  "var-pimp of of compojure.core/compile-route to support meta-data"
  (:require [compojure.core]
            [clout.core :as clout]
            [ring.swagger.common :as common]))

(ns compojure.core)
(defonce compile-route-original compile-route)
(defn compile-route-pimped
  "Compile a route in the form (method path & {optional meta-data} body) into a function."
  [method route bindings body]
  (let [[meta-data body] (ring.swagger.common/extract-parameters body)]
    (with-meta
      `(make-route
         ~method ~(prepare-route route)
         (fn [request#]
           (let-request [~bindings request#] ~@body)))
      meta-data)))
(def compile-route compile-route-pimped)
(ns compojure.api.pimp)
