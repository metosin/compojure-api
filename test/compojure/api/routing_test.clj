(ns compojure.api.routing-test
  (:require [midje.sweet :refer :all]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ring.util.http-predicates :refer :all]
            [compojure.api.test-utils :refer :all]
            [compojure.api.routing :as r]
            [schema.core :as s]))

(facts "nested routes"
  (let [middleware (fn [handler] (fn [request] (handler request)))
        more-routes (fn [version]
                      (routes*
                        (GET* "/more" []
                          (ok {:message version}))))
        routes (context* "/api/:version" []
                 :path-params [version :- String]
                 (GET* "/ping" []
                   (ok {:message (str "pong - " version)}))
                 (POST* "/ping" []
                   (ok {:message (str "pong - " version)}))
                 (middlewares [middleware]
                   (GET* "/hello" []
                     :return {:message String}
                     :summary "cool ping"
                     :query-params [name :- String]
                     (ok {:message (str "Hello, " name)}))
                   (more-routes version)))
        app (api routes)]

    (fact "all routes can be invoked"
      (let [[status body] (get* app "/api/v1/hello" {:name "Tommi"})]
        status = 200
        body => {:message "Hello, Tommi"})

      (let [[status body] (get* app "/api/v1/ping")]
        status = 200
        body => {:message "pong - v1"})

      (let [[status body] (get* app "/api/v3/more")]
        status => 200
        body => {:message "v3"}))

    (fact "routes can be extracted at runtime"
      (r/get-routes app)
      => [["/api/:version/ping" :get {:parameters {:path {:version String, s/Keyword s/Any}}}]
          ["/api/:version/ping" :post {:parameters {:path {:version String, s/Keyword s/Any}}}]
          ["/api/:version/hello" :get {:parameters {:query {:name String, s/Keyword s/Any}
                                                    :path {:version String, s/Keyword s/Any}}
                                       :responses {200 {:description "", :schema {:message String}}}
                                       :summary "cool ping"}]
          ["/api/:version/more" :get {:parameters {:path {:version String, s/Keyword s/Any}}}]])))
