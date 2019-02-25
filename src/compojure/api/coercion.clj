(ns compojure.api.coercion
  (:require [clojure.walk :as walk]
            [compojure.api.exception :as ex]
            [compojure.api.request :as request]
            [compojure.api.coercion.core :as cc]
            [compojure.api.coercion.schema]
            [compojure.api.coercion.spec])
  (:import (compojure.api.coercion.core CoercionError)))

(def default-coercion :schema)

(defn set-request-coercion [request coercion]
  (assoc request ::request/coercion coercion))

(defn get-request-coercion [request]
  (if-let [entry (find request ::request/coercion)]
    (val entry)
    default-coercion))

(defn resolve-coercion [coercion]
  (cond
    (nil? coercion) nil
    (keyword? coercion) (cc/named-coercion coercion)
    (satisfies? cc/Coercion coercion) coercion
    :else (throw (ex-info (str "invalid coercion " coercion) {:coercion coercion}))))

(defn get-apidocs [mayby-coercion spec info]
  (if-let [coercion (resolve-coercion mayby-coercion)]
    (cc/get-apidocs coercion spec info)))

(defn coerce-request! [model in type keywordize? open? request]
  (let [transform (if keywordize? walk/keywordize-keys identity)
        value (transform (in request))]
    (if-let [coercion (-> request
                          (get-request-coercion)
                          (resolve-coercion))]
      (let [model (if open? (cc/make-open coercion model) model)
            format (some-> request :muuntaja/request :format)
            result (cc/coerce-request coercion model value type format request)]
        (if (instance? CoercionError result)
          (throw (ex-info
                   (str "Request validation failed: " (pr-str result))
                   (merge
                     (into {} result)
                     {:type ::ex/request-validation
                      :coercion coercion
                      :value value
                      :in [:request in]
                      :request request})))
          result))
      value)))

(defn coerce-response! [request {:keys [status body] :as response} responses]
  (if-let [model (or (:schema (get responses status))
                     (:schema (get responses :default)))]
    (if-let [coercion (-> request
                          (get-request-coercion)
                          (resolve-coercion))]
      (let [format (or (-> response :muuntaja/content-type)
                       (some-> request :muuntaja/response :format))
            accept? (cc/accept-response? coercion model)]
        (if accept?
          (let [result (cc/coerce-response coercion model body :response format response)]
            (if (instance? CoercionError result)
              (throw (ex-info
                       (str "Response validation failed: " (pr-str result))
                       (merge
                         (into {} result)
                         {:type ::ex/response-validation
                          :coercion coercion
                          :value body
                          :in [:response :body]
                          :request request
                          :response response})))
              (assoc response
                :compojure.api.meta/serializable? true
                :body result)))
          response))
      response)
    response))

;;
;; middleware
;;

(defn wrap-coerce-response [handler responses]
  (fn
    ([request]
     (coerce-response! request (handler request) responses))
    ([request respond raise]
     (handler
       request
       (fn [response]
         (try
           (respond (coerce-response! request response responses))
           (catch Exception e
             (raise e))))
       raise))))
