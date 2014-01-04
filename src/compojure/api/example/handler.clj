(ns compojure.api.example.handler
  (:require [compojure.api.core :refer [defapi with-middleware public-resources]]
            [compojure.api.swagger :refer :all]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.util.response :refer :all]
            [schema.core :as s]
            [compojure.api.schema :refer [defmodel optional]]
            [compojure.api.common :refer :all]
            [compojure.api.dsl :refer :all]
            [schema.macros :as sm]))

;; Domain

(defmodel Pizza {:id s/Int
                 :name s/String
                 (optional :description) s/String
                 :toppings [(s/enum "cheese" "olives" "ham" "pepperoni" "habanero")]})

(defmodel NewPizza (dissoc Pizza :id))

;; Repository

(defonce id-seq (atom 0))
(defonce pizzas (atom (array-map)))

(defn get-pizza [id] (@pizzas id))
(defn get-pizzas [] (-> pizzas deref vals reverse))
(defn delete! [id] (swap! pizzas dissoc id))

(defn add! [pizza]
  (let [id (swap! id-seq inc)]
    (swap! pizzas assoc id
      (s/validate Pizza (assoc pizza :id id)))
    (get-pizza id)))

(defn update! [pizza]
  (swap! pizzas assoc (:id pizza)
    (s/validate Pizza pizza)))

;; Data

(when (empty? @pizzas)
  (add! {:name "Frutti" :toppings ["cheese" "olives"]})
  (add! {:name "Il Diablo" :toppings ["ham" "habanero"]}))

;; Web Api

(defapi app
  (with-middleware [public-resources]
    (swagger-docs "/api/docs"
      :title "Cool api"
      :description "Compojure Sample Web Api")
    (swaggered :sample
      :description "sample api"
      (context "/api" []
        (context "/resources" []
          (^{:return   Pizza
             :summary  "Gets all Pizzas"
             :nickname "getPizzas"} GET "/pizzas" [] (response (get-pizzas)))
          (^{:return   Pizza
             :summary  "Gets a pizza"
             :nickname "getPizza"} GET "/pizzas/:id" [id] (response (get-pizza (java.lang.Integer/parseInt id))))
          (^{:return   Pizza
             :parameters [{:paramType   :body
                           :name        "pizza"
                           :description "new pizza"
                           :required    true
                           :type        NewPizza}]
             :body     NewPizza
             :summary  "Adds a pizza"
             :nickname "addPizza"} POST "/pizzas" {pizza :params} (response (add! pizza)))
          (^{:return   Pizza
             :parameters [{:paramType   :body
                           :name        "body"
                           :description "updated pizza"
                           :required    true
                           :type        Pizza}]
             :body     Pizza
             :summary  "Updates a pizza"
             :nickname "updatePizza"} PUT "/pizzas" {pizza :params} (response (update! pizza)))
          (^{:return   Pizza
             :summary  "Deletes a Pizza"
             :nickname "deletePizza"} DELETE "/pizzas/:id" [id] (delete! id)))))))
