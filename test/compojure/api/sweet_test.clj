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

(def app-name "default")

(defroutes* ping-routes (GET* "/ping" [] identity))

(defapi api
  (swagger-docs)
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
    (GET* "/parameters/:a/:b" []
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
      identity)))

(facts "api documentation"
  (fact "details are generated"

    ((routes/get-routes) app-name)

    => {:paths {"/ping" {:get nil}
                "/api/ping" {:get nil}
                "/api/bands" {:get {:nickname "getBands"
                                    :return [Band]
                                    :summary "Gets all Bands"}
                              :post {:nickname "addBand"
                                     :parameters {:body [NewBand]}
                                     :return Band
                                     :summary "Adds a Band"}}
                "/api/bands/:id" {:get {:nickname "getBand"
                                        :return Band
                                        :summary "Gets a Band"
                                        :parameters {:path {:id String}}}}
                "/api/parameters/:a/:b" {:get {:nickname "pathHeaderAndQueryParameters"
                                               :parameters {:path {:a Long
                                                                   :b String}
                                                            :header {:hp Boolean
                                                                     s/Keyword s/Any}
                                                            :query {:qp Boolean
                                                                    s/Keyword s/Any}}}}
                "/api/primitive" {:get {:return String}}
                "/api/primitiveArray" {:get {:return [String]}}}})

  #_(fact "api-listing works"
    (let [{:keys [body status]} (api (request :get "/api/api-docs"))
          body (parse-body body)]
      status => 200
      body => {:apiVersion "0.0.1"
               :apis [{:description ""
                       :path (str "/" app-name)}]
               :info {}
               :authorizations {}
               :swaggerVersion "1.2"}))

  #_(fact "api-details works"
    (let [{:keys [body status]} (api (request :get (str "/api/api-docs/" app-name)))
          body (parse-body body)]
      status => 200
      body => truthy)))
