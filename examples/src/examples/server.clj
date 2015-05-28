(ns examples.server
  (:gen-class)
  (:require [org.httpkit.server :as httpkit]
            [compojure.api.middleware :refer [wrap-components]]
            [com.stuartsierra.component :as component]
            [examples.thingie :refer [app]]))

(defrecord Example []
  component/Lifecycle
  (start [this]
    (assoc this :example "hello world"))
  (stop [this]
    this))

(defrecord HttpKit []
  component/Lifecycle
  (start [this]
    (println this)
    (assoc this :http-kit (httpkit/run-server
                            (wrap-components
                              #'app
                              (select-keys this [:example]))
                            {:port 3000})))
  (stop [this]
    (if-let [http-kit (:http-kit this)]
      (http-kit))
    (dissoc this :http-kit)))

(defn new-system []
  (component/system-map
    :example (->Example)
    :http-kit (component/using (->HttpKit) [:example])))

(defn -main []
  (component/start (new-system))
  (println "server started"))
