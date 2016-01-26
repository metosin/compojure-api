(ns compojure.api.routes-test
  (:require [midje.sweet :refer :all]
            [compojure.api.sweet :refer :all]
            [compojure.api.routes :as routes]
            [ring.util.http-response :refer :all]
            [ring.util.http-predicates :refer :all]
            [compojure.api.test-utils :refer :all]
            [schema.core :as s])
  (:import [java.security SecureRandom]
           [org.joda.time LocalDate]
           [com.fasterxml.jackson.core JsonGenerationException]))

(facts "path-string"

  (fact "missing path parameter"
    (#'routes/path-string "/api/:kikka" {})
    => (throws IllegalArgumentException))

  (fact "missing serialization"
    (#'routes/path-string "/api/:kikka" {:kikka (SecureRandom.)})
    => (throws JsonGenerationException))

  (fact "happy path"
    (#'routes/path-string "/a/:b/:c/d/:e/f" {:b (LocalDate/parse "2015-05-22")
                                             :c 12345
                                             :e :kikka})
    => "/a/2015-05-22/12345/d/kikka/f"))

(fact "string-path-parameters"
  (#'routes/string-path-parameters "/:foo.json") => {:foo String})

(facts "nested routes"
  (let [mw (fn [handler] (fn [request] (handler request)))
        more-routes (fn [version]
                      (routes
                        (GET "/more" []
                          (ok {:message version}))))
        routes (context "/api/:version" []
                 :path-params [version :- String]
                 (GET "/ping" []
                   (ok {:message (str "pong - " version)}))
                 (POST "/ping" []
                   (ok {:message (str "pong - " version)}))
                 (middleware [mw]
                   (GET "/hello" []
                     :return {:message String}
                     :summary "cool ping"
                     :query-params [name :- String]
                     (ok {:message (str "Hello, " name)}))
                   (more-routes version)))
        app (api
              (swagger-docs)
              routes)]

    (fact "all routes can be invoked"
      (let [[status body] (get* app "/api/v1/hello" {:name "Tommi"})]
        status = 200
        body => {:message "Hello, Tommi"})

      (let [[status body] (get* app "/api/v1/ping")]
        status = 200
        body => {:message "pong - v1"})

      (let [[status body] (get* app "/api/v2/ping")]
        status = 200
        body => {:message "pong - v2"})

      (let [[status body] (get* app "/api/v3/more")]
        status => 200
        body => {:message "v3"}))

    (fact "routes can be extracted at runtime"
      (routes/get-routes app)
      => [["/swagger.json" :get {:x-no-doc true, :x-name :compojure.api.swagger/swagger}]
          ["/api/:version/ping" :get {:parameters {:path {:version String, s/Keyword s/Any}}}]
          ["/api/:version/ping" :post {:parameters {:path {:version String, s/Keyword s/Any}}}]
          ["/api/:version/hello" :get {:parameters {:query {:name String, s/Keyword s/Any}
                                                    :path {:version String, s/Keyword s/Any}}
                                       :responses {200 {:description "", :schema {:message String}}}
                                       :summary "cool ping"}]
          ["/api/:version/more" :get {:parameters {:path {:version String, s/Keyword s/Any}}}]])

    (fact "swagger-docs can be generated"
      (-> app get-spec :paths keys)
      => ["/api/{version}/ping"
          "/api/{version}/hello"
          "/api/{version}/more"])))

(fact "route merging"
  (routes/get-routes (routes (routes))) => []
  (routes/get-routes (routes (swagger-ui))) => []
  (routes/get-routes (routes (routes (GET "/ping" [] "pong")))) => [["/ping" :get {}]])

(fact "invalid route options"
  (let [r (routes (constantly nil))]

    (fact "ignore 'em all"
      (routes/get-routes r) => []
      (routes/get-routes r nil) => []
      (routes/get-routes r {:invalid-routes-fn nil}) => [])

    (fact "log warnings"
      (routes/get-routes r {:invalid-routes-fn routes/log-invalid-child-routes}) => []
      (provided
        (compojure.api.impl.logging/log! :warn irrelevant) => irrelevant :times 1))

    (fact "throw exception"
      (routes/get-routes r {:invalid-routes-fn routes/fail-on-invalid-child-routes})) => throws))

(fact "context routes with compojure destructuring"
  (let [app (context "/api" req
              (GET "/ping" [] (ok (:magic req))))]
    (app {:request-method :get :uri "/api/ping" :magic {:just "works"}}) => (contains {:body {:just "works"}})))
