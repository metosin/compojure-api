(ns compojure.api.common
  (:require [org.tobereplaced.lettercase :as lc]))

(defn path-vals
  "Returns vector of tuples containing path vector to the value and the value."
  [m]
  (letfn
    [(pvals [l p m]
       (reduce
         (fn [l [k v]]
           (if (map? v)
             (pvals l (conj p k) v)
             (cons [(conj p k) v] l)))
         l m))]
    (pvals [] [] m)))

(defn assoc-in-path-vals
  "Re-created a map from it's path-vals extracted with (path-vals)."
  [c] (reduce (partial apply assoc-in) {} c))

(defmacro re-resolve
  "Extracts original var from a (potemkined) var or a symbol or returns nil"
  [x]
  (let [evaluated (if (symbol? x) x (eval x))
        resolved  (cond
                    (var? evaluated)    evaluated
                    (symbol? evaluated) (resolve evaluated)
                    :else       nil)
        metadata  (meta resolved)]
    (if metadata
      (let [s (symbol (str (:ns metadata) "/" (:name metadata)))]
        `(var ~s)))))

(defn eval-re-resolve [x] (eval `(re-resolve ~x)))

(defn assoc-map-ordered
  "assocs a value into a map forcing the implementation to be
   clojure.lang.PersistentArrayMap instead of clojure.lang.PersistentHashMap,
   thus retaining the insertion order. O(n)."
  [m k v] (apply array-map (into (vec (apply concat m)) [k v])))

(defmacro map-of
  "creates map with symbol names as keywords as keys and
   symbol values as values."
  [& syms]
  `(zipmap ~(vec (map keyword syms)) ~(vec syms)))

(defn ->CamelCase [x]
  (lc/capitalized x))

(defmacro local-bindings []
  (->> (keys &env)
       (map (fn [k] [`'~k k]))
       (into {})))

(defmacro get-local [s]
  `(get (local-bindings) ~s))
