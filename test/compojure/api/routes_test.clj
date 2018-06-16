(ns compojure.api.routes-test
  (:require [midje.sweet :refer :all]
            [compojure.api.sweet :refer :all]
            [compojure.api.core :refer [route-middleware]]
            [compojure.api.routes :as routes]
            [ring.util.http-response :refer :all]
            [ring.util.http-predicates :refer :all]
            [compojure.api.test-utils :refer :all]
            [schema.core :as s]
            [jsonista.core :as j])
  (:import (org.joda.time LocalDate)
           (clojure.lang ExceptionInfo)))

(facts "path-string"

  (fact "missing path parameter"
    (#'routes/path-string muuntaja "/api/:kikka" {})
    => (throws IllegalArgumentException))

  (fact "missing serialization"
    (#'routes/path-string muuntaja "/api/:kikka" {:kikka (reify Comparable)})
    => (throws ExceptionInfo #"Malformed application/json"))

  (fact "happy path"
    (#'routes/path-string muuntaja "/a/:b/:c/d/:e/f" {:b (LocalDate/parse "2015-05-22")
                                                      :c 12345
                                                      :e :kikka})
    => "/a/2015-05-22/12345/d/kikka/f"))

(fact "string-path-parameters"
  (#'routes/string-path-parameters "/:foo.json") => {:foo String})

(facts "nested routes"
  (let [mw (fn [handler]
             (fn ([request] (handler request))
               ([request raise respond] (handler request raise respond))))
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
                 (ANY "/foo" []
                   (ok {:message (str "bar - " version)}))
                 (route-middleware [mw]
                   (GET "/hello" []
                     :return {:message String}
                     :summary "cool ping"
                     :query-params [name :- String]
                     (ok {:message (str "Hello, " name)}))
                   (more-routes version)))
        app (api
              (swagger-routes)
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
      => [["/swagger.json" :get {:no-doc true
                                 :coercion :schema
                                 :name :compojure.api.swagger/swagger
                                 :public {:x-name :compojure.api.swagger/swagger}}]
          ["/api/:version/ping" :get {:coercion :schema
                                      :public {:parameters {:path {:version String, s/Keyword s/Any}}}}]
          ["/api/:version/ping" :post {:coercion :schema
                                       :public {:parameters {:path {:version String, s/Keyword s/Any}}}}]
          ;; 'ANY' expansion
          ["/api/:version/foo" :get {:coercion :schema
                                     :public {:parameters {:path {:version String, s/Keyword s/Any}}}}]
          ["/api/:version/foo" :patch {:coercion :schema
                                       :public {:parameters {:path {:version String, s/Keyword s/Any}}}}]
          ["/api/:version/foo" :delete {:coercion :schema
                                        :public {:parameters {:path {:version String, s/Keyword s/Any}}}}]
          ["/api/:version/foo" :head {:coercion :schema
                                      :public {:parameters {:path {:version String, s/Keyword s/Any}}}}]
          ["/api/:version/foo" :post {:coercion :schema
                                      :public {:parameters {:path {:version String, s/Keyword s/Any}}}}]
          ["/api/:version/foo" :options {:coercion :schema
                                         :public {:parameters {:path {:version String, s/Keyword s/Any}}}}]
          ["/api/:version/foo" :put {:coercion :schema
                                     :public {:parameters {:path {:version String, s/Keyword s/Any}}}}]
          ;;
          ["/api/:version/hello" :get {:coercion :schema
                                       :public {:parameters {:query {:name String, s/Keyword s/Any}
                                                             :path {:version String, s/Keyword s/Any}}
                                                :responses {200 {:description "", :schema {:message String}}}
                                                :summary "cool ping"}}]
          ["/api/:version/more" :get {:coercion :schema
                                      :public {:parameters {:path {:version String, s/Keyword s/Any}}}}]])

    (fact "swagger-docs can be generated"
      (-> app get-spec :paths keys)
      => (just
           ["/api/{version}/ping"
            "/api/{version}/foo"
            "/api/{version}/hello"
            "/api/{version}/more"]
           :in-any-order))))

(def more-routes
  (routes
    (GET "/more" []
      (ok {:gary "moore"}))))

(facts "following var-routes, #219"
  (let [routes (context "/api" [] #'more-routes)]
    (routes/get-routes routes) => [["/api/more" :get {:static-context? true}]]))

(facts "dynamic routes"
  (let [more-routes (fn [version]
                      (GET (str "/" version) []
                        (ok {:message version})))
        routes (context "/api/:version" []
                 :path-params [version :- String]
                 (more-routes version))
        app (api
              (swagger-routes)
              routes)]

    (fact "all routes can be invoked"
      (let [[status body] (get* app "/api/v3/v3")]
        status => 200
        body => {:message "v3"})

      (let [[status body] (get* app "/api/v6/v6")]
        status => 200
        body => {:message "v6"}))

    (fact "routes can be extracted at runtime"
      (routes/get-routes app)
      => [["/swagger.json" :get {:no-doc true,
                                 :coercion :schema
                                 :name :compojure.api.swagger/swagger
                                 :public {:x-name :compojure.api.swagger/swagger}}]
          ["/api/:version/[]" :get {:coercion :schema
                                    :public {:parameters {:path {:version String, s/Keyword s/Any}}}}]])

    (fact "swagger-docs can be generated"
      (-> app get-spec :paths keys)
      => ["/api/{version}/[]"])))

(fact "route merging"
  (routes/get-routes (routes (routes))) => []
  (routes/get-routes (routes (swagger-routes {:spec nil}))) => []
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

(fact "dynamic context routes"
  (let [endpoint? (atom true)
        app (context "/api" []
              :dynamic true
              (when @endpoint?
                (GET "/ping" [] (ok "pong"))))]
    (fact "the endpoint exists"
      (app {:request-method :get :uri "/api/ping"}) => (contains {:body "pong"}))

    (reset! endpoint? false)
    (fact "the endpoint does not exist"
      (app {:request-method :get :uri "/api/ping"}) => nil)))

(fact "listing static context routes"
  (let [app (routes
              (context "/static" []
                (GET "/ping" [] (ok "pong")))
              (context "/dynamic" req
                (GET "/ping" [] (ok "pong"))))]
    (routes/get-static-context-routes app)
    => [["/static/ping" :get {:static-context? true}]]))
