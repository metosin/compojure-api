(ns ^:no-doc compojure.api.impl.logging
  "Internal Compojure-api logging utility"
  (:require [clojure.string :as str]))

;; Cursive-users
(declare log!)

;; use c.t.l logging if available, default to console logging
(try
  (eval
    `(do
       (require 'clojure.tools.logging)
       (defmacro ~'log! [& ~'args]
         `(clojure.tools.logging/log ~@~'args))))
  (catch Exception _
    (let [log (fn [level more] (println (.toUpperCase (name level)) (str/join " " more)))]
      (defn log! [level x & more]
        (if (instance? Throwable x)
          (do
            (log level more)
            (.printStackTrace ^Throwable x))
          (log level (into [x] more))))
      (log! :warn "clojure.tools.logging not found on classpath, logging to console."))))
