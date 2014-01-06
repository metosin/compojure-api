(ns compojure.api.dsl
  (:require [compojure.core :refer :all]
            [compojure.api.schema :as schema]
            [ring.util.response :as response]
            [compojure.api.common :refer :all]))

(defn ok
  "status 200"
  [body] (response/response body))

(defmacro GET* [path arg & body]
  (let [[parameters body] (extract-parameters body)]
    `(with-meta (GET ~path ~arg ~@body) ~parameters)))

;; FIXME: evaluates with-meta for all requests
#_(defmacro with-body [method path arg & body]
  (println body)
  (let [[parameters body] (extract-fn-parameters body)]
    (if-let [[body-name body-model body-meta] (:body parameters)]
      (let [parameters (-> parameters
                         (dissoc :body)
                         schema/purge-model-vars
                         (update-in [:parameters] conj
                           (merge
                             {:name (-> body-model schema/purge-model-var name-of .toLowerCase)
                              :description ""
                              :required "true"}
                             body-meta
                             {:paramType "body"
                              :type body-model}))
                         (update-in [:parameters] vec))]
        `(fn [req#]
           (let [{~body-name :params} req#]
             ((with-meta ('~method ~path ~arg ~@body) ~parameters) req#))))
      `(with-meta ('~method ~path ~arg ~@body) ~parameters))))

#_(defmacro POST* [path arg & body]
  `(with-body #'POST ~path ~arg ~@body))

#_(defmacro PUT* [path arg & body]
  `(with-body #'PUT ~path ~arg ~@body))

(defmacro POST* [path arg & body]
  (let [[parameters body] (extract-parameters body)]
    (if-let [[body-name body-model body-meta] (:body parameters)]
      (let [parameters (-> parameters
                         (dissoc :body)
                         schema/purge-model-vars
                         (update-in [:parameters] conj
                           (merge
                             {:name (-> body-model schema/purge-model-var name-of .toLowerCase)
                              :description ""
                              :required "true"}
                             body-meta
                             {:paramType "body"
                              :type (schema/purge-model-var body-model)}))
                         (update-in [:parameters] vec))]
        `(fn [req#]
           (let [{~body-name :params} req#]
             ((with-meta (POST ~path ~arg ~@body) ~parameters) req#))))
      `(with-meta (POST ~path ~arg ~@body) ~parameters))))

(defmacro PUT* [path arg & body]
  (let [[parameters body] (extract-parameters body)]
    (if-let [[body-name body-model body-meta] (:body parameters)]
      (let [parameters (-> parameters
                         (dissoc :body)
                         schema/purge-model-vars
                         (update-in [:parameters] conj
                           (merge
                             {:name (-> body-model schema/purge-model-var name-of .toLowerCase)
                              :description ""
                              :required "true"}
                             body-meta
                             {:paramType "body"
                              :type body-model}))
                         (update-in [:parameters] vec))]
        `(fn [req#]
           (let [{~body-name :params} req#]
             ((with-meta (PUT ~path ~arg ~@body) ~parameters) req#))))
      `(with-meta (PUT ~path ~arg ~@body) ~parameters))))

(defmacro DELETE* [path arg & body]
  (let [[parameters body] (extract-fn-parameters body)]
    `(with-meta (DELETE ~path ~arg ~@body) ~parameters)))
