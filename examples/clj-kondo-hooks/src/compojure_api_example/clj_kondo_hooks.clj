(ns compojure-api-example.clj-kondo-hooks
  (:require ;[compojure.api.sweet :as sweet]
            [compojure.api.core :as core]
            [ring.util.http-response :as resp]))

(core/GET "/30" [] (resp/ok {:result 30}))

(core/GET "/30" req
          (do +compojure-api-request+
              (resp/ok {:result (:body req)})))
