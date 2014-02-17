(ns compojure.api.example.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [compojure.api.example.domain :refer :all]))

(defroutes ping
  (GET* "/ping" [] (ok {:ping "pong"})))

(defapi app
  (swagger-ui)
  (swagger-docs
    :title "Cool api"
    :description "Compojure Sample Web Api")
  (swaggered "sample"
    :description "sample api"
    (context "/api" []
      ping
      (GET* "/pizzas" []
        :return   [Pizza]
        :summary  "Gets all Pizzas"
        :nickname "getPizzas"
        (ok (get-pizzas)))
      (GET* "/pizzas/:id" [id]
        :return   Pizza
        :summary  "Gets a pizza"
        :nickname "getPizza"
        (ok (get-pizza (->Long id))))
      (POST* "/pizzas" []
        :return   Pizza
        :body     [pizza NewPizza {:description "new pizza"}]
        :summary  "Adds a pizza"
        :nickname "addPizza"
        (ok (add! pizza)))
      (PUT* "/pizzas" []
        :return   Pizza
        :body     [pizza Pizza]
        :summary  "Updates a pizza"
        :nickname "updatePizza"
        (ok (update! pizza)))
      (DELETE* "/pizzas/:id" [id]
        :return   Pizza
        :summary  "Deletes a Pizza"
        :nickname "deletePizza"
        (ok (delete! (->Long id)))))))
