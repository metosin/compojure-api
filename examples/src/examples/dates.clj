(ns examples.dates
  (:require [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [ring.util.http-response :refer :all]
            [ring.swagger.schema :refer [describe]])
  (:import [java.util Date]
           [org.joda.time DateTime LocalDate]))

(s/defschema Dates {:date Date
                    :date-time DateTime
                    :local-date LocalDate})

(defroutes* date-routes
  (POST* "/dates" []
    :return   Dates
    :body     [dates (describe Dates "{\"date\": \"2014-02-19T12:01:20.147Z\", \"date-time\": \"2014-02-18T12:01:20.147Z\", \"local-date\": \"2014-02-02\"}")]
    :summary  "echos date input."
    (ok dates)))
