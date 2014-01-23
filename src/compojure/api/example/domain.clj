(ns compojure.api.example.domain
  (:require [schema.core :as s]
            [ring.swagger.schema :refer :all]))

;; Domain

(defmodel Pizza {:id s/Int
                 :name s/Str
                 (s/optional-key :description) s/Str
                 :toppings [(s/enum :cheese :olives :ham :pepperoni :habanero)]})

(defmodel NewPizza (dissoc Pizza :id))

;; Repository

(defonce id-seq (atom 0))
(defonce pizzas (atom (array-map)))

(defn get-pizza [id] (@pizzas id))
(defn get-pizzas [] (-> pizzas deref vals reverse))
(defn delete! [id] (swap! pizzas dissoc id) nil)

(defn add! [pizza]
  (let [id (swap! id-seq inc)]
    (swap! pizzas assoc id
      (s/validate Pizza ((coerce Pizza) (assoc pizza :id id))))
    (get-pizza id)))

(defn update! [pizza]
  (let [pizza ((coerce Pizza) pizza)]
    (swap! pizzas assoc (:id pizza)
      (s/validate Pizza pizza))
    (get-pizza (:id pizza))))

;; Data

(when (empty? @pizzas)
  (add! {:name "Frutti" :toppings ["cheese" "olives"]})
  (add! {:name "Il Diablo" :toppings ["ham" "habanero"]}))
