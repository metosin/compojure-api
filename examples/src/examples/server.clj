(ns examples.server
  (:gen-class)
  (:require [org.httpkit.server :as httpkit]
            [examples.thingie :refer [app]]))

(defn -main []
  (httpkit/run-server app {:port 8080})
  (println "server started"))
