(ns examples.domain
  (:require [schema.core :as s]
            [ring.swagger.schema :as rs]))

;;
;; Domain
;;

(rs/defmodel QueryParams {:long Long
                          (s/optional-key :string) String
                          :bool Boolean
                          :enum (s/enum "kikka" "kakka")})

(rs/defmodel Customer {:id String
                       :address {:street String
                                 :zip Long
                                 :country {:code Long
                                           :name String}}})

;;
;; Pizza Store
;;

(s/defschema Pizza {:id    Long
                    :name  String
                    :price Double
                    :hot   Boolean
                    (s/optional-key :description) String
                    :toppings #{(s/enum :cheese :olives :ham :pepperoni :habanero)}})

(s/defschema NewPizza (dissoc Pizza :id))

;; Repository

(defonce id-seq (atom 0))
(defonce pizzas (atom (array-map)))

(defn get-pizza [id] (@pizzas id))
(defn get-pizzas [] (-> pizzas deref vals reverse))
(defn delete! [id] (swap! pizzas dissoc id) nil)

(defn add! [new-pizza]
  (let [id (swap! id-seq inc)
        pizza (rs/coerce! Pizza (assoc new-pizza :id id))]
    (swap! pizzas assoc id pizza)
    pizza))

(defn update! [pizza]
  (let [pizza (rs/coerce! Pizza pizza)]
    (swap! pizzas assoc (:id pizza) pizza)
    (get-pizza (:id pizza))))

;; Data

(when (empty? @pizzas)
  (add! {:name "Frutti" :price 9.50 :hot false :toppings #{:cheese :olives}})
  (add! {:name "Il Diablo" :price 12 :hot true :toppings #{:ham :habanero}}))
