(ns compojure.api.schema
  (:require [schema.core :as s]
            [schema.macros :as sm]))

(defn required-keys [schema]
  (filter s/required-key? (keys schema)))

(defmulti json-type identity)
(defmethod json-type s/Int    [_] {:type "integer" :format "int64"})
(defmethod json-type s/String [_] {:type "string"})
(defmethod json-type sString  [_] {:type "string"})

(defn properties [schema]
  (into {}
    (for [[k v] schema
          :let [k (s/explicit-schema-key k)]]
      [k (merge (meta v) (json-type v))])))

(defn transform [schema-symbol]
  {:pre [(symbol? schema-symbol)]}
  (let [schema (eval schema-symbol)
        required (required-keys schema)
        target {:id (str schema-symbol)
                :properties (properties schema)}]
    (if (seq required)
      (assoc target :required required)
      target)))

(def sString
  "Clojure String"
  (s/pred string? 'string?))
