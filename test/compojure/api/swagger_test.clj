(ns compojure.api.swagger-test
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [compojure.api.swagger :as swagger]
            compojure.core
            [compojure.api.test-utils :refer :all]
            [clojure.test :refer [deftest is testing]]))

(defmacro optional-routes [p & body] (when p `(routes ~@body)))
(defmacro GET+ [p & body] `(GET ~(str "/xxx" p) ~@body))

(deftest extracting-compojure-paths-test

  (testing "all compojure.api.core macros are interpreted"
    (let [app (context "/a" []
                (routes
                  (context "/b" a
                    (let-routes []
                      (GET "/c" [] identity)
                      (POST "/d" [] identity)
                      (PUT "/e" [] identity)
                      (DELETE "/f" [] identity)
                      (OPTIONS "/g" [] identity)
                      (PATCH "/h" [] identity)))
                  (context "/:i/:j" []
                    (GET "/k/:l/m/:n" [] identity))))]

      (is (= (extract-paths app)
             {"/a/b/c" {:get {}}
              "/a/b/d" {:post {}}
              "/a/b/e" {:put {}}
              "/a/b/f" {:delete {}}
              "/a/b/g" {:options {}}
              "/a/b/h" {:patch {}}
              "/a/:i/:j/k/:l/m/:n" {:get {:parameters {:path {:i String
                                                              :j String
                                                              :l String
                                                              :n String}}}}}))))

  (testing "runtime code in route is NOT ignored"
    (is (= (extract-paths
             (context "/api" []
                      (if false
                        (GET "/true" [] identity)
                        (PUT "/false" [] identity))))
           {"/api/false" {:put {}}})))

  (testing "route-macros are expanded"
    (is (= (extract-paths
             (context "/api" []
                      (optional-routes true (GET "/true" [] identity))
                      (optional-routes false (PUT "/false" [] identity))))
           {"/api/true" {:get {}}})))

  (testing "endpoint-macros are expanded"
    (is (= (extract-paths
             (context "/api" []
                      (GET+ "/true" [] identity)))
           {"/api/xxx/true" {:get {}}})))

  (testing "Vanilla Compojure defroutes are NOT followed"
    (compojure.core/defroutes even-more-routes (GET "/even" [] identity))
    (compojure.core/defroutes more-routes (context "/more" [] even-more-routes))
    (is (= (extract-paths
             (context "/api" []
                      (GET "/true" [] identity)
                      more-routes))
           {"/api/true" {:get {}}})))

  (testing "Compojure Api defroutes and def routes are followed"
    (def even-more-routes (GET "/even" [] identity))
    (defroutes more-routes (context "/more" [] even-more-routes))
    (is (= (extract-paths
             (context "/api" []
                      (GET "/true" [] identity)
                      more-routes))
           {"/api/true" {:get {}}
            "/api/more/even" {:get {}}})))

  (testing "Parameter regular expressions are discarded"
    (is (= (extract-paths
             (context "/api" []
                      (GET ["/:param" :param #"[a-z]+"] [] identity)))
           {"/api/:param" {:get {:parameters {:path {:param String}}}}}))))

(deftest context-meta-data-test-1
  (is (= (extract-paths
           (context "/api/:id" []
                    :summary "top-summary"
                    :path-params [id :- String]
                    :tags [:kiss]
                    (GET "/kikka" []
                         identity)
                    (context "/ipa" []
                             :summary "mid-summary"
                             :tags [:wasp]
                             (GET "/kukka/:kukka" []
                                  :summary "bottom-summary"
                                  :path-params [kukka :- String]
                                  :tags [:venom])
                             (GET "/kakka" []
                                  identity))))

         {"/api/:id/kikka" {:get {:summary "top-summary"
                                  :tags #{:kiss}
                                  :parameters {:path {:id String}}}}
          "/api/:id/ipa/kukka/:kukka" {:get {:summary "bottom-summary"
                                             :tags #{:venom}
                                             :parameters {:path {:id String
                                                                 :kukka String}}}}
          "/api/:id/ipa/kakka" {:get {:summary "mid-summary"
                                      :tags #{:wasp}
                                      :parameters {:path {:id String}}}}})))

(deftest duplicate-context-merge-test
  (let [app (routes
              (context "/api" []
                :tags [:kiss]
                (GET "/kakka" []
                  identity))
              (context "/api" []
                :tags [:kiss]
                (GET "/kukka" []
                  identity)))]
    (is (= (extract-paths app)
           {"/api/kukka" {:get {:tags #{:kiss}}}
            "/api/kakka" {:get {:tags #{:kiss}}}}))))

(def r1
  (GET "/:id" []
    :path-params [id :- s/Str]
    identity))
(def r2
  (GET "/kukka/:id" []
    :path-params [id :- Long]
    identity))

(deftest defined-routes-path-params-test
  (is (= (extract-paths (routes r1 r2))
         {"/:id" {:get {:parameters {:path {:id String}}}}
          "/kukka/:id" {:get {:parameters {:path {:id Long}}}}})))

;;FIXME is this a duplicate of context-meta-data-test-1?
(deftest context-meta-data-test-2
  (is (= (extract-paths
           (context "/api/:id" []
                    :summary "top-summary"
                    :path-params [id :- String]
                    :tags [:kiss]
                    (GET "/kikka" []
                         identity)
                    (context "/ipa" []
                             :summary "mid-summary"
                             :tags [:wasp]
                             (GET "/kukka/:kukka" []
                                  :summary "bottom-summary"
                                  :path-params [kukka :- String]
                                  :tags [:venom])
                             (GET "/kakka" []
                                  identity))))

         {"/api/:id/kikka" {:get {:summary "top-summary"
                                  :tags #{:kiss}
                                  :parameters {:path {:id String}}}}
          "/api/:id/ipa/kukka/:kukka" {:get {:summary "bottom-summary"
                                             :tags #{:venom}
                                             :parameters {:path {:id String
                                                                 :kukka String}}}}
          "/api/:id/ipa/kakka" {:get {:summary "mid-summary"
                                      :tags #{:wasp}
                                      :parameters {:path {:id String}}}}})))

(deftest path-params-followed-by-an-extension-test
  (is (= (extract-paths
           (GET "/:foo.json" []
                :path-params [foo :- String]
                identity))
         {"/:foo.json" {:get {:parameters {:path {:foo String}}}}})))

(deftest swagger-routes-basePath-test
  (testing "swagger-routes basePath can be changed"
    (doseq [[?given-options ?expected-swagger-docs-path ?expected-base-path :as test-case]
            [[{} "/swagger.json" "/" {:data {:basePath "/app"}} "/app/swagger.json" "/app"]
             [{:data {:basePath "/app"} :options {:ui {:swagger-docs "/imaginary.json"}}} "/imaginary.json" "/app"]]]
      (testing (pr-str test-case)
        (let [app (api {} (swagger-routes ?given-options))]
          (is (= (->
                   (get* app "/swagger.json")
                   (nth 1)
                   :basePath)
                 ?expected-base-path))
          (is (= (nth (raw-get* app "/conf.js") 1)
                 (str "window.API_CONF = {\"url\":\"" ?expected-swagger-docs-path "\"};"))))))))

;;"change of contract in 1.2.0 with swagger-docs % swagger-ui"
(deftest change-1-2-0-swagger-docs-ui-test
  (testing "swagger-ui"
    (is (thrown? AssertionError (swagger/swagger-ui "/path"))))
  (testing "swagger-docs"
    (is (thrown? AssertionError (swagger/swagger-docs "/path")))))
