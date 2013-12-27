(ns compojure.api.example.handler
  (:require [compojure.api.core :refer [defapi]]
            [compojure.api.swagger :refer :all]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.util.response :refer :all]
            [schema.core :as s]
            [schema.macros :as sm]))

(def Topping (s/enum :cheese :olives :ham :pepperoni :artichoke))

(def Pizza {:id s/Int
            :name s/String
            (s/optional-key :description) s/String
            :toppings [Topping]})

(defapi api
  (swagger-docs "/api/docs"
    ;;:apiVersion "1.0.0"
    ;;:termsOfServiceUrl "http://www.metosin.fi"
    ;;:contact "tommi@metosin.fi"
    ;;:licence "Apache 2.0"
    ;;:licenseUrl "http://www.apache.org/licenses/LICENSE-2.0.html"
    :title "Cool api"
    :description "Compojure Sample Web Api")
  (swaggered :sample
    :description "sample api"
    (context "/api" []
      (context "/v1" []
        (let-routes [v 1]
          (GET "/kikka/:id" [id] (response {:hello (str "kukka id=" id ", v=" v)}))
          (POST "/kakka" [] (response {:hello "kukka"}))))
      (context "/v2" []
        (GET  "/kikka/:a/:b/:c" [a b c] (response {:hello (str a b c)}))
        (POST "/kakka" [] (response {:hello "kukka"}))))))

(def app
  (routes
    api
    (route/resources "/")
    (route/not-found "not found")))
