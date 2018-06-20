(ns compojure.api.coercion.core)

(defprotocol Coercion
  (get-name [this])
  (get-apidocs [this model data])
  (make-open [this model])
  (encode-error [this error])
  (coerce-request [this model value type format request])
  (accept-response? [this model])
  (coerce-response [this model value type format request]))

(defrecord CoercionError [])

(defmulti named-coercion identity :default ::default)

(defmethod named-coercion ::default [x]
  (let [message (if (= :spec x)
                  (str "spec-coercion is not enabled. "
                       "you most likely are missing the "
                       "required deps: org.clojure/clojure 1.9+ "
                       "and metosin/spec-tools.")
                  (str "cant find named-coercion for " x))]
    (throw (ex-info message {:name x}))))
