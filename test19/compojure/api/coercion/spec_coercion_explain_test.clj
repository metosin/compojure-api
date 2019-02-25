(ns compojure.api.coercion.spec-coercion-explain-test
  (:require [midje.sweet :refer :all]
            [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [compojure.api.test-utils :refer :all]
            [compojure.api.sweet :refer :all]
            [compojure.api.request :as request]
            [compojure.api.coercion :as coercion]
            [spec-tools.core :as st]))

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

(def custom-coercion
  (-> compojure.api.coercion.spec/default-options
      (assoc-in
        [:body :formats "application/json"]
        st/string-transformer)
      compojure.api.coercion.spec/create-coercion))

(def valid-value {:name "foo" :age "24" :languages ["clj"] :birthdate "1968-01-02T15:04:05Z"})
(def coerced-value {:name "foo" :age "24" :languages #{:clj} :birthdate #inst "1968-01-02T15:04:05Z"})
(def invalid-value {:name "foo" :age "24" :lanxguages ["clj"] :birthdate "1968-01-02T15:04:05Z"})

(fact "request-coercion"
      (let [c! #(coercion/coerce-request! ::spec :body-params :body false false %)]

        (fact "default coercion"
              (c! {:body-params       valid-value
                   :muuntaja/request  {:format "application/json"}
                   ::request/coercion :spec}) => coerced-value
              (c! {:body-params       invalid-value
                   :muuntaja/request  {:format "application/json"}
                   ::request/coercion :spec}) => (throws)
              (try
                (c! {:body-params       invalid-value
                     :muuntaja/request  {:format "application/json"}
                     ::request/coercion :spec})
                (catch Exception e
                  (let [data          (ex-data e)
                        spec-problems (get-in data [:problems ::s/problems])]
                    (count spec-problems) => 1
                    (first spec-problems) => (contains {:in   []
                                                        :path []
                                                        :val  {:age       "24"
                                                               :birthdate #inst "1968-01-02T15:04:05Z"
                                                               :name      "foo"}
                                                        :via  [::spec]})))))))


