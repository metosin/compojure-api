;; 1.1.x
(ns compojure.api.coerce
  (:require [schema.coerce :as sc]
            [compojure.api.middleware :as mw]
            [compojure.api.exception :as ex]
            [clojure.walk :as walk]
            [schema.utils :as su]
            [linked.core :as linked]))

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

(defn coerce-response! [request {:keys [status] :as response} responses]
  (-> (when-let [schema (or (:schema (get responses status))
                            (:schema (get responses :default)))]
        (when-let [matchers (mw/coercion-matchers request)]
          (when-let [matcher (matchers :response)]
            (let [coercer (cached-coercer request)
                  coerce (coercer schema matcher)
                  body (coerce (:body response))]
              (if (su/error? body)
                (throw (ex-info
                        (str "Response validation failed: " (su/error-val body))
                        (assoc body :type ::ex/response-validation
                               :response response)))
                (assoc response
                  :compojure.api.meta/serializable? true
                  :body body))))))
      (or response)))

(defn body-coercer-middleware [handler responses]
  (fn [request]
    (coerce-response! request (handler request) responses)))

(defn coerce! [schema key type request]
  (let [value (walk/keywordize-keys (key request))]
    (if-let [matchers (mw/coercion-matchers request)]
      (if-let [matcher (matchers type)]
        (let [coercer (cached-coercer request)
              coerce (coercer schema matcher)
              result (coerce value)]
          (if (su/error? result)
            (throw (ex-info
                     (str "Request validation failed: " (su/error-val result))
                     (assoc result :type ::ex/request-validation)))
            result))
        value)
      value)))
