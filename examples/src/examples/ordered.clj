(ns examples.ordered
  (:require [schema.core :as s]
            [flatland.ordered.map :as fom]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]))

(s/defschema Ordered
  (fom/ordered-map
    :a s/Str
    :b s/Str
    :c s/Str
    :d s/Str
    :e s/Str
    :f s/Str
    :g s/Str
    :h s/Str))

(defroutes* ordered-routes
  (context* "/ordered" []
    :tags ["ordered"]
    (GET* "/" []
      :return Ordered
      :summary "Ordered data"
      (ok
        (fom/ordered-map
          :a "a"
          :b "b"
          :c "c"
          :d "d"
          :e "e"
          :f "f"
          :g "g"
          :h "h")))))
