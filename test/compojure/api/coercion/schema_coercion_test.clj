(ns compojure.api.coercion.schema-coercion-test
  (:require [schema.core :as s]
            [clojure.test :refer [deftest is testing]]
            [compojure.api.sweet :refer :all]
            [compojure.api.test-utils :refer :all]
            [compojure.api.request :as request]
            [compojure.api.coercion :as coercion]
            [compojure.api.validator :as validator]
            [compojure.api.coercion.schema :as cs]
            [compojure.api.coercion.core :as cc])
  (:import (schema.utils ValidationError NamedError)))

(deftest stringify-error-test
  (testing "ValidationError"
    (is (= ValidationError (class (s/check s/Int "foo"))))
    (is (= "(not (integer? \"foo\"))" (cs/stringify (s/check s/Int "foo"))))
    (is (= {:foo "(not (integer? \"foo\"))"} (cs/stringify (s/check {:foo s/Int} {:foo "foo"})))))
  (testing "NamedError"
    (is (= NamedError (class (s/check (s/named s/Int "name") "foo"))))
    (is (= "(named (not (integer? \"foo\")) \"name\")" (cs/stringify (s/check (s/named s/Int "name") "foo")))))
  (testing "Schema"
    (is (= {:total "(constrained Int pos?)"} (cs/stringify {:total (s/constrained s/Int pos?)})))))

(s/defschema Schema2 {:kikka s/Keyword})

(def valid-value {:kikka :kukka})
(def invalid-value {:kikka "kukka"})

(deftest request-coercion-test
  (let [c! #(coercion/coerce-request! Schema2 :body-params :body false false %)]

    (testing "default coercion"
      (is (= valid-value (c! {:body-params valid-value})))
      (is (thrown? Exception (c! {:body-params invalid-value})))
      (try
        (c! {:body-params invalid-value})
        (catch Exception e
          (is (contains? (ex-data e) :errors))
          (is (= {:type :compojure.api.exception/request-validation
                  :coercion (coercion/resolve-coercion :schema)
                  :in [:request :body-params]
                  :schema Schema2
                  :value invalid-value
                  :request {:body-params {:kikka "kukka"}}}
                 (select-keys (ex-data e)
                              [:type :coercion :in :schema :value :request]))))))

    (testing ":schema coercion"
      (is (= valid-value (c! {:body-params valid-value
                              ::request/coercion :schema})))
      (is (thrown? Exception (c! {:body-params invalid-value
                                  ::request/coercion :schema}))))

    (testing "format-based coercion"
      (is (= valid-value
             (c! {:body-params valid-value
                  :muuntaja/request {:format "application/json"}})))
      (is (= valid-value
             (c! {:body-params invalid-value
                  :muuntaja/request {:format "application/json"}}))))

    (testing "no coercion"
      (is (= valid-value
             (c! {:body-params valid-value
                  ::request/coercion nil
                  :muuntaja/request {:format "application/json"}})))
      (is (= invalid-value
             (c! {:body-params invalid-value
                  ::request/coercion nil
                  :muuntaja/request {:format "application/json"}}))))))

(defn ok [body]
  {:status 200, :body body})

(def responses {200 {:schema Schema2}})

(def custom-coercion
  (cs/->SchemaCoercion
    :custom
    (-> cs/default-options
        (assoc-in [:response :formats "application/json"] cs/json-coercion-matcher))))

(deftest response-coercion-test
  (let [c! coercion/coerce-response!
        request {}]

    (testing "default coercion"
      (is (= (ok valid-value)
             (select-keys (c! request (ok valid-value) responses)
                          [:status :body])))
      (is (thrown? Exception (c! request (ok invalid-value) responses)))
      (try
        (c! request (ok invalid-value) responses)
        (catch Exception e
          (is (contains? (ex-data e) :errors))
          (is (= {:type :compojure.api.exception/response-validation
                  :coercion (coercion/resolve-coercion :schema)
                  :in [:response :body]
                  :schema Schema2
                  :value invalid-value
                  :request request}
                 (select-keys (ex-data e)
                              [:type :coercion :in :schema :value :request]))))))

    (testing ":schema coercion"
      (testing "default coercion"
        (is (= (ok valid-value)
               (select-keys
                 (c! {::request/coercion :schema}
                     (ok valid-value)
                     responses)
                 [:status :body])))
        (is (thrown? Exception
                     (c! {::request/coercion :schema}
                         (ok invalid-value)
                         responses)))))

    (testing "format-based custom coercion"
      (testing "request-negotiated response format"
        (is (thrown? Exception
                     (c! {}
                         (ok invalid-value)
                         responses)))
        (is (= (ok valid-value)
               (select-keys
                 (c! {:muuntaja/response {:format "application/json"}
                      ::request/coercion custom-coercion}
                     (ok invalid-value)
                     responses)
                 [:status :body])))))

    (testing "no coercion"
      (is (= (ok valid-value)
             (select-keys
               (c! {::request/coercion nil}
                   (ok valid-value)
                   responses)
               [:status :body])))
      (is (= (ok invalid-value)
             (select-keys
               (c! {::request/coercion nil}
                   (ok invalid-value)
                   responses)
               [:status :body]))))))

(s/defschema X s/Int)
(s/defschema Y s/Int)
(s/defschema Total (s/constrained s/Int pos? 'positive-int))
(s/defschema Schema1 {:x X, :y Y})

(deftest apis-test
  (let [app (api
              {:formatter :muuntaja
               :swagger {:spec "/swagger.json"}
               :coercion :schema}

              (POST "/body" []
                :body [{:keys [x y]} Schema1]
                (ok {:total (+ x y)}))

              (POST "/body-map" []
                :body [{:keys [x y]} {:x X, (s/optional-key :y) Y}]
                (ok {:total (+ x (or y 0))}))

              (GET "/query" []
                :query [{:keys [x y]} Schema1]
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
                  {:get {:parameters {:query-params Schema1}
                         :responses {200 {:schema {:total Total}}}
                         :handler (fn [{{:keys [x y]} :query-params}]
                                    (ok {:total (+ x y)}))}
                   :post {:parameters {:body-params {:x s/Int (s/optional-key :y) s/Int}}
                          :responses {200 {:schema {:total Total}}}
                          :handler (fn [{{:keys [x y]} :body-params}]
                                     (ok {:total (+ x (or y 0))}))}})))]

    (testing "query"
      (let [[status body] (get* app "/query" {:x "1", :y 2})]
        (is (= 200 status))
        (is (= {:total 3} body)))
      (let [[status body] (get* app "/query" {:x "1", :y "kaks"})]
        (is (= 400 status))
        (is (= {:coercion "schema"
                :in ["request" "query-params"]
                :errors {:y "(not (integer? \"kaks\"))"}
                :schema "{:x Int, :y Int}"
                :type "compojure.api.exception/request-validation"
                :value {:x "1", :y "kaks"}}
               body))))

    (testing "body"
      (let [[status body] (post* app "/body" (json-string {:x 1, :y 2, #_#_:z 3}))]
        (is (= 200 status))
        (is (= {:total 3} body))))

    (testing "body-map"
      (let [[status body] (post* app "/body-map" (json-string {:x 1, :y 2}))]
        (is (= 200 status))
        (is (= {:total 3} body)))
      (let [[status body] (post* app "/body-map" (json-string {:x 1}))]
        (is (= 200 status))
        (is (= {:total 1} body))))

    (testing "body-string"
      (let [[status body] (post* app "/body-string" (json-string "kikka"))]
        (is (= 200 status))
        (is (= {:body "kikka"} body))))

    (testing "query-params"
      (let [[status body] (get* app "/query-params" {:x "1", :y 2})]
        (is (= 200 status))
        (is (= {:total 3} body)))
      (let [[status body] (get* app "/query-params" {:x "1", :y "a"})]
        (is (= 400 status))
        (is (= {:coercion "schema"
                :in ["request" "query-params"]}
               (select-keys body [:coercion :in])))))

    (testing "body-params"
      (let [[status body] (post* app "/body-params" (json-string {:x 1, :y 2}))]
        (is (= 200 status))
        (is (= {:total 3} body)))
      (let [[status body] (post* app "/body-params" (json-string {:x 1}))]
        (is (= 200 status))
        (is (= {:total 1} body)))
      (let [[status body] (post* app "/body-params" (json-string {:x "1"}))]
        (is (= 400 status))
        (is (= {:coercion "schema"
                :in ["request" "body-params"]}
               (select-keys body [:coercion :in])))))

    (testing "response"
      (let [[status body] (get* app "/response" {:x 1, :y 2})]
        (is (= 200 status))
        (is (= {:total 3} body)))
      (let [[status body] (get* app "/response" {:x -1, :y -2})]
        (is (= 500 status))
        (is (= {:coercion "schema"
                :in ["response" "body"]}
               (select-keys body [:coercion :in])))))

    (testing "resource"
      (testing "parameters as specs"
        (let [[status body] (get* app "/resource" {:x 1, :y 2})]
          (is (= 200 status))
          (is (= {:total 3} body)))
        (let [[status body] (get* app "/resource" {:x -1, :y -2})]
          (is (= 500 status))
          (is (= {:coercion "schema"
                  :in ["response" "body"]}
                 (select-keys body [:coercion :in])))))

      (testing "parameters as data-specs"
        (let [[status body] (post* app "/resource" (json-string {:x 1, :y 2}))]
          (is (= 200 status))
          (is (= {:total 3} body)))
        (let [[status body] (post* app "/resource" (json-string {:x 1}))]
          (is (= 200 status))
          (is (= {:total 1} body)))
        (let [[status body] (post* app "/resource" (json-string {:x -1, :y -2}))]
          (is (= 500 status))
          (is (= {:coercion "schema"
                  :in ["response" "body"]}
                 (select-keys body [:coercion :in]))))))

    (testing "generates valid swagger spec"
      (is (validator/validate app)))))
