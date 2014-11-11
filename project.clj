(defproject metosin/compojure-api "0.16.4"
  :description "Compojure Api"
  :url "https://github.com/metosin/compojure-api"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [prismatic/plumbing "0.3.5"]
                 [potemkin "0.3.11"]
                 [cheshire "5.3.1"]
                 [compojure "1.2.1"]
                 [prismatic/schema "0.3.2"]
                 [metosin/ring-http-response "0.5.1"]
                 [metosin/ring-swagger "0.14.1"]
                 [org.tobereplaced/lettercase "1.0.0"]
                 [ring-middleware-format "0.4.0"]]
  :profiles {:thingie {:ring {:handler examples.thingie/app
                              :reload-paths ["src" "examples/src"]}
                       :source-paths ["examples/src"]
                       :main examples.server
                       :dependencies [[metosin/ring-swagger-ui "2.0.17"]
                                      [http-kit "2.1.19"]]}
             :uberjar {:aot :all}
             :dev {:ring {:handler examples.handler/app}
                   :plugins [[lein-clojars "0.9.1"]
                             [lein-midje "3.1.3"]
                             [lein-ring "0.8.13"]]
                   :dependencies [[peridot "0.3.0"]
                                  [javax.servlet/servlet-api "2.5"]
                                  [midje "1.6.3"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0-alpha2"]]}}
  :eastwood {:namespaces [:source-paths]
             :add-linters [:unused-namespaces]}
  :aliases {"all" ["with-profile" "dev:dev,1.7"]
            "start-thingie"    ["with-profile" "thingie" "ring" "server"]
            "http-kit-thingie" ["with-profile" "thingie" "run"]
            "aot-uberjar"      ["with-profile" "thingie,uberjar" "do" "clean," "ring" "uberjar"]
            "test-ancient"     ["midje"]
            "deploy!"          ^{:doc "Recompile sources, then deploy if tests succeed."}
            ["do" ["clean"] ["midje"] ["deploy" "clojars"]]})
