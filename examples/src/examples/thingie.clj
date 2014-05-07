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

      (GET* "/plus" []
        :query-params [x :- Long y :- Long]
        :summary      "x+y with query-parameters"
        (ok {:total (+ x y)}))

      (POST* "/minus" []
        :body-params  [x :- Long y :- Long]
        :summary      "x-y with body-parameters"
        (ok {:total (- x y)}))

      (GET* "/times/:x/:y" []
        :path-params  [x :- Long y :- Long]
        :summary      "x*y with path-parameters"
        (ok {:total (* x y)}))

      (GET* "/increment/:x" []
        :path-params [x :- Long]
        :query-params [{y :- Long 1}]
        :summary "x++, with optional parameter y to use other than default value 1 increment"
        (ok {:total (+ x y)}))

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
