(ns compojure.api.example.model
  (:require [schema.core :as s]
            [schema.macros :as sm]))

;;
;; User
;;

(def Country (s/enum :fin :swe :eng))

(def User {:id s/Int
           :name s/String
           (s/optional-key :age) s/Int
           (s/optional-key :address) {:street s/String
                                      :number s/Int
                                      :zip    s/Int
                                      :country Country}})

(s/validate User
  {:id 1
   :name "Tommi"
   :address {:street "Kekkosentie"
             :number 122
             :zip    33101
             :country :fin}})

;;
;; Pizza
;;

(def Topping (s/enum :cheese :olives :ham :pepperoni :artichoke))

(def Pizza {:id s/Int
            :name s/String
            (s/optional-key :description) s/String
            :toppings [Topping]})

(s/validate Pizza
  {:id 1
   :name "Quatro"
   :toppings [:cheese :olives :ham]})
