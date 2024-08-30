(ns compojure-api-example.clj-kondo-hooks
  (:require ;[compojure.api.sweet :as sweet]
            [compojure.api.sweet :as core]
            [ring.util.http-response :as resp]))

(core/GET "/30" [] (resp/ok {:result 30}))

(core/GET "/30" req
          (resp/ok {:result (:body req)}))
