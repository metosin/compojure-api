(ns compojure.api.test-domain
  (:require [schema.core :as s]
            [compojure.api.sweet :refer [defroutes* POST*]]
            [ring.util.http-response :refer [ok]]))

(s/defschema Topping {:name s/Str})
(s/defschema Pizza {:toppings (s/maybe [Topping])})

(s/defschema Beef {:name s/Str})
(s/defschema Burger {:ingredients (s/maybe [Beef])})

(defroutes* burger-routes
  (POST* "/burger" []
    :return Burger
    :body [burger Burger]
    (ok burger)))
