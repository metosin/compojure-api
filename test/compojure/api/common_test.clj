(ns compojure.api.common-test
  (:require [compojure.api.common :refer :all]
            [midje.sweet :refer :all]))

(fact "map-of"
  (let [a 1 b true c [:abba :jabba]]
    (map-of a b c) => {:a 1 :b true :c [:abba :jabba]}))
