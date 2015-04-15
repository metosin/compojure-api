(ns compojure.api.swagger-ordering-test
  (:require [compojure.api.routes :as routes]
            [compojure.api.sweet :refer :all]
            [midje.sweet :refer :all]))

(def app-name "default")

(facts "with 9+ routes"

  (defapi api
    (swaggered app-name
      :description "sample api"
      (context "/a" []
        (GET* "/1" [] identity)
        (GET* "/2" [] identity)
        (GET* "/3" [] identity)
        (context "/b" []
          (GET* "/4" [] identity)
          (GET* "/5" [] identity))
        (context "/c" []
          (GET* "/6" [] identity)
          (GET* "/7" [] identity)
          (GET* "/8" [] identity)
          (GET* "/9" [] identity)
          (GET* "/10" [] identity)))))

  (fact "swagger-api order is maintained"
    (->> app-name
         ((routes/get-routes))
         :routes
         (map :uri))  => ["/a/1"
                          "/a/2"
                          "/a/3"
                          "/a/b/4"
                          "/a/b/5"
                          "/a/c/6"
                          "/a/c/7"
                          "/a/c/8"
                          "/a/c/9"
                          "/a/c/10"]))
