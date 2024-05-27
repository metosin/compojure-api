(ns compojure.api.coercion.register-schema
  (:require [compojure.api.coercion.core :as cc]))

(defmethod cc/named-coercion :schema [_] 
  (deref
    (or (resolve 'compojure.api.coercion.schema/default-coercion)
        (do (require 'compojure.api.coercion.schema)
            (resolve 'compojure.api.coercion.schema/default-coercion)))))
