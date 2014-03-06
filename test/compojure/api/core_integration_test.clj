(ns compojure.api.core-integration-test
  (:require [midje.sweet :refer :all]
            [schema.core :as s]
            [ring.swagger.schema :refer :all]
            [compojure.api.swagger :as swagger]
            [ring.util.http-response :refer :all]
            [ring.mock.request :as mock]
            [cheshire.core :as cheshire]
            [compojure.core :as compojure]
            [compojure.api.sweet :refer :all]))

(defmodel User {:id   Long
                :name String})

(def pertti {:id 1 :name "Pertti"})

(def app-name (str (gensym)))

(facts "for a compojure-api app"
  (background
    (after :contents (swap! swagger/swagger dissoc app-name)))

  (defapi api
    (swagger-docs)
    (swaggered app-name
      (context "/api" []
        (GET* "/pertti" []
          :return User
          (ok pertti))
        (GET* "/user" []
          :return User
          :query  [user User]
          (ok user))
        (POST* "/user" []
          :return User
          :body   [user User]
          (ok user))
        (POST* "/user2" {user :body-params}
          :return User
          (ok user)))))

  (fact "GET*"
    (let [{:keys [body status]} (api (mock/request :get "/api/pertti"))
          body (cheshire/parse-string body true)]
      status => 200
      body => pertti))

  (fact "GET* with smart destructuring"
    (let [{:keys [body status]} (api (assoc (mock/request :get "/api/user") :query-params pertti))
          body (cheshire/parse-string body true)]
      status => 200
      body => pertti))

  (fact "POST* with smart destructuring"
    (let [{:keys [body status]} (api (assoc (mock/request :post "/api/user") :body-params pertti))
          body (cheshire/parse-string body true)]
      status => 200
      body => pertti))

  (fact "POST* with compojure destructuring"
    (let [{:keys [body status]} (api (assoc (mock/request :post "/api/user2") :body-params pertti))
          body (cheshire/parse-string body true)]
      status => 200
      body => pertti)))

