(ns compojure.api.source-linking-test
  (:require [compojure.api.sweet :refer :all]
            [compojure.api.test-domain :as domain]
            [compojure.api.test-utils :refer :all]
            [midje.sweet :refer :all]
            [ring.util.http-response :refer :all]))

(fact "external deep schemas"
  (defapi api
    (swagger-docs)
    (POST* "/pizza" []
      ;:return domain/Pizza
      :body [body domain/Pizza]
      (ok))))
