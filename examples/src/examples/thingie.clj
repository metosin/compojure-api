(ns examples.thingie
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [ring.swagger.schema :refer [defmodel]]
            [schema.core :as s]))

(defmodel Thingie {:id Long
                   :hot Boolean
                   :tag (s/enum :kikka :kukka)})

(defroutes* legacy-route
  (GET* "/ping/:id" [id]
    (ok {:id id})))

(defapi app
  (swagger-ui)
  (swagger-docs
    :title "Sample api")
  (swaggered "thingie"
    :description "There be thingies"
    (context "/api" []
      legacy-route
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
