(ns compojure-api-kondo-hooks.plumbing.fnk.impl
  (:require
   [clojure.set :as set]
   [schema.core :as-alias s]
   [compojure-api-kondo-hooks.schema.macros :as schema-macros]))

;;;;; Helpers

(defn name-sym
  "Returns symbol of x's name.
   Converts a keyword/string to symbol, or removes namespace (if any) of symbol"
  [x]
  (with-meta (symbol (name x)) (meta x)))

;;; Parsing new fnk binding style

(declare letk-input-schema-and-body-form)

(defn- process-schematized-map
  "Take an optional binding map like {a 2} or {a :- Number 2} and convert the schema
   information to canonical metadata, if present."
  [env binding]
  (case (count binding)
    1 (let [[sym v] (first binding)]
        {sym v})

    2 (let [[[[sym _]] [[schema v]]] ((juxt filter remove) #(= (val %) :-) binding)]
        {sym v})))

;; TODO: unify this with positional version.
(defn letk-arg-bind-sym-and-body-form
  "Given a single element of a single letk binding form and a current body form, return
   a map {:schema-entry :body-form} where schema-entry is a tuple
   [bound-key schema external-schema?], and body-form wraps body with destructuring
   for this binding as necessary."
  [env map-sym binding key-path body-form]
  (cond (symbol? binding)
        {:schema-entry []
         :body-form `(let [~(name-sym binding) (get ~map-sym ~(keyword binding) ~key-path)]
                       ~body-form)}

        (map? binding)
        (let [schema-fixed-binding (process-schematized-map env binding)
              [bound-sym opt-val-expr] (first schema-fixed-binding)
              bound-key (keyword bound-sym)]
          {:schema-entry []
           :body-form `(let [~(name-sym bound-sym) (get ~map-sym ~bound-key ~opt-val-expr)]
                         ~body-form)})

        (vector? binding)
        (let [[bound-key & more] binding
              {inner-input-schema :input-schema
               inner-external-input-schema :external-input-schema
               inner-map-sym :map-sym
               inner-body-form :body-form} (letk-input-schema-and-body-form
                                            env
                                            (with-meta (vec more) (meta binding))
                                            (conj key-path bound-key)
                                            body-form)]
          {:schema-entry []
           :body-form `(let [~inner-map-sym (get ~map-sym ~bound-key ~key-path)]
                         ~inner-body-form)})

        :else (throw (ex-info (format "bad binding: %s" binding) {}))))

(defn- extract-special-args
  "Extract trailing & sym and :as sym, possibly with schema metadata. Returns
  [more-bindings special-args-map] where special-args-map is a map from each
  special symbol found to the symbol that was found."
  [env special-arg-signifier-set binding-form]
  {:pre [(set? special-arg-signifier-set)]}
  (let [[more-bindings special-bindings] (split-with (complement special-arg-signifier-set) binding-form)]
    (loop [special-args-map {}
           special-arg-set special-arg-signifier-set
           [arg-signifier & other-bindings :as special-bindings] special-bindings]
      (if-not (seq special-bindings)
        [more-bindings special-args-map]
        (do
          (let [[sym remaining-bindings] (schema-macros/extract-arrow-schematized-element env other-bindings)]
            (recur (assoc special-args-map arg-signifier sym)
                   (disj special-arg-set arg-signifier)
                   remaining-bindings)))))))

(defn letk-input-schema-and-body-form
  "Given a single letk binding form, value form, key path, and body
   form, return a map {:input-schema :external-input-schema :map-sym :body-form}
   where input-schema is the schema imposed by binding-form, external-input-schema
   is like input-schema but includes user overrides for binding vectors,
   map-sym is the symbol which it expects the bound value to be bound to,
   and body-form wraps body in the bindings from binding-form from map-sym."
  [env binding-form key-path body-form]
  (let [[bindings {more-sym '& as-sym :as}] (extract-special-args env #{'& :as} binding-form)
        as-sym (or as-sym (gensym "map"))
        [input-schema-elts
         external-input-schema-elts
         bound-body-form] (reduce
                           (fn [[input-schema-elts external-input-schema-elts cur-body] binding]
                             (let [{:keys [schema-entry body-form]}
                                   (letk-arg-bind-sym-and-body-form
                                    env as-sym binding key-path cur-body)
                                   [bound-key input-schema external-input-schema] schema-entry]
                               [(conj input-schema-elts [bound-key input-schema])
                                (conj external-input-schema-elts
                                      [bound-key (or external-input-schema input-schema)])
                                body-form]))
                           [[] [] body-form]
                           (reverse
                            (schema-macros/process-arrow-schematized-args
                             env bindings)))
        explicit-schema-keys []
        final-body-form (if more-sym
                          `(let [~more-sym (dissoc ~as-sym ~@explicit-schema-keys)]
                             ~bound-body-form)
                          bound-body-form)]
    {:map-sym as-sym
     :body-form final-body-form}))
