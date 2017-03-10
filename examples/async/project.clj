(defproject example "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [metosin/compojure-api "1.2.0-SNAPSHOT" :exclude [compojue, metosin/muuntaja]]
                 [metosin/muuntaja "0.2.0-SNAPSHOT"]
                 [ring/ring "1.6.0-beta7"]
                 [compojure "1.6.0-beta3"]
                 [manifold "0.1.6-alpha6"]]
  :ring {:handler example.handler/app
         :async? true}
  :uberjar-name "server.jar"
  :profiles {:dev {:plugins [[lein-ring "0.11.0"]]}})
