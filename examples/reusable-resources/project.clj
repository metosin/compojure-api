(defproject example "0.1.0-SNAPSHOT"
  :description "Example on reusable Compojure-api resources"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [metosin/compojure-api "1.1.4"]]
  :ring {:handler example.handler/app}
  :uberjar-name "server.jar"
  :profiles {:dev {:plugins [[ikitommi/lein-ring "0.9.8-SNAPSHOT"]]}})
