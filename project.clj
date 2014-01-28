(defproject metosin/compojure-api "0.0.1"
  :description "Compojure Api"
  :url "https://github.com/metosin/compojure-api"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [potemkin "0.3.4"]
                 [cheshire "5.3.1"]
                 [camel-snake-kebab "0.1.2"]
                 [prismatic/schema "0.2.0"]
                 [metosin/ring.swagger-ui "0.0.1"]
                 [compojure "1.1.6"]]
  :plugins [[lein-ring "0.8.7"]]
  :ring {:handler compojure.api.example.handler/app}
  :profiles {:dev {:plugins [[lein-clojars "0.9.1"]
                             [lein-midje "3.1.1"]]
                   :dependencies [[ring-mock "0.1.5"]
                                  [javax.servlet/servlet-api "2.5"]
                                  [midje "1.6.0"]
                                  [clj-http "0.7.8" :exclusions [commons-codec]]]}})
