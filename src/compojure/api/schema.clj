(ns compojure.api.schema
  (:require [schema.core :as s]
            [schema.macros :as sm]))

(defn required-keys [schema]
  (filter s/required-key? (keys schema)))

(defmulti json-type identity)
(defmethod json-type s/Int [_] {:type "integer"
                                :format "int64"})
(defmethod json-type s/String [_] {:type "string"})

(defn properties [schema]
  (into {}
    (for [[k v] schema]
      [k (json-type v)])))

(defn transform [schema-symbol]
  {:pre [(symbol? schema-symbol)]}
  (let [schema (eval schema-symbol)]
    {:id (str schema-symbol)
     :required (required-keys schema)
     :properties (properties schema)}))

