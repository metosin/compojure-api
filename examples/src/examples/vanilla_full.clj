(ns examples.vanilla-full
  (:require [compojure.core :refer :all]
            [ring.util.http-response :refer :all]
            [compojure.api.core :refer :all]
            [compojure.api.swagger :refer :all]
            [compojure.api.middleware :refer [api-middleware]]
            [examples.domain :refer :all]))

(defroutes app
  (middlewares [api-middleware]
    (swagger-ui)
    (swagger-docs
      :title "Cool api"
      :description "Compojure Sample Web Api")
    (swaggered "sample"
      :description "sample api"
      (context "/api" []
        (^{:return   Pizza
           :summary  "Gets all Pizzas"
           :nickname "getPizzas"} GET "/pizzas" [] (ok (get-pizzas)))
        (^{:return   Pizza
         :summary  "Gets a pizza"
         :nickname "getPizza"} GET "/pizzas/:id" [id] (ok (get-pizza (Long/parseLong id))))
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
           :nickname "deletePizza"} DELETE "/pizzas/:id" [id] (ok (delete! (Long/parseLong id))))))))
