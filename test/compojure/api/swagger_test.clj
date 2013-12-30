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
             (GET "/k/:l/m/:n" [] identity))))) => {"/a/b/c" {:method :get}
                                                    "/a/b/d" {:method :post}
                                                    "/a/b/e" {:method :put}
                                                    "/a/b/f" {:method :delete}
                                                    "/a/b/g" {:method :options}
                                                    "/a/b/h" {:method :patch}
                                                    "/a/:i/:j/k/:l/m/:n" {:method :get}})
  (fact "runtime code in route is ignored"
    (extract-routes
      '(context "/api" []
         (if true
           (GET "/true" [] identity)
           (PUT "/false" [] identity)))) => {"/api/true" {:method :get}
                                             "/api/false" {:method :put}})
  (fact "macros are expanded"
    (defmacro optional-routes [p & body] (when p `(routes ~@body)))
    (extract-routes
      '(context "/api" []
         (optional-routes true
           (GET "/true" [] identity))
         (optional-routes false
           (PUT "/false" [] identity)))) => {"/api/true" {:method :get}}))
