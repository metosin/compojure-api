(ns examples.thingie
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [clj-time.core :as t]))

;;
;; Schemas
;;

(s/defschema Total {:total Long})

(s/defschema Thingie {:id Long
                      :hot Boolean
                      :tag (s/enum :kikka :kukka)
                      :chief [{:name String
                               :type #{{:id String}}}]})

(s/defschema FlatThingie (dissoc Thingie :chief))

;;
;; Routes
;;

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
        :return       Long
        :query-params [x :- Long {y :- Long 1}]
        :summary      "x+y with query-parameters. y defaults to 1."
        (ok (+ x y)))

      (POST* "/minus" []
        :return      Total
        :body-params [x :- Long y :- Long]
        :summary     "x-y with body-parameters."
        (ok {:total (- x y)}))

      (GET* "/times/:x/:y" []
        :return      Total
        :path-params [x :- Long y :- Long]
        :summary     "x*y with path-parameters"
        (ok {:total (* x y)}))

      (GET* "/power" []
        :return      Total
        :header-params [x :- Long y :- Long]
        :summary     "x^y with header-parameters"
        (ok {:total (long (Math/pow x y))}))

      (GET* "/now" []
        :return java.util.Date
        :summary "current time"
        (ok (new java.util.Date)))

      (GET* "/joda-now" []
        :return org.joda.time.DateTime
        :summary "current jodatime"
        (ok (t/now)))

      (GET* "/ping/:s" []
        :return String
        :path-params [s :- String]
        :summary "echos a string from query-params"
        (ok s))

      legacy-route

      (GET* "/echo" []
        :return   FlatThingie
        :query    [thingie FlatThingie]
        :summary  "echoes a FlatThingie from query-params"
        (ok thingie))

      (PUT* "/echo" []
        :return   [{:hot Boolean}]
        :body     [body [{:hot Boolean}]]
        :summary  "echoes a vector of anonymous hotties"
        (ok body))

      (POST* "/echo" []
        :return   Thingie
        :body     [thingie Thingie]
        :summary  "echoes a Thingie from json-body"
        (ok thingie)))))
