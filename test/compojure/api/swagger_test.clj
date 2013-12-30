(ns compojure.api.swagger-test
  (:require [midje.sweet :refer :all]
            [compojure.core :refer :all]
            [compojure.api.swagger :refer :all]))

(fact "extract-path-parameters"
  (extract-path-parameters "/api/:kikka/:kakka/:kukka") => [:kikka :kakka :kukka])

(fact "swagger-path"
  (swagger-path "/api/:kikka/:kakka/:kukka") => "/api/{kikka}/{kakka}/{kukka}")

(fact "extract-parameters"
  (fact "works with even number of values before body"
    (extract-parameters [:kikka 1 :kakka 2 :kukka 3 '(+ 1 1)]) => [{:kikka 1 :kakka 2 :kukka 3} '((+ 1 1))])
  (fact "fails with uneven number of values before body"
    (extract-parameters [:kikka '(+ 1 1)]) => (throws Exception)))

(fact "extracting compojure paths"
  (fact "all compojure.core macros are interpreted"
    (get-routes
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
             (GET "/k/:l/m/:n" [] identity))))) => {"/a/b/c" :get
                                                    "/a/b/d" :post
                                                    "/a/b/e" :put
                                                    "/a/b/f" :delete
                                                    "/a/b/g" :options
                                                    "/a/b/h" :patch
                                                    "/a/:i/:j/k/:l/m/:n" :get})
  (fact "runtime code in route is ignored"
    (get-routes
      '(context "/api" []
         (if true
           (GET "/true" [] identity)
           (PUT "/false" [] identity)))) => {"/api/true" :get
                                             "/api/false" :put})
  (fact "macros are expanded"
    (defmacro optional-routes [p & body] (when p `(routes ~@body)))
    (get-routes
      '(context "/api" []
         (optional-routes true
           (GET "/true" [] identity))
         (optional-routes false
           (PUT "/false" [] identity)))) => {"/api/true" :get}))
