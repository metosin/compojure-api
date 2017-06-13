(ns example.handler
  (:require [compojure.api.sweet :refer [api]]
            [example.schema]
            [example.spec]
            [example.data-spec]))

(def app
  (api
    {:swagger
     {:ui "/"
      :spec "/swagger.json"
      :data {:info {:title "Compojure-api demo"
                    :description "Demontrating 2.0.0 (alpha) coersion"}
             :tags [{:name "schema", :description "math with schema coercion"}
                    {:name "spec", :description "math with clojure.spec coercion"}
                    {:name "data-spec", :description "math with data-specs coercion"}]}}}

    example.schema/routes
    example.spec/routes
    example.data-spec/routes))
