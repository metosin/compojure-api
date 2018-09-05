(defproject example "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [metosin/compojure-api "2.0.0-alpha25" :exclude [compojure, metosin/muuntaja]]
                 [ring/ring "1.6.3"]
                 [compojure "1.6.1"]
                 [manifold "0.1.8"]
                 [org.clojure/core.async "0.4.474"]]
  :ring {:handler example.handler/app
         :async? true}
  :uberjar-name "server.jar"
  :profiles {:dev {:plugins [[lein-ring "0.11.0"]]}})
