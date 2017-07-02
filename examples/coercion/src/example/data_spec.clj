(ns example.data-spec
  (:require [compojure.api.sweet :refer [context POST resource]]
            [ring.util.http-response :refer [ok]]))

(def routes
  (context "/data-spec" []
    :tags ["data-spec"]
    :coercion :spec

    (POST "/plus" []
      :summary "plus with clojure.spec using data-specs"
      :body-params [x :- int?, {y :- int? 0}]
      :return {:total int?}
      (ok {:total (+ x y)}))

    (context "/plus" []
      (resource
        {:get
         {:summary "data-driven plus with clojure.spec using data-specs"
          :parameters {:query-params {:x int?, :y int?}}
          :responses {200 {:schema {:total int?}}}
          :handler (fn [{{:keys [x y]} :query-params}]
                     (ok {:total (+ x y)}))}}))))
