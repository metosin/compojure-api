(ns example.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [metrics.ring.instrument :refer [instrument]]
            [metrics.core :refer [default-registry]]
            [metrics.ring.expose :refer [render-metrics]]
            [schema.core :as s]))

(s/defschema Pizza
  {:name s/Str
   (s/optional-key :description) s/Str
   :size (s/enum :L :M :S)
   :origin {:country (s/enum :FI :PO)
            :city s/Str}})

(def app
  (instrument
    (api
      {:swagger
       {:ui "/"
        :spec "/swagger.json"
        :data {:info {:title "Simple"
                      :description "Compojure Api example"}
               :tags [{:name "api", :description "some apis"}]}}}

      (context "/api" []
        :tags ["api"]

        (GET "/metrics" []
          :summary "Application level metrics."
          (ok (render-metrics default-registry)))

        (GET "/plus" []
          :return {:result Long}
          :query-params [x :- Long, y :- Long]
          :summary "adds two numbers together"
          (ok {:result (+ x y)}))

        (POST "/echo" []
          :return Pizza
          :body [pizza Pizza]
          :summary "echoes a Pizza"
          (ok pizza))))))
