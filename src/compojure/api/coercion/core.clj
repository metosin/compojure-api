(ns compojure.api.coercion.core)

(defprotocol Coercion
  (get-name [this])
  (get-apidocs [this spec data])
  (encode-error [this error])
  (coerce-request [this model value type format request])
  (coerce-response [this model value type format request]))

(defrecord CoercionError [])

(defmulti named-coercion identity :default ::default)
(defmethod named-coercion ::default [x]
  (throw (ex-info (str "cant find named-coercion for " x) {:name x})))
