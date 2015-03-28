(ns compojure.api.common-test
  (:require [compojure.api.meta :refer :all]
            [midje.sweet :refer :all]))

(fact "unwrapping meta-container"
  (fact "meta-data is returned"
    (unwrap-meta-container '(meta-container {:a 1} identity)) => {:a 1})
  (fact "non-map meta-data can't be unwrapped"
    (unwrap-meta-container '(meta-container :abba identity)) => (throws AssertionError))
  (fact "unwrapping non-meta-container returns empty map"
    (unwrap-meta-container 'identity) => {}))
