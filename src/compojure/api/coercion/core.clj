(ns compojure.api.coercion.core
  (:require [compojure.api.middleware :as mw]
            [compojure.api.exception :as ex]
            [clojure.walk :as walk]))

(defprotocol Coercion
  (get-name [this])
  (coerce-request [this model value type format request])
  (coerce-response [this model value type format request]))

(defrecord CoercionError [])

(defmulti named-coercion identity :default ::default)
(defmethod named-coercion ::default [x]
  (throw (ex-info (str "cant find named-coercion for " x) {:name x})))
