(ns compojure.api.coercion.schema-coercion-test
  (:require [midje.sweet :refer :all]
            [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [compojure.api.test-utils :refer :all]
            [compojure.api.request :as request]
            [compojure.api.coercion :as coercion]
            [compojure.api.validator :as validator]
            [compojure.api.coercion.schema :as cs]
            [compojure.api.coercion.core :as cc])
  (:import (schema.utils ValidationError NamedError)))

(fact "stringify-error"
  (fact "ValidationError"
    (class (s/check s/Int "foo")) => ValidationError
    (cs/stringify (s/check s/Int "foo")) => "(not (integer? \"foo\"))"
    (cs/stringify (s/check {:foo s/Int} {:foo "foo"})) => {:foo "(not (integer? \"foo\"))"})
  (fact "NamedError"
    (class (s/check (s/named s/Int "name") "foo")) => NamedError
    (cs/stringify (s/check (s/named s/Int "name") "foo")) => "(named (not (integer? \"foo\")) \"name\")")
  (fact "Schema"
    (cs/stringify {:total (s/constrained s/Int pos?)}) => {:total "(constrained Int pos?)"}))

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

(s/defschema X s/Int)
(s/defschema Y s/Int)
(s/defschema Total (s/constrained s/Int pos? 'positive-int))
(s/defschema Schema {:x X, :y Y})

(facts "apis"
  (let [app (api
              {:swagger {:spec "/swagger.json"}
               :coercion :schema}

              (POST "/body" []
                :body [{:keys [x y]} Schema]
                (ok {:total (+ x y)}))

              (POST "/body-map" []
                :body [{:keys [x y]} {:x X, (s/optional-key :y) Y}]
                (ok {:total (+ x (or y 0))}))

              (GET "/query" []
                :query [{:keys [x y]} Schema]
                (ok {:total (+ x y)}))

              (GET "/query-params" []
                :query-params [x :- X, y :- Y]
                (ok {:total (+ x y)}))

              (POST "/body-params" []
                :body-params [x :- s/Int, {y :- Y 0}]
                (ok {:total (+ x y)}))

              (POST "/body-string" []
                :body [body s/Str]
                (ok {:body body}))

              (GET "/response" []
                :query-params [x :- X, y :- Y]
                :return {:total Total}
                (ok {:total (+ x y)}))

              (context "/resource" []
                (resource
                  {:get {:parameters {:query-params Schema}
                         :responses {200 {:schema {:total Total}}}
                         :handler (fn [{{:keys [x y]} :query-params}]
                                    (ok {:total (+ x y)}))}
                   :post {:parameters {:body-params {:x s/Int (s/optional-key :y) s/Int}}
                          :responses {200 {:schema {:total Total}}}
                          :handler (fn [{{:keys [x y]} :body-params}]
                                     (ok {:total (+ x (or y 0))}))}})))]

    (fact "query"
      (let [[status body] (get* app "/query" {:x "1", :y 2})]
        status => 200
        body => {:total 3})
      (let [[status body] (get* app "/query" {:x "1", :y "kaks"})]
        status => 400
        body => {:coercion "schema"
                 :in ["request" "query-params"]
                 :errors {:y "(not (integer? \"kaks\"))"}
                 :schema "{:x Int, :y Int}"
                 :type "compojure.api.exception/request-validation"
                 :value {:x "1", :y "kaks"}}))

    (fact "body"
      (let [[status body] (post* app "/body" (json {:x 1, :y 2, #_#_:z 3}))]
        status => 200
        body => {:total 3}))

    (fact "body-map"
      (let [[status body] (post* app "/body-map" (json {:x 1, :y 2}))]
        status => 200
        body => {:total 3})
      (let [[status body] (post* app "/body-map" (json {:x 1}))]
        status => 200
        body => {:total 1}))

    (fact "body-string"
      (let [[status body] (post* app "/body-string" (json "kikka"))]
        status => 200
        body => {:body "kikka"}))

    (fact "query-params"
      (let [[status body] (get* app "/query-params" {:x "1", :y 2})]
        status => 200
        body => {:total 3})
      (let [[status body] (get* app "/query-params" {:x "1", :y "a"})]
        status => 400
        body => (contains {:coercion "schema"
                           :in ["request" "query-params"]})))

    (fact "body-params"
      (let [[status body] (post* app "/body-params" (json {:x 1, :y 2}))]
        status => 200
        body => {:total 3})
      (let [[status body] (post* app "/body-params" (json {:x 1}))]
        status => 200
        body => {:total 1})
      (let [[status body] (post* app "/body-params" (json {:x "1"}))]
        status => 400
        body => (contains {:coercion "schema"
                           :in ["request" "body-params"]})))

    (fact "response"
      (let [[status body] (get* app "/response" {:x 1, :y 2})]
        status => 200
        body => {:total 3})
      (let [[status body] (get* app "/response" {:x -1, :y -2})]
        status => 500
        body => (contains {:coercion "schema"
                           :in ["response" "body"]})))

    (fact "resource"
      (fact "parameters as specs"
        (let [[status body] (get* app "/resource" {:x 1, :y 2})]
          status => 200
          body => {:total 3})
        (let [[status body] (get* app "/resource" {:x -1, :y -2})]
          status => 500
          body => (contains {:coercion "schema"
                             :in ["response" "body"]})))

      (fact "parameters as data-specs"
        (let [[status body] (post* app "/resource" (json {:x 1, :y 2}))]
          status => 200
          body => {:total 3})
        (let [[status body] (post* app "/resource" (json {:x 1}))]
          status => 200
          body => {:total 1})
        (let [[status body] (post* app "/resource" (json {:x -1, :y -2}))]
          status => 500
          body => (contains {:coercion "schema"
                             :in ["response" "body"]}))))

    (fact "generates valid swagger spec"
      (validator/validate app) =not=> (throws))))
