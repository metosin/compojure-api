(ns compojure.api.common)

(defmacro map-of
  "creates map with symbol names as keywords as keys and
  symbol values as values."
  [& syms]
  `(zipmap ~(vec (map keyword syms)) ~(vec syms)))

(defn plain-map?
  "checks whether input is a map, but not a record"
  [x] (and (map? x) (not (record? x))))

(defn extract-parameters
  "Extract parameters from head of the list. Parameters can be:

  1. a map (if followed by any form) `[{:a 1 :b 2} :body]` => `{:a 1 :b 2}`
  2. list of keywords & values `[:a 1 :b 2 :body]` => `{:a 1 :b 2}`
  3. else => `{}`

  Returns a tuple with parameters and body without the parameters"
  [c]
  (cond
    (plain-map? (first c))
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
