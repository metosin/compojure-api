(ns compojure-api-example.clj-kondo-hooks
  (:require ;[compojure.api.sweet :as sweet]
            [compojure.api.core :as core]
            [ring.util.http-response :as resp]))

(core/GET "/30" [] (resp/ok {:result 30}))
(core/GET "/30" [] (:ok)) ;; src/compojure_api_example/clj_kondo_hooks.clj:7:20: error: keyword :ok is called with 0 args but expects 1 or 2
(core/GET "/30" [] '(:ok))

(core/GET "/30" req (resp/ok {:result (:body req)}))
(core/GET "/30" [:as req] (resp/ok {:result (:body req)}))
(core/GET "/30" {:keys [body]} (resp/ok {:result body}))
(core/GET "/30" {:as req} (resp/ok {:result (:body req)}))
(core/GET "/30" {:as req} (resp/ok {:result (:body req)}))
