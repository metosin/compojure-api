(ns examples.vanilla-routes
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
        (context "/pizzas" []
          (GET "/" []
            (ok (get-pizzas)))
          (GET "/:id" [id]
            (ok (get-pizza (Long/parseLong id))))
          (POST "/" {pizza :params}
            (ok (add! pizza)))
          (PUT "/" {pizza :params}
            (ok (update! pizza)))
          (DELETE "/:id" [id]
            (ok (delete! (Long/parseLong id)))))))))
