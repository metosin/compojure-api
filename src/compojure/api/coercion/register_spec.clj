(ns compojure.api.coercion.register-spec
  (:require [compojure.api.coercion.core :as cc]))

(defmethod cc/named-coercion :spec [_]
  (deref
    (or (resolve 'compojure.api.coercion.spec/default-coercion)
        (do (require 'compojure.api.coercion.spec)
            (resolve 'compojure.api.coercion.spec/default-coercion)))))
