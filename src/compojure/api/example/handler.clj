(ns compojure.api.example.handler
  (:require [compojure.api.core :refer [defapi]]
            [compojure.api.swagger :refer :all]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.util.response :refer :all]
            [schema.core :as s]
            [compojure.api.schema :refer [defmodel optional]]
            [compojure.api.common :refer :all]
            [schema.macros :as sm]))

;; Domain

(defmodel Pizza {:id s/Int
                 :name s/String
                 (optional :description) s/String
                 :toppings [(s/enum :cheese :olives :ham :pepperoni :artichoke :habanero)]})

;; Repository

(def id-seq (atom 0))
(def pizzas (atom (array-map)))

(defn add! [pizza]
  (let [id (swap! id-seq inc)]
    (swap! pizzas assoc id
      (s/validate Pizza (assoc pizza :id id)))))

(defn get-pizza [id] (@pizzas id))
(defn get-pizzas [] (-> pizzas deref vals reverse))
(defn delete! [id] (swap! pizzas dissoc id))
(defn update! [pizza]
  (swap! pizzas assoc (:id pizza)
    (s/validate Pizza pizza)))

;; Data

(add! {:name "Quatro" :toppings [:cheese :olives :artichoke]})
(add! {:name "Il Diablo" :toppings [:ham :habanero]})

;; Web Api

(defapi api
  (swagger-docs "/api/docs"
    :title "Cool api"
    :description "Compojure Sample Web Api")
  (swaggered :sample
    :description "sample api"
    (context "/api" []
      (context "/pizzas" []
        (^{:return Pizza
           :summary "Gets all Pizzas"
           :notes   "Fast and furious"
           :nickname "getPizzas"} GET "" [] (response (get-pizzas)))
        (^{:return Pizza} GET "/:id" [id] (response (get-pizza id)))
        (^{:return Pizza
           :body   Pizza} POST "" {pizza :params} (response (add! pizza)))
        (^{:return Pizza} PUT "" {pizza :params} (response (update! pizza)))
        (^{:return Pizza} DELETE "/:id" [id] (delete! id))))))

;; Ring App

(def app
  (routes
    api
    (route/resources "/")
    (route/not-found "not found")))
