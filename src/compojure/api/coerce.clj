(ns compojure.api.coerce
  (:require [schema.coerce :as sc]
            [compojure.api.middleware :as mw]
            [compojure.api.exception :as ex]
            [clojure.walk :as walk]
            [schema.utils :as su]
            [linked.core :as linked])
  (:import (java.io File)))

(defn memoized-coercer
  "Returns a memoized version of a referentially transparent coercer fn. The
  memoized version of the function keeps a cache of the mapping from arguments
  to results and, when calls with the same arguments are repeated often, has
  higher performance at the expense of higher memory use. FIFO with 10000 entries.
  Cache will be filled if anonymous coercers are used (does not match the cache)"
  []
  (let [cache (atom (linked/map))
        cache-size 10000]
    (fn [& args]
      (or (@cache args)
          (let [coercer (apply sc/coercer args)]
            (swap! cache (fn [mem]
                           (let [mem (assoc mem args coercer)]
                             (if (>= (count mem) cache-size)
                               (dissoc mem (-> mem first first))
                               mem))))
            coercer)))))

(defn cached-coercer [request]
  (or (-> request mw/get-options :coercer) sc/coercer))

;; don't use coercion for certain types
(defmulti coerce-response? identity :default ::default)
(defmethod coerce-response? ::default [_] true)
(defmethod coerce-response? File [_] false)

(defn- coerce-response* [request {:keys [status body] :as response} responses respond raise]
  (-> (when-let [schema (or (:schema (get responses status))
                            (:schema (get responses :default)))]
        (when (coerce-response? schema)
          (when-let [matchers (mw/coercion-matchers request)]
            (when-let [matcher (matchers :response)]
              (let [coercer (cached-coercer request)
                    coerce (coercer schema matcher)
                    coerced (coerce body)]
                (if (su/error? coerced)
                  (let [errors (su/error-val coerced)]
                    (raise
                      (ex-info
                        (str "Response validation failed: " (pr-str errors))
                        {:type ::ex/response-validation
                         :validation :schema
                         :in [:response :body]
                         :schema schema
                         :errors errors
                         :response response})))
                  (assoc response
                    :compojure.api.meta/serializable? true
                    :body coerced)))))))
      (or response)
      respond))

(defn coerce-response! [request response responses]
  (coerce-response* request response responses identity #(throw %)))

(defn body-coercer-middleware [handler responses]
  (fn
    ([request]
     (coerce-response! request (handler request) responses))
    ([request respond raise]
     (handler request
              (fn [response] (coerce-response* request response responses respond raise))
              raise))))

(defn coerce! [schema key type keywordize? request]
  (let [transform (if keywordize? walk/keywordize-keys identity)
        value (transform (key request))]
    (if-let [matchers (mw/coercion-matchers request)]
      (if-let [matcher (matchers type)]
        (let [coercer (cached-coercer request)
              coerce (coercer schema matcher)
              coerced (coerce value)]
          (if (su/error? coerced)
            (let [errors (su/error-val coerced)]
              (throw (ex-info
                       (str "Request validation failed: " (pr-str errors))
                       {:type ::ex/request-validation
                        :validation :schema
                        :value value
                        :in [:request key]
                        :schema schema
                        :errors errors
                        :request request})))
            coerced))
        value)
      value)))
