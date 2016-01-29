(ns compojure.api.common-test
  (:require [compojure.api.common :refer :all]
            [midje.sweet :refer :all]))

(fact "group-with"
  (group-with pos? [1 -10 2 -4 -1 999]) => [[1 2 999] [-10 -4 -1]]
  (group-with pos? [1 2 999]) => [[1 2 999] nil])
