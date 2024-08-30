(ns compojure-api-example.clj-kondo-hooks
  (:require [compojure.api.sweet :as sweet]
            [compojure.api.core :as core]
            [ring.util.http-response :as resp]
            [schema.core :as s]
            [ring.util.http-response :refer [describe]]
            ;; intentionally blank
            ;; intentionally blank
            ;; intentionally blank
            ;; intentionally blank
            ;; intentionally blank
            ;; intentionally blank
            ))
;; intentionally blank
;; intentionally blank           
;; intentionally blank
;; intentionally blank
;; intentionally blank
;; intentionally blank
;; intentionally blank           
;; intentionally blank
;; intentionally blank
;; intentionally blank

(core/GET "/30" [] (resp/ok {:result 30}))
(core/GET "/30" [] (:ok)) ;; src/compojure_api_example/clj_kondo_hooks.clj:26:20: error: keyword :ok is called with 0 args but expects 1 or 2
(core/GET "/30" [] '(:ok))

(sweet/GET "/30" [] (resp/ok {:result 30}))
(sweet/GET "/30" [] (:ok)) ;; src/compojure_api_example/clj_kondo_hooks.clj:30:21: error: keyword :ok is called with 0 args but expects 1 or 2
(sweet/GET "/30" [] '(:ok))

(core/GET "/30" req (resp/ok {:result (:body req)}))
(core/GET "/30" [:as req] (resp/ok {:result (:body req)}))
(core/GET "/30" {:keys [body]} (resp/ok {:result body}))
(core/GET "/30" {:as req} (resp/ok {:result (:body req)}))
(core/GET "/30" {:as req} (resp/ok {:result (:body req)}))
(core/GET "/30" _ (resp/ok {:result (:body req)})) ;; src/compojure_api_example/clj_kondo_hooks.clj:38:44: error: Unresolved symbol: req

(core/PUT "/30" req (resp/ok {:result (:body req)}))

(core/routes
  (core/PUT "/" []
            :responses {200 {:schema s/Any}}
            :summary "summary"
            :query-params [{qparam :- s/Int nil}]
            :body [body (describe s/Any "description")]
            :description (str "foo" "bar")
            (ok (str qparam body))))
