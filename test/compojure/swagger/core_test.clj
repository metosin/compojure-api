(ns compojure.swagger.core-test
  (:require [midje.sweet :refer :all]
            [ring.swagger.core :refer [->Route]]
            [compojure.core :refer :all]
            [compojure.swagger.core :refer :all]))

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
             (GET "/k/:l/m/:n" [] identity))))) => [(->Route :get ["/a/b/c"] {})
                                                    (->Route :post ["/a/b/d"] {})
                                                    (->Route :put ["/a/b/e"] {})
                                                    (->Route :delete ["/a/b/f"] {})
                                                    (->Route :options ["/a/b/g"] {})
                                                    (->Route :patch ["/a/b/h"] {})
                                                    (->Route :get ["/a/" :i "/" :j "/k/" :l "/m/" :n] {})])
  (fact "runtime code in route is ignored"
    (extract-routes
      '(context "/api" []
         (if true
           (GET "/true" [] identity)
           (PUT "/false" [] identity)))) => [(->Route :get ["/api/true"] {})
                                             (->Route :put ["/api/false"] {})])
  (fact "macros are expanded"
    (defmacro optional-routes [p & body] (when p `(routes ~@body)))
    (extract-routes
      '(context "/api" []
         (optional-routes true
           (GET "/true" [] identity))
         (optional-routes false
           (PUT "/false" [] identity)))) => [(->Route :get ["/api/true"] {})]))

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
                                              :routes [(->Route :get ["/api/user/" :id] {})]})
