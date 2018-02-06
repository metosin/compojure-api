(ns compojure.api.common-test
  (:require [compojure.api.common :as common]
            [midje.sweet :refer :all]))

(fact "group-with"
  (common/group-with pos? [1 -10 2 -4 -1 999]) => [[1 2 999] [-10 -4 -1]]
  (common/group-with pos? [1 2 999]) => [[1 2 999] nil])

(fact "extract-parameters"

  (facts "expect body"
    (common/extract-parameters [] true) => [{} nil]
    (common/extract-parameters [{:a 1}] true) => [{} [{:a 1}]]
    (common/extract-parameters [:a 1] true) => [{:a 1} nil]
    (common/extract-parameters [{:a 1} {:b 2}] true) => [{:a 1} [{:b 2}]]
    (common/extract-parameters [:a 1 {:b 2}] true) => [{:a 1} [{:b 2}]])

  (facts "don't expect body"
    (common/extract-parameters [] false) => [{} nil]
    (common/extract-parameters [{:a 1}] false) => [{:a 1} nil]
    (common/extract-parameters [:a 1] false) => [{:a 1} nil]
    (common/extract-parameters [{:a 1} {:b 2}] false) => [{:a 1} [{:b 2}]]
    (common/extract-parameters [:a 1 {:b 2}] false) => [{:a 1} [{:b 2}]]))

(fact "merge-vector"
  (common/merge-vector nil) => nil
  (common/merge-vector [{:a 1}]) => {:a 1}
  (common/merge-vector [{:a 1} {:b 2}]) => {:a 1 :b 2})
