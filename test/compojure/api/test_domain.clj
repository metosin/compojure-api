(ns compojure.api.test-domain
  (:require [schema.core :as s]))

(s/defschema Topping {(s/optional-key :data) String})
(s/defschema Pizza {:toppings [Topping]})
