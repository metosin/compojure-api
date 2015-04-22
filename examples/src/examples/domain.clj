(ns examples.domain
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ring.swagger.schema :as rs :refer [describe]]))

;;
;; Pizza Store
;;

(s/defschema Topping {:type (s/enum :cheese :olives :ham :pepperoni :habanero)
                      :qty  Long})

(s/defschema Pizza {:id    Long
                    :name  String
                    :price Double
                    :hot   Boolean
                    (s/optional-key :description) String
                    :toppings (describe [Topping] "List of toppings of the Pizza")})

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
  (add! {:name "Frutti" :price 9.50 :hot false :toppings [{:type :cheese :qty 2}
                                                          {:type :olives :qty 1}]})
  (add! {:name "Il Diablo" :price 12 :hot true :toppings [{:type :ham :qty 3}
                                                          {:type :habanero :qty 1}]}))

;; Routes

(defroutes* pizza-routes
  (context* "/api" []
    :tags ["pizzas"]
    (context "/pizzas" []
      (GET* "/" []
        :return   [Pizza]
        :summary  "Gets all Pizzas"
        (ok (get-pizzas)))
      (GET* "/:id" []
        :path-params [id :- Long]
        :return   (s/maybe Pizza)
        :summary  "Gets a pizza"
        (ok (get-pizza id)))
      (POST* "/" []
        :return   Pizza
        :body     [pizza (describe NewPizza "new pizza")]
        :summary  "Adds a pizza"
        (ok (add! pizza)))
      (PUT* "/" []
        :return   Pizza
        :body     [pizza Pizza]
        :summary  "Updates a pizza"
        (ok (update! pizza)))
      (DELETE* "/:id" []
        :path-params [id :- Long]
        :summary  "Deletes a Pizza"
        (ok (delete! id)))))

  (context* "/foreign" []
    :tags ["foreign"]
    ;:path-params [foo :- s/Str]
    (GET* "/bar" []
      (ok {:bar "foo"}))))
