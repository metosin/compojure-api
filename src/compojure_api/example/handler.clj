(ns compojure-api.example.handler
  (:require [compojure-api.core :refer [defapi]]
            [compojure-api.swagger :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer :all]
            [compojure.core :refer :all]))

(defapi api
  (swagger-docs "/api/docs"
    :apiVersion "1.0.0"
    :title "Cool api"
    :description "Compojure Sample Web Api"
    :temsOfServiceUrl "http://www.metosin.fi"
    :contact "tommi@metosin.fi"
    :licence "Apache 2.0"
    :licenseUrl "http://www.apache.org/licenses/LICENSE-2.0.html")
  (swaggered :sample
    :description "sample api"
    (context "/api" []
      (context "/v1" []
        (let-routes [v 1]
          (GET "/kikka/:id" [id] (response {:hello (str "kukka id=" id ", #" v)}))
          (POST "/kakka" [] (response {:hello "kukka"}))))
      (context "/v2" []
        (GET  "/kikka/:a/:b/:c" [a b c] (response {:hello (str a b c)}))
        (POST "/kakka" [] (response {:hello "kukka"}))))))

(def app
  (routes
    api
    (route/resources "/")
    (route/not-found "not found")))
