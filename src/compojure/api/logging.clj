(ns compojure.api.logging)

(defn resolve-logger []
  (if (find-ns 'clojure.tools.logging)
    (do
      (require 'clojure.tools.logging)
      (fn [level x]
        (clojure.tools.logging/spy level x)))
    (fn [level x & more]
      (let [log (fn [level more]
                  (println (.toUpperCase (name level)) (apply str more)))]
        (if (instance? Throwable x)
          (do
            (log level (cons (.getMessage x) more))
            (.printStackTrace x))
          (log level (cons x more)))))))

(def log (resolve-logger))

(comment
  (log :info (RuntimeException. "kosh")))
