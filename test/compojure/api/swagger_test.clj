(ns compojure.api.swagger-test
  (:require [midje.sweet :refer :all]
            [compojure.core :refer :all]
            [compojure.api.swagger :refer :all]))

(fact "extracting compojure paths"
  (fact "all compojure.core macros are interpreted"
    (extract-routes
      '(context "/a" []
         (routes
           (context "/b" []
             (let-routes []
               (GET     "/c" [] identity)
               (POST    "/d" [] identity)
               (PUT     "/e" [] identity)
               (DELETE  "/f" [] identity)
               (OPTIONS "/g" [] identity)
               (PATCH   "/h" [] identity)))
           (context "/:i/:j" []
             (GET "/k/:l/m/:n" [] identity))))) => [{:method :get
                                                     :uri ["/a/b/c"]}
                                                    {:method :post
                                                     :uri ["/a/b/d"]}
                                                    {:method :put
                                                     :uri ["/a/b/e"]}
                                                    {:method :delete
                                                     :uri ["/a/b/f"]}
                                                    {:method :options
                                                     :uri ["/a/b/g"]}
                                                    {:method :patch
                                                     :uri ["/a/b/h"]}
                                                    {:method :get
                                                     :uri ["/a/" :i "/" :j "/k/" :l "/m/" :n]}])
  (fact "runtime code in route is ignored"
    (extract-routes
      '(context "/api" []
         (if true
           (GET "/true" [] identity)
           (PUT "/false" [] identity)))) => [{:method :get
                                              :uri ["/api/true"]}
                                             {:method :put
                                              :uri ["/api/false"]}])
  (fact "macros are expanded"
    (defmacro optional-routes [p & body] (when p `(routes ~@body)))
    (extract-routes
      '(context "/api" []
         (optional-routes true
           (GET "/true" [] identity))
         (optional-routes false
           (PUT "/false" [] identity)))) => [{:method :get
                                              :uri ["/api/true"]}]))

(fact "path-to-index"
  (path-to-index "/")    => "/index.html"
  (path-to-index "/ui")  => "/ui/index.html"
  (path-to-index "/ui/") => "/ui/index.html")

(fact "create-uri"
  (create-uri "/api/:version/users/:id") => ["/api/" :version "/users/" :id])

(fact "swagger-info"
  (first
    (swagger-info
      '(context "/api"
         (GET "/user/:id" [] identity)))) => {:models []
                                              :routes [{:method :get
                                                        :uri ["/api/user/" :id]}]})
