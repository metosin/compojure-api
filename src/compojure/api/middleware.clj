(ns compojure.api.middleware
  (:require [ring.util.response :refer [response content-type redirect]]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.util.http-response :refer [bad-request]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.format-response :as format-response]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            ring.middleware.http-response
            ring.swagger.middleware))

(defroutes public-resource-routes
  (GET "/" [] (redirect "/index.html"))
  (route/resources "/"))

(defn public-resources
  "serves public resources for missed requests"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (or response
        ((route/resources "/") request)))))

;; make this better, Slingshot?
(defn ex-info-support
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (bad-request (ex-data e))))))

;; All mime-types supported by default mw-format
;; FIXME: Extension point to mw-format which can be used to modify req?
;; So that we can check which formats are enabled
(def mime-types (into #{}
                      (map (fn [[_ x]]
                             (let [t (:enc-type x)]
                               (str (:type t) "/" (:sub-type t))))
                           format-response/format-encoders)))

(defn wrap-swagger [handler]
  (fn [request]
    (-> request
        (assoc-in [:meta :consumes] mime-types)
        (assoc-in [:meta :produces] mime-types)
        (handler))))

(defn api-middleware
  "opinionated chain of middlewares for web apis."
  [handler]
  (-> handler
      ring.middleware.http-response/catch-response
      ring.swagger.middleware/catch-validation-errors
      ex-info-support
      wrap-swagger
      (wrap-restful-format :formats [:json-kw :edn :yaml :transit-mspack :transit-json])
      wrap-keyword-params
      wrap-nested-params
      wrap-params))
