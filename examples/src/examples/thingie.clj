(ns examples.thingie
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [ring.swagger.schema :refer [defmodel]]
            [schema.core :as s]))

(defmodel Thingie {:id Long
                   :hot Boolean
                   :tag (s/enum :kikka :kukka)})

(defroutes* legacy-route
  (GET* "/legacy/:value" [value]
    (ok {:value value})))

(defapi app
  (swagger-ui)
  (swagger-docs
    :title "Sample api")
  (swaggered "thingie"
    :description "There be thingies"
    (context "/api" []
      legacy-route

      (GET* "/sum" []
        :query-params [x :- Long y :- Long]
        :summary      "sums x & y query-parameters"
        (ok {:total (+ x y)}))

      (GET* "/times/:x/:y" []
        :path-params [x :- Long y :- Long]
        :summary      "multiplies x & y path-parameters"
        (ok {:total (* x y)}))

      (GET* "/echo" []
        :return   Thingie
        :query    [thingie Thingie]
        :summary  "echos a thingie from query-params"
        (ok thingie)) ;; here be coerced thingie

      (POST* "/echo" []
        :return   Thingie
        :body     [thingie Thingie]
        :summary  "echos a thingie from json-body"
        (ok thingie))))) ;; here be coerced thingie
