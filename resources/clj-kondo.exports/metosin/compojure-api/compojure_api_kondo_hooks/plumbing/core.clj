(ns compojure-api-kondo-hooks.plumbing.core
  "Utility belt for Clojure in the wild"
  (:refer-clojure :exclude [update])
  (:require
   [compojure-api-kondo-hooks.schema.macros :as schema-macros]
   [compojure-api-kondo-hooks.plumbing.fnk.impl :as fnk-impl]))

(defmacro letk
  "Keyword let.  Accepts an interleaved sequence of binding forms and map forms like:
   (letk [[a {b 2} [:f g h] c d {e 4} :as m & more] a-map ...] & body)
   a, c, d, and f are required keywords, and letk will barf if not in a-map.
   b and e are optional, and will be bound to default values if not present.
   g and h are required keys in the map found under :f.
   m will be bound to the entire map (a-map).
   more will be bound to all the unbound keys (ie (dissoc a-map :a :b :c :d :e)).
   :as and & are both optional, but must be at the end in the specified order if present.
   The same symbol cannot be bound multiple times within the same destructing level.

   Optional values can reference symbols bound earlier within the same binding, i.e.,
   (= [2 2] (let [a 1] (letk [[a {b a}] {:a 2}] [a b]))) but
   (= [2 1] (let [a 1] (letk [[{b a} a] {:a 2}] [a b])))

   If present, :as and :& symbols are bound before other symbols within the binding.

   Namespaced keys are supported by specifying fully-qualified key in binding form. The bound
   symbol uses the _name_ portion of the namespaced key, i.e,
   (= 1 (letk [[a/b] {:a/b 1}] b)).

   Map destructuring bindings can be mixed with ordinary symbol bindings."
  [bindings & body]
  (reduce
   (fn [cur-body-form [bind-form value-form]]
     (if (symbol? bind-form)
       `(let [~bind-form ~value-form] ~cur-body-form)
       (let [{:keys [map-sym body-form]} (fnk-impl/letk-input-schema-and-body-form
                                          &env
                                          bind-form ;(fnk-impl/ensure-schema-metadata &env bind-form)
                                          []
                                          cur-body-form)]
         `(let [~map-sym ~value-form] ~body-form))))
   `(do ~@body)
   (reverse (partition 2 bindings))))
