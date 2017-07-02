(ns example.schema
  (:require [compojure.api.sweet :refer [context POST resource]]
            [ring.util.http-response :refer [ok]]
            [schema.core :as s]))

(s/defschema Total
  {:total s/Int})

(def routes
  (context "/schema" []
    :tags ["schema"]

    (POST "/plus" []
      :summary "plus with schema"
      :body-params [x :- s/Int, {y :- s/Int 0}]
      :return Total
      (ok {:total (+ x y)}))

    (context "/plus" []
      (resource
        {:get
         {:summary "data-driven plus with schema"
          :parameters {:query-params {:x s/Str, :y s/Str}}
          :responses {200 {:schema Total}}
          :handler (fn [{{:keys [x y]} :query-params}]
                     (ok {:total (+ x y)}))}}))))