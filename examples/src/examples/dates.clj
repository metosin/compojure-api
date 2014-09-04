(ns examples.dates
  (:require [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [ring.util.http-response :refer :all]
            [ring.swagger.schema :refer [describe]]))

(s/defschema Dates {:date java.util.Date
                    :date-time org.joda.time.DateTime
                    :local-date org.joda.time.LocalDate})

(defroutes* date-routes
  (POST* "/dates" []
    :return   Dates
    :body     [dates (describe Dates "{\"date\": \"2014-02-19T12:01:20.147Z\", \"date-time\": \"2014-02-18T12:01:20.147Z\", \"local-date\": \"2014-02-02\"}")]
    :summary  "echos date input."
    (ok dates)))
