(ns compojure.api.common-test
  (:require [compojure.api.common :refer :all]
            [midje.sweet :refer :all]))

(fact "map-of"
  (let [a 1 b true c [:abba :jabba]]
    (map-of a b c) => {:a 1 :b true :c [:abba :jabba]}))

(fact "group-with"
  (group-with pos? [1 -10 2 -4 -1 999]) => [[1 2 999] [-10 -4 -1]]
  (group-with pos? [1 2 999]) => [[1 2 999] nil])
