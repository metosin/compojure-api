(ns compojure.api.schema
  (:require [schema.core :as s]
            [schema.macros :as sm]))

(defn required-keys [schema]
  (filter s/required-key? (keys schema)))

(def sString
  "Clojure String Predicate enabling setting metadata to it."
  (s/pred string? 'string?))

(defmulti json-type  identity)
(defmethod json-type s/Int    [_] {:type "integer" :format "int64"})
(defmethod json-type s/String [_] {:type "string"})
(defmethod json-type sString  [_] {:type "string"})
(defmethod json-type :default [e]
  {:type "string"
   :enum (seq (:vs e))})

(defn type-of [v]
  (if (sequential? v)
    {:type "array"
     :items (json-type (first v))}
    (json-type v)))

(defn properties [schema]
  (into {}
    (for [[k v] schema
          :let [k (s/explicit-schema-key k)]]
      [k (merge (meta v) (type-of v))])))

(defn transform [schema-symbol]
  {:pre [(symbol? schema-symbol)]}
  (let [schema (eval schema-symbol)
        required (required-keys schema)
        target {:id (str schema-symbol)
                :properties (properties schema)}]
    (if (seq required)
      (assoc target :required required)
      target)))

(defn field [pred metadata]
  (let [pred (if (= s/String pred) sString pred)]
    (with-meta pred metadata)))
