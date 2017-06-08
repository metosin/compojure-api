(ns compojure.api.request)

(def options ::options)

(defn get-options [request]
  (options request))
