(ns example.handler
  "Asynchronous compojure-api application."
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [manifold.deferred :as d]
            [clojure.core.async :as async]
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

     (GET "/minus" []
       :return {:result Long}
       :query-params [x :- Long, y :- Long]
       :summary "subtract two numbers from each other"
       (fn [_ respond _]
         (future
           (respond (ok {:result (- x y)})))
         nil))

     (GET "/times" []
       :return {:result Long}
       :query-params [x :- Long, y :- Long]
       :summary "multiply two numbers together"
       (let [d (d/deferred)]
         (future
           (d/success! d (ok {:result (* x y)})))
         d))

     (GET "/divide" []
       :return {:result Float}
       :query-params [x :- Long, y :- Long]
       :summary "multiply two numbers together"
       (let [chan (async/chan)]
         (future
           (async/go
             (try
               (async/>! chan (ok {:result (float (/ x y))}))
               (catch Throwable e
                 (async/>! chan e))
               (finally
                 (async/close! chan)))))
         chan)))))
