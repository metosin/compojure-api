(ns compojure.api.schema
  (:require [schema.core :as s]
            [schema.macros :as sm]))

(def sString
  "Clojure String Predicate enabling setting metadata to it."
  (s/pred string? 'string?))

(defmulti json-type  identity)
(defmethod json-type s/Int    [_] {:type "integer" :format "int64"})
(defmethod json-type s/String [_] {:type "string"})
(defmethod json-type sString  [_] {:type "string"})
(defmethod json-type :default [e]
  (cond
    (= (class e) schema.core.EnumSchema) {:type "string"
                                          :enum (seq (:vs e))}
    (map? e) {:$ref (-> e meta :model str)}
    :else (throw (IllegalArgumentException. (str e)))))

(defn type-of [v]
  (if (sequential? v)
    {:type "array"
     :items (json-type (first v))}
    (json-type v)))

(defn properties [schema]
  (into {}
    (for [[k v] schema
          :let [k (s/explicit-schema-key k)]]
      [k (merge (dissoc (meta v) :model) (type-of v))])))

(defn required-keys [schema]
  (filter s/required-key? (keys schema)))

(defn eval-symbol-or-var [x]
  (cond
    (symbol? x) (eval x)
    (var? x) (var-get x)
    :else (throw (IllegalArgumentException. (str "not a symbol or var: " x)))))

;;
;; public Api
;;

(defn transform [schema-symbol]
  (let [schema (eval-symbol-or-var schema-symbol)
        required (required-keys schema)
        target {:id (str schema-symbol)
                :properties (properties schema)}]
    (if (seq required)
      (assoc target :required required)
      target)))

(defn collect-models [x]
  (let [model  (-> x meta :model)
        values (if (map? x) (vals x) (seq x))
        cols   (filter coll? values)
        models (->> cols (map meta) (keep :model))
        models (if model (conj models model) model)]
    (reduce concat models (map collect-models cols))))

(defn transform-models [& schema-symbols]
  (->> schema-symbols
    (map eval-symbol-or-var)
    (mapcat collect-models)
    set
    (map transform)
    (map (juxt (comp keyword :id) identity))
    (into {})))

;;
;; the DSL
;;

(defn field [pred metadata]
  (let [pred (if (= s/String pred) sString pred)
        old-meta (meta pred)]
    (with-meta pred (merge old-meta metadata))))

(defn required [k] (s/required-key k))
(defn optional [k] (s/optional-key k))

(defmacro defmodel [name form]
  `(def ~name (with-meta ~form {:model '~(resolve name)})))
