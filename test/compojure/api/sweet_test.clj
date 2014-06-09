(ns compojure.api.sweet-test
  (:require [midje.sweet :refer :all]
            [schema.core :as s]
            [ring.swagger.schema :refer :all]
            [compojure.api.swagger :as swagger]
            [ring.mock.request :refer :all]
            [cheshire.core :as cheshire]
            [compojure.core :as compojure]
            [compojure.api.sweet :refer :all]))

(defmodel Band {:id s/Int
                :name s/Str
                (s/optional-key :description) s/Str
                :toppings [(s/enum :cheese :olives :ham :pepperoni :habanero)]})

(defmodel NewBand (dissoc Band :id))

;; Make sure defapis from other namespaces are reset
;; (eg. midje autotest will load examples.thingie which would break tests)
(reset! swagger/swagger (array-map))

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
        :body     [band [NewBand] {:description "new Band"}]
        :summary  "Adds a Band"
        :nickname "addBand"
        identity)
      (GET* "/path-and-query-parameters/:a/:b" []
        :path-params [a :- Long]
        :query-params [all :- Boolean]
        :nickname "pathAndQueryParameters"
        identity))))

(facts "swaggered"
  (background
    (after :contents (swap! swagger/swagger dissoc app-name)))

  (fact "details are generated"
    (@swagger/swagger app-name)

    => {:description "sample api"
        :routes [{:method :get
                  :uri "/ping"}
                 {:method :get
                  :uri "/api/ping"}
                 {:method :get
                  :uri "/api/bands"
                  :metadata {:nickname "getBands"
                             :return [#'Band]
                             :summary "Gets all Bands"}}
                 {:method :get
                  :uri "/api/bands/:id"
                  :metadata {:nickname "getBand"
                             :return #'Band
                             :summary "Gets a Band"
                             :parameters [{:type :path
                                           :model {:id String}}]}}
                 {:method :post
                  :uri "/api/bands"
                  :metadata {:nickname "addBand"
                             :parameters [{:type :body
                                           :model [#'NewBand]
                                           :meta {:description "new Band"}}]
                             :return #'Band
                             :summary "Adds a Band"}}
                 {:method :get
                  :uri "/api/path-and-query-parameters/:a/:b"
                  :metadata {:nickname "pathAndQueryParameters"
                             :parameters [{:type :path
                                           :model {:a Long
                                                   :b String}}
                                          {:type :query
                                           :model {:all Boolean
                                                   s/Keyword s/Any}}]}}]})

  (fact "api-listing works"
    (let [{:keys [body status]} (api (request :get "/api/api-docs"))
          body (cheshire/parse-string body true)]
      status => 200
      body => {:apiVersion "0.0.1"
               :apis [{:description "sample api"
                       :path (str "/" app-name)}]
               :info {}
               :swaggerVersion "1.2"}))

  (fact "api-details works"
    (let [{:keys [body status]} (api (request :get (str "/api/api-docs/" app-name)))
          body (cheshire/parse-string body true)]
      status => 200
      body => truthy)))
