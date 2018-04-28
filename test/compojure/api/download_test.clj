(ns compojure.api.download-test
  (:require  [midje.sweet :as midje]
             [compojure.api.sweet :as sw :refer [api context
                                                 undocumented
                                                 GET PUT POST DELETE]]
             [midje.sweet :refer :all]
             [ring.mock.request :as mock]
             [clojure.java.io :as io]
             [ring.util.http-response :refer [ok header]]
             [muuntaja.core :as muuntaja])
  (:import (java.io File)) )

(def app
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger.json"
     :data {:info {:title "API"
                   :description "API methods "}
            :tags [{:name "api", :description ""}]}}}
   
   (context "/download" []
            :tags ["download"]

            (GET "/file" []
                 :summary "file download"
                 :return File
                 :produces ["image/png"]
                 (-> (io/resource "screenshot.png")
                     (io/input-stream)
                     (ok)
                     (header "Content-Type" "image/png")
                     (muuntaja/disable-response-encoding))))))

(fact "download returns data file "
      (let [response (app (-> (mock/request :get "/download/file")))
            klass (-> response :body class)
            status (:status response)]
        status => 200
        klass => java.io.BufferedInputStream))




