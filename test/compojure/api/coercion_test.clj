(ns compojure.api.coercion-test
  (:require [compojure.api.sweet :refer :all]
            [compojure.api.test-utils :refer :all]
            [clojure.test :refer [deftest]]
            [clojure.test :refer [deftest testing is]]
            [ring.util.http-response :refer :all]
            [clojure.core.async :as a]
            [schema.core :as s]
            [compojure.api.middleware :as mw]
            [compojure.api.coercion.schema :as cs]))

(defn is-has-body [expected value]
  (is (= (second value) expected)))

(defn is-fails-with [expected-status [status body]]
  (is (= status expected-status)
      (pr-str body))
  (is (every? (partial contains? body) [:type :coercion :in :value :schema :errors])
      (pr-str body)))

(deftest schema-coercion-test
  (testing "response schemas"
    (let [r-200 (GET "/" []
                  :query-params [{value :- s/Int nil}]
                  :responses {200 {:schema {:value s/Str}}}
                  (ok {:value (or value "123")}))
          r-default (GET "/" []
                      :query-params [{value :- s/Int nil}]
                      :responses {:default {:schema {:value s/Str}}}
                      (ok {:value (or value "123")}))
          r-200-default (GET "/" []
                          :query-params [{value :- s/Int nil}]
                          :responses {200 {:schema {:value s/Str}}
                                      :default {:schema {:value s/Int}}}
                          (ok {:value (or value "123")}))]
      (testing "200"
        (is-has-body {:value "123"} (get* (api {:formatter :muuntaja} r-200) "/"))
        (is-fails-with 500 (get* (api {:formatter :muuntaja} r-200) "/" {:value 123})))

      (testing "exception data"
        (let [ex (get* (api {:formatter :muuntaja} r-200) "/" {:value 123})]
          (is (= 500 (first ex)))
          (is (= {:type "compojure.api.exception/response-validation"
                  :coercion "schema",
                  :in ["response" "body"],
                  :value {:value 123},
                  :schema "{:value java.lang.String}",
                  :errors {:value "(not (instance? java.lang.String 123))"}}
                 (select-keys (second ex) [:type :coercion :in :value :schema :errors])))))

      (testing ":default"
        (is-has-body {:value "123"} (get* (api {:formatter :muuntaja} r-default) "/"))
        (is-fails-with 500 (get* (api {:formatter :muuntaja} r-default) "/" {:value 123})))

      (testing ":default"
        (is-has-body {:value "123"} (get* (api {:formatter :muuntaja} r-200-default) "/"))
        (is-fails-with 500 (get* (api {:formatter :muuntaja} r-200-default) "/" {:value 123})))))

  (testing "custom coercion"

    (testing "response coercion"
      (let [ping-route (GET "/ping" []
                         :return {:pong s/Str}
                         (ok {:pong 123}))]

        (testing "by default, applies response coercion"
          (let [app (api
                      {:formatter :muuntaja}
                      ping-route)]
            (is-fails-with 500 (get* app "/ping"))))

        (testing "response-coercion can be disabled"
          (testing "separately"
            (let [app (api
                        {:formatter :muuntaja
                         :coercion (cs/create-coercion (dissoc cs/default-options :response))}
                        ping-route)]
              (let [[status body] (get* app "/ping")]
                (is (= 200 status))
                (is (= {:pong 123} body))))
            (testing "legacy"
              (let [app (api
                          {:formatter :muuntaja
                           :coercion mw/no-response-coercion}
                          ping-route)]
                (let [[status body] (get* app "/ping")]
                  (is (= 200 status))
                  (is (= {:pong 123} body))))))
          (testing "all coercion"
            (let [app (api
                        {:formatter :muuntaja
                         :coercion nil}
                        ping-route)]
              (let [[status body] (get* app "/ping")]
                (is (= 200 status))
                (is (= {:pong 123} body))))))

        (testing "coercion for async handlers"
          (binding [*async?* true]
            (testing "successful"
              (let [app (api
                          {:formatter :muuntaja}
                          (GET "/async" []
                               :return s/Str
                               (a/go (ok "abc"))))]
                (is-has-body "abc" (get* app "/async"))))
            (testing "failing"
              (let [app (api
                          {:formatter :muuntaja}
                          (GET "/async" []
                               :return s/Int
                               (a/go (ok "foo"))))]
                (is-fails-with 500 (get* app "/async"))))))))

    (testing "body coercion"
      (let [beer-route (POST "/beer" []
                         :body [body {:beers #{(s/enum "ipa" "apa")}}]
                         (ok body))]

        (testing "by default, applies body coercion (to set)"
          (let [app (api
                      {:formatter :muuntaja}
                      beer-route)]
            (let [[status body] (post* app "/beer" (json-string {:beers ["ipa" "apa" "ipa"]}))]
              (is (= 200 status))
              (is (= {:beers ["ipa" "apa"]} body)))))

        (testing "body-coercion can be disabled"
          (let [no-body-coercion (cs/create-coercion (dissoc cs/default-options :body))
                app (api
                      {:formatter :muuntaja
                       :coercion no-body-coercion}
                      beer-route)]
            (let [[status body] (post* app "/beer" (json-string {:beers ["ipa" "apa" "ipa"]}))]
              (is (= 200 status))
              (is (= {:beers ["ipa" "apa" "ipa"]} body))))
          (let [app (api
                      {:formatter :muuntaja
                       :coercion nil}
                      beer-route)]
            (let [[status body] (post* app "/beer" (json-string {:beers ["ipa" "apa" "ipa"]}))]
              (is (= 200 status))
              (is (= {:beers ["ipa" "apa" "ipa"]} body)))))

        (testing "legacy body-coercion can be disabled"
          (let [no-body-coercion (constantly (dissoc mw/default-coercion-matchers :body))
                app (api
                      {:formatter :muuntaja
                       :coercion no-body-coercion}
                      beer-route)]
            (let [[status body] (post* app "/beer" (json-string {:beers ["ipa" "apa" "ipa"]}))]
              (is (= 200 status))
              (is (= {:beers ["ipa" "apa" "ipa"]} body))))
          (let [app (api
                      {:formatter :muuntaja
                       :coercion nil}
                      beer-route)]
            (let [[status body] (post* app "/beer" (json-string {:beers ["ipa" "apa" "ipa"]}))]
              (is (= 200 status))
              (is (= {:beers ["ipa" "apa" "ipa"]} body)))))

        (testing "body-coercion can be changed"
          (let [nop-body-coercion (cs/create-coercion (assoc cs/default-options :body {:default (constantly nil)}))
                app (api
                      {:formatter :muuntaja
                       :coercion nop-body-coercion}
                      beer-route)]
            (is-fails-with 400 (post* app "/beer" (json-string {:beers ["ipa" "apa" "ipa"]})))))
        (testing "legacy body-coercion can be changed"
          (let [nop-body-coercion (constantly (assoc mw/default-coercion-matchers :body (constantly nil)))
                app (api
                      {:formatter :muuntaja
                       :coercion nop-body-coercion}
                      beer-route)]
            (is-fails-with 400 (post* app "/beer" (json-string {:beers ["ipa" "apa" "ipa"]})))))))

    (testing "query coercion"
      (let [query-route (GET "/query" []
                          :query-params [i :- s/Int]
                          (ok {:i i}))]

        (testing "by default, applies query coercion (string->int)"
          (let [app (api
                      {:formatter :muuntaja}
                      query-route)]
            (let [[status body] (get* app "/query" {:i 10})]
              (is (= 200 status))
              (is (= {:i 10} body)))))

        (testing "query-coercion can be disabled"
          (let [no-query-coercion (cs/create-coercion (dissoc cs/default-options :string))
                app (api
                      {:formatter :muuntaja
                       :coercion no-query-coercion}
                      query-route)]
            (let [[status body] (get* app "/query" {:i 10})]
              (is (= 200 status))
              (is (= {:i "10"} body)))))

        (testing "legacy query-coercion can be disabled"
          (let [no-query-coercion (constantly (dissoc mw/default-coercion-matchers :string))
                app (api
                      {:formatter :muuntaja
                       :coercion no-query-coercion}
                      query-route)]
            (let [[status body] (get* app "/query" {:i 10})]
              (is (= 200 status))
              (is (= {:i "10"} body)))))

        (testing "query-coercion can be changed"
          (let [nop-query-coercion (cs/create-coercion (assoc cs/default-options :string {:default (constantly nil)}))
                app (api
                      {:formatter :muuntaja
                       :coercion nop-query-coercion}
                      query-route)]
            (is-fails-with 400 (get* app "/query" {:i 10}))))

        (testing "legacy query-coercion can be changed"
          (let [nop-query-coercion (constantly (assoc mw/default-coercion-matchers :string (constantly nil)))
                app (api
                      {:formatter :muuntaja
                       :coercion nop-query-coercion}
                      query-route)]
            (is-fails-with 400 (get* app "/query" {:i 10}))))))

    (testing "route-specific coercion"
      (let [app (api
                  {:formatter :muuntaja}
                  (GET "/default" []
                    :query-params [i :- s/Int]
                    (ok {:i i}))
                  (GET "/disabled-coercion" []
                    :coercion (cs/create-coercion (assoc cs/default-options :string {:default (constantly nil)}))
                    :query-params [i :- s/Int]
                    (ok {:i i}))
                  (GET "/no-coercion" []
                    :coercion nil
                    :query-params [i :- s/Int]
                    (ok {:i i})))]

        (testing "default coercion"
          (let [[status body] (get* app "/default" {:i 10})]
            (is (= 200 status))
            (is (= {:i 10} body))))

        (testing "disabled coercion"
          (is-fails-with 400 (get* app "/disabled-coercion" {:i 10})))

        (testing "exception data"
          (let [ex (get* app "/disabled-coercion" {:i 10})]
            (is (= 400 (first ex)))
            (is (= {:type "compojure.api.exception/request-validation"
                    :coercion "schema",
                    :in ["request" "query-params"],
                    :value {:i "10"}
                    :schema "{Keyword Any, :i Int}",
                    :errors {:i "(not (integer? \"10\"))"}}
                   (select-keys (second ex)
                                [:type :coercion :in :value :schema :errors])))))

        (testing "no coercion"
          (let [[status body] (get* app "/no-coercion" {:i 10})]
            (is (= 200 status))
            (is (= {:i "10"} body))))))
    (testing "legacy route-specific coercion"
      (let [app (api
                  {:formatter :muuntaja}
                  (GET "/default" []
                    :query-params [i :- s/Int]
                    (ok {:i i}))
                  (GET "/disabled-coercion" []
                    :coercion (constantly (assoc mw/default-coercion-matchers :string (constantly nil)))
                    :query-params [i :- s/Int]
                    (ok {:i i}))
                  (GET "/no-coercion" []
                    :coercion (constantly nil)
                    :query-params [i :- s/Int]
                    (ok {:i i}))
                  (GET "/nil-coercion" []
                    :coercion nil
                    :query-params [i :- s/Int]
                    (ok {:i i})))]

        (testing "default coercion"
          (let [[status body] (get* app "/default" {:i 10})]
            (is (= 200 status))
            (is (= {:i 10} body))))

        (testing "disabled coercion"
          (is-fails-with 400 (get* app "/disabled-coercion" {:i 10})))

        (testing "exception data"
          (let [ex (get* app "/disabled-coercion" {:i 10})]
            (is (= 400 (first ex)))
            (is (= {:type "compojure.api.exception/request-validation"
                    :coercion "schema",
                    :in ["request" "query-params"],
                    :value {:i "10"}
                    :schema "{Keyword Any, :i Int}",
                    :errors {:i "(not (integer? \"10\"))"}}
                   (select-keys (second ex)
                                [:type :coercion :in :value :schema :errors])))))

        (testing "no coercion"
          (let [[status body] (get* app "/no-coercion" {:i 10})]
            (is (= 200 status))
            (is (= {:i "10"} body)))
          (let [[status body] (get* app "/nil-coercion" {:i 10})]
            (is (= 200 status))
            (is (= {:i "10"} body)))))))

  (testing "apiless coercion"

    (testing "use default-coercion-matchers by default"
      (let [app (context "/api" []
                  :query-params [{y :- Long 0}]
                  (GET "/ping" []
                    :query-params [x :- Long]
                    (ok [x y])))]
        (is (thrown? Exception (app {:request-method :get :uri "/api/ping" :query-params {}})))
        (is (thrown? Exception (app {:request-method :get :uri "/api/ping" :query-params {:x "abba"}})))
        (is (= [1 0] (:body (app {:request-method :get :uri "/api/ping" :query-params {:x "1"}}))))
        (is (= [1 2] (:body (app {:request-method :get :uri "/api/ping" :query-params {:x "1", :y 2}}))))
        (is (thrown? Exception (app {:request-method :get :uri "/api/ping" :query-params {:x "1", :y "abba"}})))))

    (testing "coercion can be overridden"
      (let [app (context "/api" []
                  :query-params [{y :- Long 0}]
                  (GET "/ping" []
                    :coercion nil
                    :query-params [x :- Long]
                    (ok [x y])))]
        (is (thrown? Exception (app {:request-method :get :uri "/api/ping" :query-params {}})))
        (is (= ["abba" 0] (:body (app {:request-method :get :uri "/api/ping" :query-params {:x "abba"}}))))
        (is (= ["1" 0] (:body (app {:request-method :get :uri "/api/ping" :query-params {:x "1"}}))))
        (is (= ["1" 2] (:body (app {:request-method :get :uri "/api/ping" :query-params {:x "1", :y 2}}))))
        (is (thrown? Exception (app {:request-method :get :uri "/api/ping" :query-params {:x "1", :y "abba"}})))))

    (testing "legacy coercion can be overridden"
      (let [app (context "/api" []
                  :query-params [{y :- Long 0}]
                  (GET "/ping" []
                    :coercion (constantly nil)
                    :query-params [x :- Long]
                    (ok [x y])))]
        (is (thrown? Exception (app {:request-method :get :uri "/api/ping" :query-params {}})))
        (is (= ["abba" 0] (:body (app {:request-method :get :uri "/api/ping" :query-params {:x "abba"}}))))
        (is (= ["1" 0] (:body (app {:request-method :get :uri "/api/ping" :query-params {:x "1"}}))))
        (is (= ["1" 2] (:body (app {:request-method :get :uri "/api/ping" :query-params {:x "1", :y 2}}))))
        (is (thrown? Exception (app {:request-method :get :uri "/api/ping" :query-params {:x "1", :y "abba"}})))))

    (testing "context coercion is used for subroutes"
      (let [app (context "/api" []
                  :coercion nil
                  (GET "/ping" []
                    :query-params [x :- Long]
                    (ok x)))]
        (is (= "abba" (:body (app {:request-method :get :uri "/api/ping" :query-params {:x "abba"}}))))))))
