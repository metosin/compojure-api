(defproject metosin/compojure-api "0.23.0"
  :description "Compojure Api"
  :url "https://github.com/metosin/compojure-api"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[prismatic/plumbing "0.4.4"]
                 [potemkin "0.4.1"]
                 [cheshire "5.5.0"]
                 [compojure "1.4.0"]
                 [prismatic/schema "0.4.4"]
                 [org.tobereplaced/lettercase "1.0.0"]
                 [metosin/ring-http-response "0.6.5"]
                 [metosin/ring-swagger "0.21.0"]
                 [metosin/schema-tools "0.5.2"]
                 [metosin/ring-middleware-format "0.6.0"]
                 [backtick "0.3.3"]
                 [metosin/ring-swagger-ui "2.1.2"]]
  :profiles {:uberjar {:aot :all
                       :ring {:handler examples.thingie/app}
                       :source-paths ["examples/src"]
                       :dependencies [[org.clojure/clojure "1.7.0"]
                                      [http-kit "2.1.19"]
                                      [com.stuartsierra/component "0.2.3"]]}
             :dev {:repl-options {:init-ns user}
                   :plugins [[lein-clojars "0.9.1"]
                             [lein-midje "3.1.3"]
                             [lein-ring "0.9.6"]
                             [funcool/codeina "0.3.0"]]
                   :dependencies [[org.clojure/clojure "1.7.0"]
                                  [peridot "0.4.1"]
                                  [javax.servlet/servlet-api "2.5"]
                                  [midje "1.7.0"]
                                  [metosin/scjsv "0.2.0"]
                                  [com.stuartsierra/component "0.2.3"]
                                  [reloaded.repl "0.1.0"]
                                  [http-kit "2.1.19"]]
                   :ring {:handler examples.thingie/app
                          :reload-paths ["src" "examples/src"]}
                   :source-paths ["examples/src" "examples/dev-src"]
                   :main examples.server}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}}
  :eastwood {:namespaces [:source-paths]
             :add-linters [:unused-namespaces]}
  :codeina {:sources ["src"]
            :output-dir "gh-pages/doc"
            :src-dir-uri "http://github.com/metosin/compojure-api/blob/master/"
            :src-linenum-anchor-prefix "L" }
  :aliases {"all" ["with-profile" "dev:dev,1.6"]
            "start-thingie" ["run"]
            "aot-uberjar" ["with-profile" "uberjar" "do" "clean," "ring" "uberjar"]
            "test-ancient" ["midje"]
            "deploy!" ^{:doc "Recompile sources, then deploy if tests succeed."}
                      ["do" ["clean"] ["midje"] ["deploy" "clojars"]]})
