(ns compojure.api.dsl
  (:require [compojure.core :refer :all]
            [compojure.api.schema :as schema]
            [compojure.api.common :refer :all]))

(defmacro GET* [path arg & body]
  (let [[parameters body] (extract-fn-parameters body)]
    `(with-meta (GET ~path ~arg ~@body) ~parameters)))

;; FIXME: evaluates with-meta for all requests
(defmacro POST* [path arg & body]
  (let [[parameters body] (extract-fn-parameters body)]
    (if-let [[body-name body-model body-meta] (:body parameters)]
      (let [parameters (-> parameters
                         (dissoc :body)
                         schema/purge-model-vars
                         (update-in [:parameters] conj (merge
                                                         {:name (-> body-model schema/purge-model-var name-of .toLowerCase)
                                                          :description ""
                                                          :required "true"}
                                                         body-meta
                                                         {:paramType "body"
                                                          :type body-model}))
                         (update-in [:parameters] vec))]
        `(fn [req#]
           (let [{~body-name :params} req#]
             ((with-meta (POST ~path ~arg ~@body) ~parameters) req#))))
      `(with-meta (POST ~path ~arg ~@body) ~parameters))))
