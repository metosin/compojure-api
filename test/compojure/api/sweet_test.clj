(ns compojure.api.sweet-test
  (:require [compojure.api.sweet :refer :all]
            [compojure.api.test-utils :refer :all]
            [testit.core :refer :all]
            [clojure.test :refer [deftest]]
            [ring.mock.request :refer :all]
            [schema.core :as s]
            [ring.swagger.validator :as v]))

(s/defschema Band {:id s/Int
                   :name s/Str
                   (s/optional-key :description) (s/maybe s/Str)
                   :toppings [(s/enum :cheese :olives :ham :pepperoni :habanero)]})

(s/defschema NewBand (dissoc Band :id))

(def ping-route
  (GET "/ping" [] identity))

(def app
  (api
    {:swagger {:spec "/swagger.json"
               :data {:info {:version "1.0.0"
                             :title "Sausages"
                             :description "Sausage description"
                             :termsOfService "http://helloreverb.com/terms/"
                             :contact {:name "My API Team"
                                       :email "foo@example.com"
                                       :url "http://www.metosin.fi"}
                             :license {:name "Eclipse Public License"
                                       :url "http://www.eclipse.org/legal/epl-v10.html"}}}}}
    ping-route
    (context "/api" []
      ping-route
      (GET "/bands" []
        :name :bands
        :return [Band]
        :summary "Gets all Bands"
        :description "bands bands bands"
        :operationId "getBands"
        identity)
      (GET "/bands/:id" [id]
        :return Band
        :summary "Gets a Band"
        :operationId "getBand"
        identity)
      (POST "/bands" []
        :return Band
        :body [band [NewBand]]
        :summary "Adds a Band"
        :operationId "addBand"
        identity)
      (GET "/query" []
        :query-params [qp :- Boolean]
        identity)
      (GET "/header" []
        :header-params [hp :- Boolean]
        identity)
      (POST "/form" []
        :form-params [fp :- Boolean]
        identity)
      (GET "/primitive" []
        :return String
        identity)
      (GET "/primitiveArray" []
        :return [String]
        identity))))

(deftest api-documentation-test
  (fact "details are generated"

    (extract-paths app)

    => {"/swagger.json" {:get {:x-name :compojure.api.swagger/swagger}}
        "/ping" {:get {}}
        "/api/ping" {:get {}}
        "/api/bands" {:get {:x-name :bands
                            :operationId "getBands"
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
        "/api/query" {:get {:parameters {:query {:qp Boolean
                                                 s/Keyword s/Any}}}}
        "/api/header" {:get {:parameters {:header {:hp Boolean
                                                   s/Keyword s/Any}}}}
        "/api/form" {:post {:parameters {:formData {:fp Boolean}}
                            :consumes ["application/x-www-form-urlencoded"]}}
        "/api/primitive" {:get {:responses {200 {:schema String
                                                 :description ""}}}}
        "/api/primitiveArray" {:get {:responses {200 {:schema [String]
                                                      :description ""}}}}})

  (fact "api-listing works"
    (let [spec (get-spec app)]

      spec => (just
                {:swagger "2.0"
                 :info {:version "1.0.0"
                        :title "Sausages"
                        :description "Sausage description"
                        :termsOfService "http://helloreverb.com/terms/"
                        :contact {:name "My API Team"
                                  :email "foo@example.com"
                                  :url "http://www.metosin.fi"}
                        :license {:name "Eclipse Public License"
                                  :url "http://www.eclipse.org/legal/epl-v10.html"}}
                 :basePath "/"
                 :consumes (just
                             ["application/json"
                              "application/edn"
                              "application/transit+json"
                              "application/transit+msgpack"]
                             :in-any-order),
                 :produces (just
                             ["application/json"
                              "application/edn"
                              "application/transit+json"
                              "application/transit+msgpack"]
                             :in-any-order)
                 :paths {"/api/bands" {:get {:x-name "bands"
                                             :operationId "getBands"
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
                         "/api/bands/{id}" {:get {:operationId "getBand"
                                                  :parameters [{:description ""
                                                                :in "path"
                                                                :name "id"
                                                                :required true
                                                                :type "string"}]
                                                  :responses {:200 {:description ""
                                                                    :schema {:$ref "#/definitions/Band"}}}
                                                  :summary "Gets a Band"}}
                         "/api/query" {:get {:parameters [{:in "query"
                                                           :name "qp"
                                                           :description ""
                                                           :required true
                                                           :type "boolean"}]
                                             :responses {:default {:description ""}}}}
                         "/api/header" {:get {:parameters [{:in "header"
                                                            :name "hp"
                                                            :description ""
                                                            :required true
                                                            :type "boolean"}]
                                              :responses {:default {:description ""}}}}
                         "/api/form" {:post {:parameters [{:in "formData"
                                                           :name "fp"
                                                           :description ""
                                                           :required true
                                                           :type "boolean"}]
                                             :responses {:default {:description ""}}
                                             :consumes ["application/x-www-form-urlencoded"]}}
                         "/api/ping" {:get {:responses {:default {:description ""}}}}
                         "/api/primitive" {:get {:responses {:200 {:description ""
                                                                   :schema {:type "string"}}}}}
                         "/api/primitiveArray" {:get {:responses {:200 {:description ""
                                                                        :schema {:items {:type "string"}
                                                                                 :type "array"}}}}}
                         "/ping" {:get {:responses {:default {:description ""}}}}}
                 :definitions {:Band {:type "object"
                                      :properties {:description {:type "string"
                                                                 :x-nullable true}
                                                   :id {:format "int64", :type "integer"}
                                                   :name {:type "string"}
                                                   :toppings {:items {:enum ["olives"
                                                                             "pepperoni"
                                                                             "ham"
                                                                             "cheese"
                                                                             "habanero"]
                                                                      :type "string"}
                                                              :type "array"}}
                                      :required ["id" "name" "toppings"]
                                      :additionalProperties false}
                               :NewBand {:type "object"
                                         :properties {:description {:type "string"
                                                                    :x-nullable true}
                                                      :name {:type "string"}
                                                      :toppings {:items {:enum ["olives"
                                                                                "pepperoni"
                                                                                "ham"
                                                                                "cheese"
                                                                                "habanero"]
                                                                         :type "string"}
                                                                 :type "array"}}
                                         :required ["name" "toppings"]
                                         :additionalProperties false}}})

      (fact "spec is valid"
        (v/validate spec) => nil))))

(deftest produces-and-consumes-test
  (let [app (api
              {:swagger {:spec "/swagger.json"
                         :data {:produces ["application/json" "application/edn"]
                                :consumes ["application/json" "application/edn"]}}}
              ping-route)]
    (get-spec app) => (contains
                        {:consumes (just
                                     ["application/json"
                                      "application/edn"]
                                     :in-any-order)
                         :produces (just
                                     ["application/json"
                                      "application/edn"]
                                     :in-any-order)})))
