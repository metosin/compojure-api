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

(def app-name (str (gensym)))

(defroutes* ping-routes (GET* "/ping" [] identity))

(defapi api
  (swagger-docs)
  (swaggered app-name
    :description "sample api"
    ping-routes
    (context "/api" []
      ping-routes
      (GET* "/bands" []
        :return   [Band]
        :summary  "Gets all Bands"
        :nickname "getBands"
        identity)
      (GET* "/bands/:id" [id]
        :return   Band
        :summary  "Gets a Band"
        :nickname "getBand"
        identity)
      (POST* "/bands" []
        :return   Band
        :body     [band [NewBand]]
        :summary  "Adds a Band"
        :nickname "addBand"
        identity)
      (GET* "/path-header-and-query-parameters/:a/:b" []
        :path-params [a :- Long]
        :query-params [qp :- Boolean]
        :header-params [hp :- Boolean]
        :nickname "pathHeaderAndQueryParameters"
        identity)
      (GET* "/primitive" []
        :return String
        identity)
      (GET* "/primitiveArray" []
        :return [String]
        identity))))

(facts "swaggered"
  (fact "details are generated"
    ((routes/get-routes) app-name)

    => {:description "sample api"
        :routes [{:method :get
                  :uri "/ping"}
                 {:method :get
                  :uri "/api/ping"}
                 {:method :get
                  :uri "/api/bands"
                  :metadata {:nickname "getBands"
                             :return [Band]
                             :summary "Gets all Bands"}}
                 {:method :get
                  :uri "/api/bands/:id"
                  :metadata {:nickname "getBand"
                             :return Band
                             :summary "Gets a Band"
                             :parameters [{:type :path
                                           :model {:id String}}]}}
                 {:method :post
                  :uri "/api/bands"
                  :metadata {:nickname "addBand"
                             :parameters [{:type :body
                                           :model [NewBand]}]
                             :return Band
                             :summary "Adds a Band"}}
                 {:method :get
                  :uri "/api/path-header-and-query-parameters/:a/:b"
                  :metadata {:nickname "pathHeaderAndQueryParameters"
                             :parameters [{:type :path
                                           :model {:a Long
                                                   :b String}}
                                          {:type :header
                                           :model {:hp Boolean
                                                   s/Keyword s/Any}}
                                          {:type :query
                                           :model {:qp Boolean
                                                   s/Keyword s/Any}}
                                          ]}}
                 {:method :get
                  :uri "/api/primitive"
                  :metadata {:return String}}
                 {:method :get
                  :uri "/api/primitiveArray"
                  :metadata {:return [String]}}]})

  (fact "api-listing works"
    (let [{:keys [body status]} (api (request :get "/api/api-docs"))
          body (parse-body body)]
      status => 200
      body => {:apiVersion "0.0.1"
               :apis [{:description "sample api"
                       :path (str "/" app-name)}]
               :info {}
               :authorizations {}
               :swaggerVersion "1.2"}))

  (fact "api-details works"
    (let [{:keys [body status]} (api (request :get (str "/api/api-docs/" app-name)))
          body (parse-body body)]
      status => 200
      body => truthy)))
