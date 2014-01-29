(ns compojure.api.sweet-test
  (:require [midje.sweet :refer :all]
            [schema.core :as s]
            [ring.swagger.schema :refer :all]
            [ring.swagger.core :refer [->Route map->Route]]
            [compojure.api.sweet :refer :all]))

(defmodel Band {:id s/Int
                :name s/Str
                (s/optional-key :description) s/Str
                :toppings [(s/enum :cheese :olives :ham :pepperoni :habanero)]})

(defmodel NewBand (dissoc Band :id))

(fact "swaggered"
  (let [app (swaggered "sample"
              :description "sample api"
              (context "/api" []
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
                  :body     [Band NewBand {:description "new Band"}]
                  :summary  "Adds a Band"
                  :nickname "addBand"
                  identity)))
        swagger (:swagger (meta app))]
    app => truthy
    swagger => {:description "sample api"
                :models [#'NewBand
                         #'Band]
                :routes [(map->Route
                           {:method :compojure.core/get ;; should be plain :get
                            :uri ["/api/bands"]
                            :metadata {:nickname "getBands"
                                       :return [#'Band]
                                       :summary "Gets all Bands"}})
                         (map->Route
                           {:method :compojure.core/get ;; should be plain :get
                            :uri ["/api/bands/" :id]
                            :metadata {:nickname "getBand"
                                       :return #'Band
                                       :summary "Gets a Band"}})
                          (map->Route
                            {:method :compojure.core/post ;; should be plain :post
                             :uri ["/api/bands"]
                             :metadata {:nickname "addBand"
                                        :parameters [{:description "new Band"
                                                      :name "newband"
                                                      :paramType "body"
                                                      :required "true"
                                                      :type #'NewBand}]
                                        :return #'Band
                                        :summary "Adds a Band"}})]}))
