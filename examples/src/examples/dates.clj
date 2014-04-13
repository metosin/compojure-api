(ns examples.dates
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ring.swagger.schema :refer :all]))

(defmodel Dates {:date java.util.Date
                 :date-time org.joda.time.DateTime
                 :local-date org.joda.time.LocalDate})

(defroutes* date-routes
  (POST* "/dates" []
    :return   Dates
    :body     [dates Dates {:description "{\"date\": \"2014-02-19T12:01:20.147Z\", \"date-time\": \"2014-02-18T12:01:20.147Z\", \"local-date\": \"2014-02-02\"}"}]
    :summary  "echo input."
    (ok (coerce! Dates dates))))
