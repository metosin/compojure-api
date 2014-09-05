(ns compojure.api.middleware
  (:require [ring.util.response :refer [response content-type redirect]]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.util.http-response :refer [bad-request internal-server-error]]
            [ring.middleware.format-response :as format-response]
            [ring.middleware.format-params :as format-params :refer [wrap-restful-params]]
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
(def mime-types (into {} (map (fn [[k x]]
                                (let [t (:enc-type x)]
                                  [k (str (:type t) "/" (:sub-type t))]))
                              format-response/format-encoders)))

(def res-mime-types (into #{} (vals mime-types)))

(def req-mime-types (into #{} (map (fn [[k _]]
                                     (k mime-types))
                                   format-params/format-wrappers)))

(defn wrap-swagger [handler]
  (fn [request]
    (-> request
        (assoc-in [:meta :consumes] req-mime-types)
        (assoc-in [:meta :produces] res-mime-types)
        handler)))

(defn handle-req-error [e handler req]
  (cond
    (instance? com.fasterxml.jackson.core.JsonParseException e)
    (bad-request {:type "parse-exception"
                  :content-type (:content-type req)
                  :message (.getMessage e)})

    :else
    (internal-server-error {:message (.getMessage e)})))

(defn serializable?
  "Predicate which return true if the response body is serializable.
   That is, return type is set by :return compojure-api key or it's not
   string, File or InputStream."
  [req {:keys [body] :as response}]
  (when response
    (or (:compojure.api.meta/serializable? response)
        (not
          (or
            (string? body)
            (instance? java.io.File body)
            (instance? java.io.InputStream body))))))

;; Version with out serializable?
(defn wrap-restful-response
  "Wrapper that tries to do the right thing with the response *:body*
  and provide a solid basis for a RESTful API. It will serialize to
  JSON, YAML, Clojure, Transit or HTML-wrapped YAML depending on Accept header.
  See wrap-format-response for more details. Recognized formats are
  *:json*, *:json-kw*, *:edn* *:yaml*, *:yaml-in-html*, *:transit-json*,
  *:transit-msgpack*."
  [handler & {:keys [handle-error formats charset binary?]
              :or {handle-error format-response/default-handle-error
                   charset format-response/default-charset-extractor
                   formats [:json :yaml :edn :clojure :yaml-in-html :transit-json :transit-msgpack]}}]
  (let [encoders (for [format formats
                       :when format
                       :let [encoder (if (map? format)
                                       format
                                       (get format-response/format-encoders (keyword format)))]
                       :when encoder]
                   encoder)]
    (format-response/wrap-format-response handler
                          :predicate serializable?
                          :encoders encoders
                          :binary? binary?
                          :charset charset
                          :handle-error handle-error)))

(defn api-middleware
  "opinionated chain of middlewares for web apis."
  [handler]
  (-> handler
      ring.middleware.http-response/catch-response
      ring.swagger.middleware/catch-validation-errors
      ex-info-support
      wrap-swagger
      (wrap-restful-params
        :formats [:json-kw :edn :yaml-kw :transit-msgpack :transit-json]
        :handle-error handle-req-error)
      (wrap-restful-response)
      wrap-keyword-params
      wrap-nested-params
      wrap-params))
