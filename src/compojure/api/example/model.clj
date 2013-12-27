(ns compojure.api.example.model
  (:require [schema.core :as s]
            [schema.macros :as sm]))

(def Topping (s/enum :cheese :olives :ham :pepperoni :artichoke))

(def Pizza {:id s/Int
            :name s/String
            (s/optional-key :description) s/String
            :toppings [Topping]})
