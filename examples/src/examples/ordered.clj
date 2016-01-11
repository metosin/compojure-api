(ns examples.ordered
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]))

(def more-ordered-routes
  (routes*
    (GET* "/6" [] (ok))
    (GET* "/7" [] (ok))
    (GET* "/8" [] (ok))))

(def ordered-routes
  (context* "/ordered" []
    :tags ["ordered"]
    (context* "/a" []
      (GET* "/1" [] (ok))
      (GET* "/2" [] (ok))
      (GET* "/3" [] (ok))
      (context* "/b" []
        (GET* "/4" [] (ok))
        (GET* "/5" [] (ok)))
      (context* "/c" []
        more-ordered-routes
        (GET* "/9" [] (ok))
        (GET* "/10" [] (ok))))))
