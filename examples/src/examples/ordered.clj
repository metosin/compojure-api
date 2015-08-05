(ns examples.ordered
  (:require [schema.core :as s]
            [flatland.ordered.map :as fom]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]))

;; does not work with AOT
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

(defroutes* more-ordered-routes
  (GET* "/6" [] identity)
  (GET* "/7" [] identity)
  (GET* "/8" [] identity))

(defroutes* ordered-routes
  (context* "/ordered" []
    :tags ["ordered"]
    (context "/a" []
      (GET* "/1" [] (ok))
      (GET* "/2" [] (ok))
      (GET* "/3" [] (ok))
      (context "/b" []
        (GET* "/4" [] (ok))
        (GET* "/5" [] (ok)))
      (context "/c" []
        more-ordered-routes
        (GET* "/9" [] (ok))
        (GET* "/10" [] (ok))))
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
