(ns examples.vanilla-routes
  (:require [compojure.core :refer :all]
            [ring.util.http-response :refer :all]
            [compojure.api.core :refer :all]
            [compojure.api.common :refer [->Long]]
            [compojure.api.swagger :refer :all]
            [compojure.api.middleware :refer [api-middleware]]
            [examples.domain :refer :all]))

(defroutes app
  (with-middleware [api-middleware]
    (swagger-ui)
    (swagger-docs
      :title "Cool api"
      :description "Compojure Sample Web Api")
    (swaggered "sample"
      :description "sample api"
      (context "/api" []
        (GET "/pizzas" []
          (ok (get-pizzas)))
        (GET "/pizzas/:id" [id]
          (ok (get-pizza (->Long id))))
        (POST "/pizzas" {pizza :params}
          (ok (add! pizza)))
        (PUT "/pizzas" {pizza :params}
          (ok (update! pizza)))
        (DELETE "/pizzas/:id" [id]
          (ok (delete! (->Long id))))))))
