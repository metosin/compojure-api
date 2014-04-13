(ns examples.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [examples.dates :refer :all]
            [examples.domain :refer :all]))

(defroutes* ping-route
  (GET* "/ping" [] (ok {:ping "pong"})))

(defapi app
  (swagger-ui "/")
  (swagger-docs "/api/api-docs"
    :title "Cool api"
    :apiVersion "1.0.0"
    :description "Compojure Sample Web Api"
    :termsOfServiceUrl "http://www.metosin.fi"
    :contact "pizza@example.com"
    :license "Eclipse 1.0"
    :licenseUrl "http://www.eclipse.org/legal/epl-v10.html")
  (swaggered "ping"
    :description "Ping api"
    ping-route)
  (swaggered "models"
    :description "Model mappings"
    (GET* "/echo" []
      :return QueryParams
      :query  [query QueryParams]
      (ok query))
    (GET* "/sum" []
      :query-params [x :- Long y :- Long]
      :summary      "sums x & y query-parameters"
      (ok {:total (+ x y)}))
    (GET* "/times/:x/:y" []
      :path-params [x :- Long y :- Long]
      :summary      "multiplies x & y path-parameters"
      (ok {:total (* x y)}))
    (POST* "/customer" []
      :return Customer
      :body   [customer Customer]
      (ok customer)))
  (swaggered "dates"
    :description "Roundrobin of Dates"
    date-routes)
  (swaggered "pizza"
    :description "Pizza api"
    (context "/api" []
      (GET* "/pizzas" []
        :return   [Pizza]
        :summary  "Gets all Pizzas"
        :nickname "getPizzas"
        (ok (get-pizzas)))
      (GET* "/pizzas/:id" []
        :path-params [id :- Long]
        :return   Pizza
        :summary  "Gets a pizza"
        :nickname "getPizza"
        (ok (get-pizza id)))
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
      (DELETE* "/pizzas/:id" []
        :path-params [id :- Long]
        :return   Pizza
        :summary  "Deletes a Pizza"
        :nickname "deletePizza"
        (ok (delete! id))))))
