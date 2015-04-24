(defproject metosin/compojure-api "0.20.0"
  :description "Compojure Api"
  :url "https://github.com/metosin/compojure-api"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [prismatic/plumbing "0.4.2"]
                 [potemkin "0.3.13"]
                 [cheshire "5.4.0"]
                 [compojure "1.3.3"]
                 [prismatic/schema "0.4.1"]
                 [org.tobereplaced/lettercase "1.0.0"]
                 [metosin/ring-http-response "0.6.1"]
                 [metosin/ring-swagger "0.20.0"]
                 [metosin/ring-middleware-format "0.6.0"]
                 [backtick "0.3.2"]
                 [metosin/ring-swagger-ui "2.1.1-M2"]]
  :profiles {:uberjar {:aot :all
                       :ring {:handler examples.thingie/app}
                       :source-paths ["examples/src"]
                       :dependencies [[http-kit "2.1.19"]]}
             :dev {:plugins [[lein-clojars "0.9.1"]
                             [lein-midje "3.1.3"]
                             [lein-ring "0.9.3"]]
                   :dependencies [[peridot "0.3.1"]
                                  [javax.servlet/servlet-api "2.5"]
                                  [midje "1.7.0-SNAPSHOT"]
                                  [metosin/scjsv "0.2.0"]
                                  [http-kit "2.1.19"]]
                   :ring {:handler examples.thingie/app
                          :reload-paths ["src" "examples/src"]}
                   :source-paths ["examples/src"]
                   :main examples.server}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0-beta1"]]}}
  :eastwood {:namespaces [:source-paths]
             :add-linters [:unused-namespaces]}
  :aliases {"all" ["with-profile" "dev:dev,1.7"]
            "start-thingie" ["ring" "server"]
            "http-kit-thingie" ["run"]
            "aot-uberjar" ["with-profile" "uberjar" "do" "clean," "ring" "uberjar"]
            "test-ancient" ["midje"]
            "deploy!" ^{:doc "Recompile sources, then deploy if tests succeed."}
["do" ["clean"] ["midje"] ["deploy" "clojars"]]})
