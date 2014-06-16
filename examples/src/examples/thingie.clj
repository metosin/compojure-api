(ns examples.thingie
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]))

(s/defschema Total {:total Long})
(s/defschema Thingie {:id Long
                      :hot Boolean
                      :tag (s/enum :kikka :kukka)
                      :chief [{:name String
                               :type #{{:id String}}}]})
(s/defschema FlatThingie (dissoc Thingie :chief))

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

      (GET* "/plus" []
        :return Total
        :query-params [x :- Long {y :- Long 1}]
        :summary      "x+y with query-parameters. y defaults to 1."
        (ok {:total (+ x y)}))

      (POST* "/minus" []
        :return Total
        :body-params  [x :- Long y :- Long]
        :summary      "x-y with body-parameters."
        (ok {:total (- x y)}))

      (GET* "/times/:x/:y" []
        :return Total
        :path-params  [x :- Long y :- Long]
        :summary      "x*y with path-parameters"
        (ok {:total (* x y)}))

      legacy-route

      (GET* "/echo" []
        :return   FlatThingie
        :query    [thingie FlatThingie]
        :summary  "echos a FlatThingie from query-params"
        (ok thingie))

      (POST* "/echo" []
        :return   Thingie
        :body     [thingie Thingie]
        :summary  "echos a Thingie from json-body"
        (ok thingie)))))
