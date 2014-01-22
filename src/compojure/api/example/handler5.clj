(ns compojure.api.example.handler
  (:require [compojure.core :refer :all]
            [compojure.api.core :refer :all]
            [compojure.api.swagger :refer :all]
            [compojure.api.middleware :refer [public-resources]]
            [compojure.api.example.domain :refer :all]))

(defapi app
  (with-middleware [public-resources]
    (swagger-docs "/api/docs"
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
