(ns compojure.api.example.handler
  (:require [compojure.api.core :refer [defapi]]
            [compojure.api.swagger :refer :all]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.util.response :refer :all]
            [schema.core :as s]
            [compojure.api.schema :refer [defmodel optional]]
            [schema.macros :as sm]))

(defmodel Pizza {:id s/Int
                 :name s/String
                 (optional :description) s/String
                 :toppings [(s/enum :cheese :olives :ham :pepperoni :artichoke)]})

(defmodel quatro {:id 1
                  :name "Quatro"
                  :toppings [:cheese :olives :artichoke]})

(defapi api
  (swagger-docs "/api/docs"
    :title "Cool api"
    :description "Compojure Sample Web Api")
  (swaggered :sample
    :description "sample api"
    (context "/api" []
      (context "/pizza" []
        (^{:model Pizza} GET "/:id" [id] (response quatro))
        (POST "" [] (response {:a 1}))))))

(def app
  (routes
    api
    (route/resources "/")
    (route/not-found "not found")))
