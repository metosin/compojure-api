(ns compojure.api.common-test
  (:require [midje.sweet :refer :all]
            [compojure.api.common :refer :all]))

(fact "path-vals"
  (let [original {:a {:b {:c 1
                          :d 2}
                      :e 3}}
        target [[[:a :e] 3]
                [[:a :b :d] 2]
                [[:a :b :c] 1]]]
    (path-vals original) => target
    (assoc-in-path-vals target) => original))

(defrecord Plane [x y z])
(def *tuples [[:x 1] [:y 2] [:z 3]])
(def *map    {:x 1 :y 2 :z 3})
(def *record (->Plane 1 2 3))

(fact "fn-> & fn->>"
  (let [inc-x  (fn-> :x inc)
        sum-vals (fn->> vals (apply +))]
    (inc-x *map) => 2
    (sum-vals *map) => 6))

(fact "->map"
  (->map *map) => *map
  (->map *tuples) => *map
  (->map *record) => *map)

(fact "record?"
  (record? *map) => false
  (record? *tuples) => false
  (record? *record) => true)

(fact "map-def"
  (let [f (map-def (fn->> vals (apply +)))]
    (f {:x 1 :y 2 :z 3}) => 6
    (f "x") => "x"))

(fact "map-defn"
  (let [f (map-defn [{:keys [x y z]}] (+ x y z))]
    (f {:x 1 :y 2 :z 3}) => 6
    (f "x") => "x"))

(fact "remove-empty-keys"
  (remove-empty-keys {:a nil :b false :c 0}) => {:b false :c 0})

(def Abba nil)

(fact "name-of"
  (name-of (resolve 'Abba)) => "Abba"
  (name-of 'Abba)  => "Abba"
  (name-of "Abba") => "Abba"
  (name-of :Abba)  => "Abba"
  (name-of nil)    => nil)
