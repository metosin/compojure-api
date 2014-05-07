(ns compojure.api.common-test
  (:require [midje.sweet :refer :all]
            [compojure.api.common :refer :all]
            potemkin))

(fact "path-vals"
  (let [original {:a {:b {:c 1
                          :d 2}
                      :e 3}}
        target (seq [
                [[:a :b :d] 2]
                [[:a :b :c] 1]
                [[:a :e] 3]])]
    (path-vals original) => target
    (assoc-in-path-vals target) => original))

(defrecord Plane [x y z])
(def *tuples [[:x 1] [:y 2] [:z 3]])
(def *map    {:x 1 :y 2 :z 3})
(def *record (->Plane 1 2 3))

(fact "->map"
  (->map *map) => *map
  (->map *tuples) => *map
  (->map *record) => *map)

(potemkin/import-vars [clojure.walk walk])

(fact "re-resolve"

  (fact "potemkin'd var is imported locally"
    #'walk => #'compojure.api.common-test/walk)

  (fact "non-symbol/var resolves to nil"
    (re-resolve 1) => nil)

  (fact "re-resolve to the rescue!"
    (re-resolve walk) => #'clojure.walk/walk
    (re-resolve 'walk) => #'clojure.walk/walk
    (re-resolve #'walk) => #'clojure.walk/walk))

(defmacro re-resolve-in-compile-time [sym]
  (let [resolved (re-resolve sym)]
    `~resolved))

(defmacro eval-re-resolve-in-compile-time [sym]
  (let [resolved (eval-re-resolve sym)]
    `~resolved))

(fact "re-resolve in compile-time"
  (fact "re-resolve does not work with macros"
    (re-resolve-in-compile-time 'walk) => nil)
  (fact "eval-re-resolve works with macros"
    (eval-re-resolve-in-compile-time 'walk) => #'clojure.walk/walk))

(fact "->Long"
  (->Long 123) => 123
  (->Long 12.3) => (throws IllegalArgumentException)
  (->Long "123") => 123
  (->Long "12.3") => (throws NumberFormatException))

(fact "assoc-map"
  (fact "assoc for array-map loses its order"
    (keys (reduce (partial apply assoc) (array-map) (map-indexed vector (range 100)))) =not=> (range 100))
  (fact "assoc-map-ordered for array-map retains its order"
    (keys (reduce (partial apply assoc-map-ordered) (array-map) (map-indexed vector (range 100)))) => (range 100)))

(fact "map-of"
  (let [a 1 b true c [:abba :jabba]]
    (map-of a b c) => {:a 1 :b true :c [:abba :jabba]}))

(fact "unwrapping meta-container"
  (fact "meta-data is returned"
    (unwrap-meta-container '(meta-container {:a 1} identity)) => {:a 1})
  (fact "non-map meta-data can't be unwrapped"
    (unwrap-meta-container '(meta-container :abba identity)) => (throws AssertionError))
  (fact "unwrapping non-meta-container returns empty map"
    (unwrap-meta-container 'identity) => {}))
