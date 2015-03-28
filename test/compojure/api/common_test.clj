(ns compojure.api.common-test
  (:require [compojure.api.common :refer :all]
            [midje.sweet :refer :all]
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

(fact "assoc-map"
  (fact "assoc for array-map loses its order"
    (keys (reduce (partial apply assoc) (array-map) (map-indexed vector (range 100)))) =not=> (range 100))
  (fact "assoc-map-ordered for array-map retains its order"
    (keys (reduce (partial apply assoc-map-ordered) (array-map) (map-indexed vector (range 100)))) => (range 100)))

(fact "map-of"
  (let [a 1 b true c [:abba :jabba]]
    (map-of a b c) => {:a 1 :b true :c [:abba :jabba]}))

(fact "->CamelCase"
  (->CamelCase "olipa-kerran") => "OlipaKerran")

(fact "get-local allows local symbol resolution"
  (let [a1 (get-local 'a)
        a  "kikka"
        a2 (get-local 'a)]
    a1 => nil
    a => "kikka"
    a2 => "kikka"))
