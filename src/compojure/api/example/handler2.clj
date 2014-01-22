(ns compojure.api.example.handler2
  (:require [compojure.core :refer :all]
            [compojure.api.core :refer [defapi with-middleware public-resources]]
            [compojure.api.swagger :refer :all]
            [compojure.api.example.domain :refer :all]
            [compojure.api.dsl :refer :all]))

;; Web Api

(defapi app
  (with-middleware [public-resources]
    (swagger-docs "/api/docs"
      :title "Cool api"
      :description "Compojure Sample Web Api")
    (swaggered :sample
      :description "sample api"
      (context "/api" []
        (^{:return   Pizza
           :summary  "Gets all Pizzas"
           :nickname "getPizzas"} GET "/pizzas" [] (ok (get-pizzas)))
        (^{:return   Pizza
           :summary  "Gets a pizza"
           :nickname "getPizza"} GET "/pizzas/:id" [id] (ok (get-pizza (->Long id))))
        (^{:return   Pizza
           :parameters [{:paramType   :body
                         :name        "pizza"
                         :description "new pizza"
                         :required    true
                         :type        NewPizza}]
           :body     NewPizza
           :summary  "Adds a pizza"
           :nickname "addPizza"} POST "/pizzas" {pizza :params} (ok (add! pizza)))
        (^{:return   Pizza
           :parameters [{:paramType   :body
                         :name        "body"
                         :description "updated pizza"
                         :required    true
                         :type        Pizza}]
           :body     Pizza
           :summary  "Updates a pizza"
           :nickname "updatePizza"} PUT "/pizzas" {pizza :params} (ok (update! pizza)))
        (^{:return   Pizza
           :summary  "Deletes a Pizza"
           :nickname "deletePizza"} DELETE "/pizzas/:id" [id] (ok (delete! (->Long id))))))))
