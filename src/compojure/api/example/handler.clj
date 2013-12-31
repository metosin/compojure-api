(ns compojure.api.example.handler
  (:require [compojure.api.core :refer [defapi]]
            [compojure.api.swagger :refer :all]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.util.response :refer :all]
            [schema.core :as s]
            [compojure.api.schema :refer [defmodel optional]]
            [schema.macros :as sm]))

;;
;; Domain
;;

(defmodel Pizza {:id s/Int
                 :name s/String
                 (optional :description) s/String
                 :toppings [(s/enum :cheese :olives :ham :pepperoni :artichoke)]})

;;
;; Repository
;;

(def quatro {:id 1
             :name "Quatro"
             :toppings [:cheese :olives :artichoke]})

;;
;; Web Api
;;

(defapi api
  (swagger-docs "/api/docs"
    :title "Cool api"
    :description "Compojure Sample Web Api")
  (swaggered :sample
    :description "sample api"
    (context "/api" []
      (context "/pizza" []
        (^{:return Pizza} GET "/:id" [id] (response quatro))
        (POST "" [] (response quatro))))))

(def app
  (routes
    api
    (route/resources "/")
    (route/not-found "not found")))
