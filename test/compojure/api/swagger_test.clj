(ns compojure.api.swagger-test
  (:require [midje.sweet :refer :all]
            [compojure.core :refer :all]
            [compojure.api.core :refer :all]
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
             (GET "/k/:l/m/:n" [] identity)))))

    => [{:method :get
         :uri "/a/b/c"}
        {:method :post
         :uri "/a/b/d"}
        {:method :put
         :uri "/a/b/e"}
        {:method :delete
         :uri "/a/b/f"}
        {:method :options
         :uri "/a/b/g"}
        {:method :patch
         :uri "/a/b/h"}
        {:method :get
         :uri "/a/:i/:j/k/:l/m/:n"
         :metadata {:parameters [{:type :path
                                  :model {:i String
                                          :j String
                                          :l String
                                          :n String}}]}}])

  (fact "runtime code in route is ignored"
    (extract-routes
      '(context "/api" []
         (if true
           (GET "/true" [] identity)
           (PUT "/false" [] identity)))) => [{:method :get
                                              :uri "/api/true"}
                                             {:method :put
                                              :uri "/api/false"}])

  (fact "macros are expanded"
    (defmacro optional-routes [p & body] (when p `(routes ~@body)))
    (extract-routes
      '(context "/api" []
         (optional-routes true
           (GET "/true" [] identity))
         (optional-routes false
           (PUT "/false" [] identity)))) => [{:method :get
                                              :uri "/api/true"}])

  (fact "Vanilla Compojure defroutes are NOT followed"
    (defroutes even-more-routes (GET "/even" [] identity))
    (defroutes more-routes (context "/more" [] even-more-routes))
    (extract-routes
      '(context "/api" []
         (GET "/true" [] identity)
         more-routes)) => [{:method :get
                            :uri "/api/true"}])

  (fact "Compojure Api defroutes _ARE_ followed"
    (defroutes* even-more-routes* (GET "/even" [] identity))
    (defroutes* more-routes* (context "/more" [] even-more-routes*))
    (extract-routes
      '(context "/api" []
         (GET "/true" [] identity)
         more-routes*)) => [{:method :get
                            :uri "/api/true"}
                           {:method :get
                            :uri "/api/more/even"}])

  (fact "Parameter regular expressions are discarded"
    (extract-routes '(context "/api" []
                       (GET ["/:param" :param #"[a-z]+"] [] identity)))

    => [{:method :get
         :uri "/api/:param"
         :metadata {:parameters [{:type :path
                                  :model {:param String}}]}}]))

(defn fake-servlet-context [context]
 (proxy [javax.servlet.ServletContext] []
   (getContextPath [] context)))

(fact "path-to-index"
  (path-to-index {} "/")    => "/index.html"
  (path-to-index {} "/ui")  => "/ui/index.html"
  (path-to-index {} "/ui/") => "/ui/index.html"
  (path-to-index {:servlet-context (fake-servlet-context "/kikka")} "/ui") => "/kikka/ui/index.html")

(facts "swagger-info"

  (fact "with keyword-parameters"
    (first
      (swagger-info
        '(:title ..title..
          :description ..description..
          :routes ..overridded..
          (context "/api"
            (GET "/user/:id" [] identity)))))

    => {:title  ..title..
        :description ..description..
        :routes [{:method :get
                  :uri "/api/user/:id"
                  :metadata {:parameters [{:type :path
                                           :model {:id String}}]}}]})

  (fact "with map-parameters"
    (first
      (swagger-info
        '({:title ..title..
           :description ..description..
           :routes ..overridded..}
          (context "/api"
            (GET "/user/:id" [] identity)))))

    => {:title  ..title..
        :description ..description..
        :routes [{:method :get
                  :uri "/api/user/:id"
                  :metadata {:parameters [{:type :path
                                           :model {:id String}}]}}]}))
