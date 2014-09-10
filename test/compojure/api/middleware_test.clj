(ns compojure.api.middleware-test
  (:require [midje.sweet :refer :all]
            [compojure.api.middleware :refer :all]
            ring.util.test))

(facts serializable?
  (tabular
    (fact
      (serializable? nil
                     {:body ?body
                      :compojure.api.meta/serializable? ?serializable?}) => ?res)
    ?body ?serializable? ?res
    5 true true
    5 false false
    "foobar" true true
    "foobar" false false

    {:foobar "1"} false true
    {:foobar "1"} true true
    [1 2 3] false true
    [1 2 3] true true

    (ring.util.test/string-input-stream "foobar") false false
    ))


