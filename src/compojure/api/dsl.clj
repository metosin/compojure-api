(ns compojure.api.dsl
  (:require [compojure.core :refer :all]
            [compojure.api.schema :as schema]
            [compojure.api.common :refer :all]))

(defmacro GET* [path arg & body]
  (let [[parameters body] (extract-fn-parameters body)]
    `(with-meta (GET ~path ~arg ~@body) ~parameters)))

(defmacro POST* [path arg & body]
  (let [[parameters body] (extract-fn-parameters body)]
    (if-let [[body-name body-model body-meta] (:body parameters)]
      (let [parameters (-> parameters
                         (dissoc :body)
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
             (println "BODY:" ~body-name)
             (with-meta (POST ~path ~arg ~@body) ~parameters))))
      `(with-meta (POST ~path ~arg ~@body) ~parameters))))

(defmacro TEST* [& body]
  (let [[parameters [body]] (extract-fn-parameters body)
        [body-name body-model body-meta] (:body parameters)
        parameters (dissoc parameters :body)]
    (println parameters)
    `(fn [req#]
       (let [{~body-name :params} req#]
         (do ~@body)))))
