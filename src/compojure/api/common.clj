(ns compojure.api.common
  (:require [linked.core :as linked]))

(defn plain-map?
  "checks whether input is a map, but not a record"
  [x] (and (map? x) (not (record? x))))

(defn extract-parameters
  "Extract parameters from head of the list. Parameters can be:

  1. a map (if followed by any form) `[{:a 1 :b 2} :body]` => `{:a 1 :b 2}`
  2. list of keywords & values `[:a 1 :b 2 :body]` => `{:a 1 :b 2}`
  3. else => `{}`

  Returns a tuple with parameters and body without the parameters"
  [c expect-body]
  (cond
    (and (plain-map? (first c)) (or (not expect-body) (seq (rest c))))
    [(first c) (seq (rest c))]

    (keyword? (first c))
    (let [parameters (->> c
                          (partition 2)
                          (take-while (comp keyword? first))
                          (mapcat identity)
                          (apply array-map))
          form (drop (* 2 (count parameters)) c)]
      [parameters (seq form)])

    :else
    [{} (seq c)]))

(defn group-with
  "Groups a sequence with predicate returning a tuple of sequences."
  [pred coll]
  [(seq (filter pred coll))
   (seq (remove pred coll))])

(defn merge-vector
  "Merges vector elements, optimized for 1 arity (x10 faster than merge)."
  [v]
  (if (get v 1)
    (apply merge v)
    (get v 0)))

(defn fast-map-merge
  [x y]
  (reduce-kv
    (fn [m k v]
      (assoc m k v))
    x
    y))

(defn fifo-memoize [f size]
  "Returns a memoized version of a referentially transparent f. The
  memoized version of the function keeps a cache of the mapping from arguments
  to results and, when calls with the same arguments are repeated often, has
  higher performance at the expense of higher memory use. FIFO with size entries."
  (let [cache (atom (linked/map))]
    (fn [& xs]
      (or (@cache xs)
          (let [value (apply f xs)]
            (swap! cache (fn [mem]
                           (let [mem (assoc mem xs value)]
                             (if (>= (count mem) size)
                               (dissoc mem (-> mem first first))
                               mem))))
            value)))))
