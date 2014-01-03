(ns compojure.api.dsl
  (:require [compojure.core :refer :all]
            [compojure.api.common :refer :all]))

(defmacro GET* [path arg & body]
  (let [[parameters [body]] (extract-fn-parameters body)]
    `(with-meta (GET ~path ~arg ~@body) ~parameters)))

(defmacro POST* [path arg & body]
  (let [[parameters [body]] (extract-fn-parameters body)]
    (if-let [[body-name body-model body-meta] (:body parameters)]
      (let [parameters (-> parameters
                         (dissoc :body)
                         (update-in [:parameters] conj {:paramType "body"
                                                        :name (-> body-model str .toLowerCase)
                                                        :description "updated pizza"
                                                        :required "true"
                                                        :type body-model})
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
