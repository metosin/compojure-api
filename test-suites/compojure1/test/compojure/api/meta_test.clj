(ns compojure.api.meta-test
  (:require [compojure.api.meta :refer :all]
            [midje.sweet :refer :all]))

(fact "src-coerce! with deprecated types"
  (src-coerce! nil nil :query) => (throws AssertionError)
  (src-coerce! nil nil :json) => (throws AssertionError))
