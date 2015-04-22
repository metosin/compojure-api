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

(defn sample [] {:date (Date.)
                 :date-time (DateTime.)
                 :local-date (LocalDate.)})

(defroutes* date-routes
  (GET* "/dates" []
    :return   Dates
    :summary  "returns dates"
    (ok (sample)))
  (POST* "/dates" []
    :return   Dates
    :body     [sample (describe Dates "read response from GET /dates in here to see symmetric handling of dates")]
    :summary  "echos date input."
    (ok sample)))
