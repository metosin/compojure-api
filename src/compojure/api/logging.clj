(ns ^:no-doc compojure.api.logging
  "Internal Compojure-api logging utility"
  (:require [clojure.string :as str]))

;; Cursive-users
(declare log!)

;; use c.t.l logging if available, default to console logging
(if (find-ns 'clojure.tools.logging)
  (eval
    `(do
       (require 'clojure.tools.logging)
       (defmacro ~'log! [& ~'args]
         `(do
            (clojure.tools.logging/log ~@~'args)))))
  (let [log (fn [level more] (println (.toUpperCase (name level)) (str/join " " more)))]
    (defn log! [level x & more]
      (if (instance? Throwable x)
        (do
          (log level more)
          (.printStackTrace x))
        (log level (into [x] more))))))
