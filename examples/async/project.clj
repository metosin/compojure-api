(defproject example "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [metosin/compojure-api "1.2.0-SNAPSHOT" :exclude [compojure, metosin/muuntaja]]
                 [ring/ring "1.6.0-RC1"]
                 [compojure "1.6.0-beta3"]
                 [manifold "0.1.6-alpha6"]
                 [org.clojure/core.async "0.3.441"]]
  :ring {:handler example.handler/app
         :async? true}
  :uberjar-name "server.jar"
  :profiles {:dev {:plugins [[lein-ring "0.11.0"]]}})
