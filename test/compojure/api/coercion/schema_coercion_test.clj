(ns compojure.api.coercion.schema-coercion-test
  (:require [midje.sweet :refer :all]
            [schema.core :as s]
            [compojure.api.request :as request]
            [compojure.api.coercion :as coercion]
            [compojure.api.coercion.schema :as cs])
  (:import (schema.utils ValidationError NamedError)))

(fact "stringify-error"
  (fact "ValidationError"
    (class (s/check s/Int "foo")) => ValidationError
    (cs/stringify (s/check s/Int "foo")) => "(not (integer? \"foo\"))"
    (cs/stringify (s/check {:foo s/Int} {:foo "foo"})) => {:foo "(not (integer? \"foo\"))"})
  (fact "NamedError"
    (class (s/check (s/named s/Int "name") "foo")) => NamedError
    (cs/stringify (s/check (s/named s/Int "name") "foo")) => "(named (not (integer? \"foo\")) \"name\")"))

(s/defschema Schema {:kikka s/Keyword})

(def valid-value {:kikka :kukka})
(def invalid-value {:kikka "kukka"})

(fact "request-coercion"
  (let [c! #(coercion/coerce-request! Schema :body-params :body false false %)]

    (fact "default coercion"
      (c! {:body-params valid-value}) => valid-value
      (c! {:body-params invalid-value}) => throws
      (try
        (c! {:body-params invalid-value})
        (catch Exception e
          (ex-data e) => (just {:type :compojure.api.exception/request-validation
                                :coercion (coercion/resolve-coercion :schema)
                                :in [:request :body-params]
                                :schema Schema
                                :value invalid-value
                                :errors anything
                                :request {:body-params {:kikka "kukka"}}}))))

    (fact ":schema coercion"
      (c! {:body-params valid-value
           ::request/coercion :schema}) => valid-value
      (c! {:body-params invalid-value
           ::request/coercion :schema}) => throws)

    (fact "format-based coercion"
      (c! {:body-params valid-value
           :muuntaja/request {:format "application/json"}}) => valid-value
      (c! {:body-params invalid-value
           :muuntaja/request {:format "application/json"}}) => valid-value)

    (fact "no coercion"
      (c! {:body-params valid-value
           ::request/coercion nil
           :muuntaja/request {:format "application/json"}}) => valid-value
      (c! {:body-params invalid-value
           ::request/coercion nil
           :muuntaja/request {:format "application/json"}}) => invalid-value)))

(defn ok [body]
  {:status 200, :body body})

(defn ok? [body]
  (contains (ok body)))

(def responses {200 {:schema Schema}})

(def custom-coercion
  (cs/->SchemaCoercion
    :custom
    (-> cs/default-options
        (assoc-in [:response :formats "application/json"] cs/json-coercion-matcher))))

(fact "response-coercion"
  (let [c! coercion/coerce-response!]

    (fact "default coercion"
      (c! ..request.. (ok valid-value) responses) => (ok? valid-value)
      (c! ..request.. (ok invalid-value) responses) => (throws)
      (try
        (c! ..request.. (ok invalid-value) responses)
        (catch Exception e
          (ex-data e) => (contains {:type :compojure.api.exception/response-validation
                                    :coercion (coercion/resolve-coercion :schema)
                                    :in [:response :body]
                                    :schema Schema
                                    :value invalid-value
                                    :errors anything
                                    :request ..request..}))))

    (fact ":schema coercion"
      (fact "default coercion"
        (c! {::request/coercion :schema}
            (ok valid-value)
            responses) => (ok? valid-value)
        (c! {::request/coercion :schema}
            (ok invalid-value)
            responses) => throws))

    (fact "format-based custom coercion"
      (fact "request-negotiated response format"
        (c! irrelevant
            (ok invalid-value)
            responses) => throws
        (c! {:muuntaja/response {:format "application/json"}
             ::request/coercion custom-coercion}
            (ok invalid-value)
            responses) => (ok? valid-value)))

    (fact "no coercion"
      (c! {::request/coercion nil}
          (ok valid-value)
          responses) => (ok? valid-value)
      (c! {::request/coercion nil}
          (ok invalid-value)
          responses) => (ok? invalid-value))))

