(ns compojure.api.sweet-test
  (:require [compojure.api.routes :as routes]
            [compojure.api.sweet :refer :all]
            [compojure.api.test-utils :refer :all]
            [midje.sweet :refer :all]
            [ring.mock.request :refer :all]
            [ring.swagger.schema :refer [describe]]
            [schema.core :as s]))

(s/defschema Band {:id s/Int
                   :name s/Str
                   (s/optional-key :description) (s/maybe s/Str)
                   :toppings [(s/enum :cheese :olives :ham :pepperoni :habanero)]})

(s/defschema NewBand (dissoc Band :id))

(defroutes* ping-routes (GET* "/ping" [] identity))

(defapi api
  (swagger-docs
    {:info {:version "1.0.0"
            :title "Sausages"
            :description "Sausage description"
            :termsOfService "http://helloreverb.com/terms/"
            :contact {:name "My API Team"
                      :email "foo@example.com"
                      :url "http://www.metosin.fi"}
            :license {:name "Eclipse Public License"
                      :url "http://www.eclipse.org/legal/epl-v10.html"}}})
  ping-routes
  (context "/api" []
    ping-routes
    (GET* "/bands" []
      :return   [Band]
      :summary  "Gets all Bands"
      :description "bands bands bands"
      :operationId "getBands"
      identity)
    (GET* "/bands/:id" [id]
      :return   Band
      :summary  "Gets a Band"
      :operationId "getBand"
      identity)
    (POST* "/bands" []
      :return   Band
      :body     [band [NewBand]]
      :summary  "Adds a Band"
      :operationId "addBand"
      identity)
    (GET* "/parameters/:a/:b" []
      :path-params [a :- Long]
      :query-params [qp :- Boolean]
      :header-params [hp :- Boolean]
      :operationId "pathHeaderAndQueryParameters"
      identity)
    (GET* "/primitive" []
      :return String
      identity)
    (GET* "/primitiveArray" []
      :return [String]
      identity)))

(facts "api documentation"
  (fact "details are generated"

    (routes/get-routes)

    => {:paths {"/ping" {:get nil}
                "/api/ping" {:get nil}
                "/api/bands" {:get {:operationId "getBands"
                                    :description "bands bands bands"
                                    :responses {200 {:schema [Band]
                                                     :description ""}}
                                    :summary "Gets all Bands"}
                              :post {:operationId "addBand"
                                     :parameters {:body [NewBand]}
                                     :responses {200 {:schema Band
                                                      :description ""}}
                                     :summary "Adds a Band"}}
                "/api/bands/:id" {:get {:operationId "getBand"
                                        :responses {200 {:schema Band
                                                         :description ""}}
                                        :summary "Gets a Band"
                                        :parameters {:path {:id String}}}}
                "/api/parameters/:a/:b" {:get {:operationId "pathHeaderAndQueryParameters"
                                               :parameters {:path {:a Long
                                                                   :b String}
                                                            :header {:hp Boolean
                                                                     s/Keyword s/Any}
                                                            :query {:qp Boolean
                                                                    s/Keyword s/Any}}}}
                "/api/primitive" {:get {:responses {200 {:schema String
                                                         :description ""}}}}
                "/api/primitiveArray" {:get {:responses {200 {:schema [String]
                                                              :description ""}}}}}})

  (fact "api-listing works"
    (let [{:keys [body status]} (api (request :get "/swagger.json"))
          body (parse-body body)]

      (fact "is ok"
        status => 200)

      (fact "spec is ok"
        body => {:swagger "2.0"
                 :info {:version "1.0.0"
                        :title "Sausages"
                        :description "Sausage description"
                        :termsOfService "http://helloreverb.com/terms/"
                        :contact {:name "My API Team"
                                  :email "foo@example.com"
                                  :url "http://www.metosin.fi"}
                        :license {:name "Eclipse Public License"
                                  :url "http://www.eclipse.org/legal/epl-v10.html"}}
                 :consumes ["application/json" "application/x-yaml" "application/edn" "application/transit+json" "application/transit+msgpack"],
                 :produces ["application/json" "application/x-yaml" "application/edn" "application/transit+json" "application/transit+msgpack"]
                 :paths {(keyword "/api/bands") {:get {:operationId "getBands"
                                                       :description "bands bands bands"
                                                       :responses {:200 {:description ""
                                                                         :schema {:items {:$ref "#/definitions/Band"}
                                                                                  :type "array"}}}
                                                       :summary "Gets all Bands"}
                                                 :post {:operationId "addBand"
                                                        :parameters [{:description ""
                                                                      :in "body"
                                                                      :name "NewBand"
                                                                      :required true
                                                                      :schema {:items {:$ref "#/definitions/NewBand"}
                                                                               :type "array"}}]
                                                        :responses {:200 {:description ""
                                                                          :schema {:$ref "#/definitions/Band"}}}
                                                        :summary "Adds a Band"}}
                         (keyword "/api/bands/{id}") {:get {:operationId "getBand"
                                                            :parameters [{:description ""
                                                                          :in "path"
                                                                          :name "id"
                                                                          :required true
                                                                          :type "string"}]
                                                             :responses {:200 {:description ""
                                                                               :schema {:$ref "#/definitions/Band"}}}
                                                            :summary "Gets a Band"}}
                         (keyword "/api/parameters/{a}/{b}") {:get {:operationId "pathHeaderAndQueryParameters"
                                                                     :parameters [{:in "header"
                                                                                   :name "hp"
                                                                                   :description ""
                                                                                   :required true
                                                                                   :type "boolean"}
                                                                                  {:in "query"
                                                                                   :name "qp"
                                                                                   :description ""
                                                                                   :required true
                                                                                   :type "boolean"}
                                                                                  {:in "path"
                                                                                   :name "b"
                                                                                   :description ""
                                                                                   :required true
                                                                                   :type "string"}
                                                                                  {:in "path"
                                                                                   :name "a"
                                                                                   :description ""
                                                                                   :format "int64"
                                                                                   :required true
                                                                                   :type "integer"}]
                                                                     :responses {:default {:description ""}}}}
                         (keyword "/api/ping") {:get {:responses {:default {:description ""}}}}
                         (keyword "/api/primitive") {:get {:responses {:200 {:description ""
                                                                             :schema {:type "string"}}}}}
                         (keyword "/api/primitiveArray") {:get {:responses {:200 {:description ""
                                                                                  :schema {:items {:type "string"}
                                                                                           :type "array"}}}}}
                         (keyword "/ping") {:get {:responses {:default {:description ""}}}}}
                 :definitions {:Band {:properties {:description {:type "string"}
                                                   :id {:format "int64", :type "integer"}
                                                   :name {:type "string"}
                                                   :toppings {:items {:enum ["olives" "pepperoni" "ham" "cheese" "habanero"]
                                                                      :type "string"}
                                                              :type "array"}}
                                      :required ["id" "name" "toppings"]}
                               :NewBand {:properties {:description {:type "string"}
                                                      :name {:type "string"}
                                                      :toppings {:items {:enum ["olives" "pepperoni" "ham" "cheese" "habanero"]
                                                                         :type "string"}
                                                                 :type "array"}}
                                         :required ["name" "toppings"]}}}))))
