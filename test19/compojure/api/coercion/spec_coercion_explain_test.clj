(ns compojure.api.coercion.spec-coercion-explain-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [compojure.api.test-utils :refer :all]
            [compojure.api.sweet :refer :all]
            [compojure.api.request :as request]
            [compojure.api.coercion :as coercion]))

(s/def ::birthdate spec/inst?)

(s/def ::name string?)

(s/def ::languages
  (s/coll-of
    (s/and spec/keyword? #{:clj :cljs})
    :into #{}))

(s/def ::spec
  (s/keys
    :req-un [::name ::languages ::age]
    :opt-un [::birthdate]))

(def valid-value {:name "foo" :age "24" :languages ["clj"] :birthdate "1968-01-02T15:04:05Z"})
(def coerced-value {:name "foo" :age "24" :languages #{:clj} :birthdate #inst "1968-01-02T15:04:05Z"})
(def invalid-value {:name "foo" :age "24" :lanxguages ["clj"] :birthdate "1968-01-02T15:04:05Z"})

(deftest request-coercion-test
  (let [c! #(coercion/coerce-request! ::spec :body-params :body false false %)]

    (testing "default coercion"
      (is (= (c! {:body-params valid-value
                  :muuntaja/request {:format "application/json"}
                  ::request/coercion :spec})
             coerced-value))
      (is (thrown? Exception
                   (c! {:body-params invalid-value
                        :muuntaja/request {:format "application/json"}
                        ::request/coercion :spec})))
      (try
        (c! {:body-params invalid-value
             :muuntaja/request {:format "application/json"}
             ::request/coercion :spec})
        (catch Exception e
          (let [data (ex-data e)
                spec-problems (get-in data [:problems ::s/problems])]
            (is (= (count spec-problems) 1))
            (is (= (select-keys (first spec-problems)
                                [:in :path :val :via])
                   {:in []
                    :path []
                    :val {:age "24"
                          :birthdate #inst "1968-01-02T15:04:05Z"
                          :name "foo"}
                    :via [::spec]}))))))))
