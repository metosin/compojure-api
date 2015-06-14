(ns compojure.api.coercion-test
  (:require [compojure.api.sweet :refer :all]
            [compojure.api.test-utils :refer :all]
            [midje.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [compojure.api.middleware :as mw]))

(fact "custom coercion"

  (fact "response coercion"
    (let [ping-route (GET* "/ping" []
                       :return {:pong s/Str}
                       (ok {:pong 123}))]

      (fact "by default, applies response coercion"
        (let [app (api
                    ping-route)]
          (let [[status body] (get* app "/ping")]
            status => 500
            body => (contains {:errors irrelevant}))))

      (fact "response-coersion can ba disabled"
        (let [app (api
                    {:coercion mw/no-response-coercion}
                    ping-route)]
          (let [[status body] (get* app "/ping")]
            status => 200
            body => {:pong 123})))))

  (fact "body coersion"
    (let [beer-route (POST* "/beer" []
                       :body [body {:beers #{(s/enum "ipa" "apa")}}]
                       (ok body))]

      (fact "by default, applies body coercion (to set)"
        (let [app (api
                    beer-route)]
          (let [[status body] (post* app "/beer" (json {:beers ["ipa" "apa" "ipa"]}))]
            status => 200
            body => {:beers ["ipa" "apa"]})))

      (fact "body-coersion can ba disabled"
        (let [no-body-coercion (fn [_] (dissoc mw/default-coercion-matchers :body))
              app (api
                    {:coercion no-body-coercion}
                    beer-route)]
          (let [[status body] (post* app "/beer" (json {:beers ["ipa" "apa" "ipa"]}))]
            status => 200
            body => {:beers ["ipa" "apa" "ipa"]})))

      (fact "body-coersion can ba changed"
        (let [nop-body-coercion (fn [_] (assoc mw/default-coercion-matchers :body (constantly nil)))
              app (api
                    {:coercion nop-body-coercion}
                    beer-route)]
          (let [[status body] (post* app "/beer" (json {:beers ["ipa" "apa" "ipa"]}))]
            status => 400
            body => (contains {:errors irrelevant}))))))

  (fact "query coersion"
    (let [query-route (GET* "/query" []
                        :query-params [i :- s/Int]
                        (ok {:i i}))]

      (fact "by default, applies query coercion (string->int)"
        (let [app (api
                    query-route)]
          (let [[status body] (get* app "/query" {:i 10})]
            status => 200
            body => {:i 10})))

      (fact "query-coersion can ba disabled"
        (let [no-query-coercion (fn [_] (dissoc mw/default-coercion-matchers :string))
              app (api
                    {:coercion no-query-coercion}
                    query-route)]
          (let [[status body] (get* app "/query" {:i 10})]
            status => 200
            body => {:i "10"})))

      (fact "query-coersion can ba changed"
        (let [nop-query-coercion (fn [_] (assoc mw/default-coercion-matchers :string (constantly nil)))
              app (api
                    {:coercion nop-query-coercion}
                    query-route)]
          (let [[status body] (get* app "/query" {:i 10})]
            status => 400
            body => (contains {:errors irrelevant}))))))

  (fact "route-spesific coercion"
    (let [app (api
                (GET* "/default" []
                  :query-params [i :- s/Int]
                  (ok {:i i}))
                (GET* "/disabled-coercion" []
                  :coercion (fn [_] (assoc mw/default-coercion-matchers :string (constantly nil)))
                  :query-params [i :- s/Int]
                  (ok {:i i}))
                (GET* "/no-coercion" []
                  :coercion (constantly nil)
                  :query-params [i :- s/Int]
                  (ok {:i i})))]
      (fact "default coercion"
        (let [[status body] (get* app "/default" {:i 10})]
          status => 200
          body => {:i 10}))
      (fact "disabled coercion"
        (let [[status body] (get* app "/disabled-coercion" {:i 10})]
          status => 400
          body => (contains {:errors irrelevant})))
      (fact "no coercion"
        (let [[status body] (get* app "/no-coercion" {:i 10})]
          status => 200
          body => {:i "10"})))))
