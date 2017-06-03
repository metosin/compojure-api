(ns compojure.api.coercion
  (:require [compojure.api.middleware :as mw]
            [compojure.api.exception :as ex]
            [clojure.walk :as walk]))

(defprotocol Coercion
  (get-name [this])
  (coerce-request [this model value type format request])
  (coerce-response [this model value type format request]))

(defrecord CoercionError [])

(defmulti named-coercion identity :default ::default)
(defmethod named-coercion ::default [x]
  (throw (ex-info (str "cant find named-coercion for " x) {:name x})))

(defn find-coercion [coercion]
  (cond
    (nil? coercion) nil
    (keyword? coercion) (named-coercion coercion)
    (instance? Coercion coercion) coercion
    :else (throw (ex-info (str "invalid coercion " coercion) {:coercion coercion}))))

(defn coerce-request! [model in type keywordize? request]
  (let [transform (if keywordize? walk/keywordize-keys identity)
        value (transform (in request))]
    (if-let [coercion (-> request
                          (mw/coercion)
                          (find-coercion))]
      (let [format (-> request :muuntaja/request :format)
            result (coerce-request coercion model value type format request)]
        (if (instance? CoercionError result)
          (throw (ex-info
                   (str "Request validation failed: " (pr-str result))
                   (merge
                     (into {} result)
                     {:type ::ex/request-validation
                      :coercion (get-name coercion)
                      :value value
                      :in [:request in]
                      :request request})))
          result))
      value)))

(defn coerce-response! [request {:keys [status body] :as response} responses]
  (when-let [model (or (:schema (get responses status))
                       (:schema (get responses :default)))]
    (if-let [coercion (-> request
                          (mw/coercion)
                          (find-coercion))]
      (let [result (coerce-response coercion model body :response nil response)]
        (if (instance? CoercionError result)
          (throw (ex-info
                   (str "Response validation failed: " (pr-str result))
                   (merge
                     (into {} result)
                     {:type ::ex/response-validation
                      :coercion (get-name coercion)
                      :value body
                      :in [:request :body]
                      :request request
                      :response response})))
          (assoc response
            :compojure.api.meta/serializable? true
            :body result)))
      response)))

;;
;; middleware
;;

(defn wrap-coerce-response [handler responses]
  (fn
    ([request]
     (coerce-response! request (handler request) responses))
    ([request respond raise]
     (handler request
              (fn [response]
                (respond (coerce-response! request response responses)))
              raise))))
