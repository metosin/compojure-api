(ns compojure.api.logging
  (:require [clojure.string :as str]))

;; default to console logging
(defn log! [level x & more]
  (let [log (fn [level more] (println (.toUpperCase (name level)) (str/join " - " more)))]
    (if (instance? Throwable x)
      (do
        (log level more)
        (.printStackTrace x))
      (log level (into [x] more)))))

;; use c.t.l logging if available
(if (find-ns 'clojure.tools.logging)
  (eval
    `(do
       (require 'clojure.tools.logging)
       (defmacro ~'log! [& ~'args]
         `(do
            (clojure.tools.logging/log ~@~'args))))))
