(ns compojure.api.coercion.spec
  (:require [compojure.api.middleware :as mw]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [compojure.api.coercion.core :as cc]
            [compojure.api.impl.logging :as log]))

(defmulti coerce-response? identity :default ::default)
(defmethod coerce-response? ::default [_] true)

(defrecord SpecCoercion [name options]
  cc/Coercion
  (get-name [_] name)

  (coerce-request [_ spec value type format request]
    (let [type-options (options type)]
      (if-let [conforming (or (get (get type-options :formats) format)
                              (get type-options :default))]
        (let [conformed (st/conform spec value conforming)]
          (if (s/invalid? conformed)
            (let [problems (st/explain-data spec value conforming)]
              (cc/map->CoercionError
                {:spec spec
                 :problems (::s/problems problems)}))
            conformed))
        value)))

  (coerce-response [this spec value type format request]
    (if (coerce-response? spec)
      (cc/coerce-request this spec value type format request)
      value)))

(def string-conforming st/string-conforming)
(def json-conforming st/json-conforming)

(def default-options
  {:body {:default nil
          :formats {"application/json" json-conforming
                    "application/msgpack" json-conforming
                    "application/x-yaml" json-conforming}}
   :string {:default string-conforming}
   :response {:default nil}})

(defn create-coercion [options]
  (->SpecCoercion :spec options))

(def default-coercion (create-coercion default-options))

(defmethod cc/named-coercion :spec [_] default-coercion)

(log/log! :info ":spec coercion enabled in compojure.api")
