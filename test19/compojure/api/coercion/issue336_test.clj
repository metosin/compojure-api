(ns compojure.api.coercion.issue336-test
  (:require [midje.sweet :refer :all]
            [compojure.api.test-utils :refer :all]
            [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [spec-tools.core :as st]))

(s/def ::customer-id spec/string?)
(s/def ::requestor-id spec/string?)
(s/def ::requestor-email spec/string?)
(s/def ::requestor-name spec/string?)
(s/def ::endpoint spec/string?)
(s/def ::from-year spec/int?)
(s/def ::from-month spec/int?)
(s/def ::to-year spec/int?)
(s/def ::to-month spec/int?)

(s/def ::input-settings (s/and
                          (s/keys :req-un [::endpoint
                                           ::customer-id
                                           ::requestor-id]
                                  :opt-un [::from-year
                                           ::from-month
                                           ::to-year
                                           ::to-month
                                           ::requestor-email
                                           ::requestor-name])))

(def app
  (api
    {:swagger
     {:ui "/"
      :spec "/swagger.json"
      :data {:info {:title "Futomaki"
                    :description "API for counter stats over the Sushi protocol"}
             :tags [{:name "Reports", :description "Retrieve information per report definition"}]}}}

    (context "/api" []
      :tags ["api"]
      :coercion :spec

      (context "/jr1" []
        (resource
          {:get
           {:summary "Number of successful full-text article requests by month and journal"
            :parameters {:query-params ::input-settings}
            :response {200 {:schema ::input-settings}}
            :handler (fn [{:keys [query-params]}]
                       (ok query-params))}})))))

(fact "coercion works with s/and"
  (let [data {:endpoint "http://sushi.cambridge.org/GetReport"
              :customer-id "abc"
              :requestor-id "abc"}
        [status body] (get* app "/api/jr1" data)]
    status => 200
    body => data))
