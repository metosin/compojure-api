(ns compojure.api.common-test
  (:require [compojure.api.common :as common]
            [clojure.test :refer [deftest testing is]]
            [criterium.core :as cc]))

(deftest group-with-test
  (is (= (common/group-with pos? [1 -10 2 -4 -1 999]) [[1 2 999] [-10 -4 -1]]))
  (is (= (common/group-with pos? [1 2 999]) [[1 2 999] nil])))

(deftest extract-parameters-test

  (testing "expect body"
    (is (= (common/extract-parameters [] true) [{} nil]))
    (is (= (common/extract-parameters [{:a 1}] true) [{} [{:a 1}]]))
    (is (= (common/extract-parameters [:a 1] true) [{:a 1} nil]))
    (is (= (common/extract-parameters [{:a 1} {:b 2}] true) [{:a 1} [{:b 2}]]))
    (is (= (common/extract-parameters [:a 1 {:b 2}] true) [{:a 1} [{:b 2}]])))

  (testing "don't expect body"
    (is (= (common/extract-parameters [] false) [{} nil]))
    (is (= (common/extract-parameters [{:a 1}] false) [{:a 1} nil]))
    (is (= (common/extract-parameters [:a 1] false) [{:a 1} nil]))
    (is (= (common/extract-parameters [{:a 1} {:b 2}] false) [{:a 1} [{:b 2}]]))
    (is (= (common/extract-parameters [:a 1 {:b 2}] false) [{:a 1} [{:b 2}]]))))

(deftest merge-vector-test
  (is (= (common/merge-vector nil) nil))
  (is (= (common/merge-vector [{:a 1}]) {:a 1}))
  (is (= (common/merge-vector [{:a 1} {:b 2}]) {:a 1 :b 2})))

(deftest fast-merge-map-test
  (let [x {:a 1, :b 2, :c 3}
        y {:a 2, :d 4, :e 5}]
    (is (= (common/fast-map-merge x y) {:a 2, :b 2, :c 3 :d 4, :e 5}))
    (is (= (common/fast-map-merge x y) (merge x y)))))

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
