(ns example.handler
  "Asynchronous compojure-api application."
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [manifold.deferred :as d]
            compojure.api.async))

(def app
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger.json"
     :data {:info {:title "Simple"
                   :description "Compojure Api example"}
            :tags [{:name "api", :description "some apis"}]}}}

   (context "/api" []
     :tags ["api"]

     (GET "/plus" []
       :return {:result Long}
       :query-params [x :- Long, y :- Long]
       :summary "adds two numbers together"
       (ok {:result (+ x y)}))

     (GET "/slowplus" []
       :return {:result Long}
       :query-params [x :- Long, y :- Long]
       :summary "slowly adds two numbers together"
       (fn [_ respond _]
         (future
           (Thread/sleep 2000)
           (respond (ok {:result (+ x y)})))
         nil))

     (GET "/times" []
       :return {:result Long}
       :query-params [x :- Long, y :- Long]
       :summary "multiply two numbers together"
       (doto (d/deferred)
         (deliver (ok {:result (* x y)})))))))
