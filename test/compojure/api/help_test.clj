(ns compojure.api.help-test
  (:require [compojure.api.help :as help]
            [compojure.api.meta :as meta]
            [clojure.test :refer [deftest is testing]]))

(deftest help-for-api-meta-test
  (testing "all restructure-param methods have a help text"
    (let [restructure-method-names (-> meta/restructure-param methods keys)
          meta-help-topics (-> (methods help/help-for)
                               (dissoc ::help/default)
                               keys
                               (->> (filter #(= :meta (first %)))
                                    (map second)))]
      (is (= (set restructure-method-names) (set meta-help-topics))))))
