(ns compojure-api-example.clj-kondo-hooks
  (:require [compojure.api.sweet :as sweet]
            [ring.util.http-response :as resp]))

(sweet/GET "/30" [] (resp/ok {:result 30}))
(sweet/GET "/30" req
           (resp/ok {:result (:body req)}))
