(ns compojure.api.common-test
  (:require [compojure.api.common :as common]
            [midje.sweet :refer :all]
            [criterium.core :as cc]))

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

(fact "fast-merge-map"
  (let [x {:a 1, :b 2, :c 3}
        y {:a 2, :d 4, :e 5}]
    (common/fast-map-merge x y) => {:a 2, :b 2, :c 3 :d 4, :e 5}
    (common/fast-map-merge x y) => (merge x y)))

(comment
  (require '[criterium.core :as cc])

  ;; 163ns
  (cc/quick-bench
    (common/fast-map-merge
      {:a 1, :b 2, :c 3}
      {:a 2, :d 4, :e 5}))

  ;; 341ns
  (cc/quick-bench
    (merge
      {:a 1, :b 2, :c 3}
      {:a 2, :d 4, :e 5})))
