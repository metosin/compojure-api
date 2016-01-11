(ns compojure.api.swagger-ordering-test
  (:require [midje.sweet :refer :all]
            [compojure.api.sweet :refer :all]
            [compojure.api.test-utils :refer :all]))

(def more-routes
  (routes*
    (GET* "/6" [] identity)
    (GET* "/7" [] identity)
    (GET* "/8" [] identity)))

(facts "with 10+ routes"
  (let [app (api
              (context* "/a" []
                (GET* "/1" [] identity)
                (GET* "/2" [] identity)
                (GET* "/3" [] identity)
                (context* "/b" []
                  (GET* "/4" [] identity)
                  (GET* "/5" [] identity))
                (context* "/c" []
                  more-routes
                  (GET* "/9" [] identity)
                  (GET* "/10" [] identity))))]

    (fact "swagger-api order is maintained"
      (keys (extract-paths app)) => ["/a/1"
                                     "/a/2"
                                     "/a/3"
                                     "/a/b/4"
                                     "/a/b/5"
                                     "/a/c/6"
                                     "/a/c/7"
                                     "/a/c/8"
                                     "/a/c/9"
                                     "/a/c/10"])))
