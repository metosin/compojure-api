(defproject metosin/compojure-api "0.24.1-SNAPSHOT"
  :description "Compojure Api"
  :url "https://github.com/metosin/compojure-api"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[prismatic/plumbing "0.5.2"]
                 [potemkin "0.4.1"]
                 [cheshire "5.5.0"]
                 [compojure "1.4.0"]
                 [prismatic/schema "1.0.3"]
                 [org.tobereplaced/lettercase "1.0.0"]
                 [frankiesardo/linked "1.2.6"]
                 [metosin/ring-http-response "0.6.5"]
                 [metosin/ring-swagger "0.22.1-SNAPSHOT"]
                 [metosin/schema-tools "0.7.0"]
                 [ring-middleware-format "0.7.0"]
                 [backtick "0.3.3"]
                 [metosin/ring-swagger-ui "2.1.3-2"]]
  :profiles {:uberjar {:aot :all
                       :ring {:handler examples.thingie/app}
                       :source-paths ["examples/src"]
                       :dependencies [[org.clojure/clojure "1.7.0"]
                                      [http-kit "2.1.19"]
                                      [com.stuartsierra/component "0.3.0"]]}
             :dev {:repl-options {:init-ns user}
                   :plugins [[lein-clojars "0.9.1"]
                             [lein-midje "3.2"]
                             [lein-ring "0.9.7"]
                             [funcool/codeina "0.3.0"]]
                   :dependencies [[org.clojure/clojure "1.7.0"]
                                  [peridot "0.4.1"]
                                  [javax.servlet/servlet-api "2.5"]
                                  [midje "1.8.2"]
                                  [metosin/scjsv "0.2.0"]
                                  [com.stuartsierra/component "0.3.0"]
                                  [reloaded.repl "0.2.1"]
                                  [http-kit "2.1.19"]
                                  ; Required when using with Java 1.6
                                  [org.codehaus.jsr166-mirror/jsr166y "1.7.0"]]
                   :ring {:handler examples.thingie/app
                          :reload-paths ["src" "examples/src"]}
                   :source-paths ["examples/src" "examples/dev-src"]
                   :main examples.server}
             :logging {:dependencies [[org.clojure/tools.logging "0.3.1"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0-RC2"]]}}
  :eastwood {:namespaces [:source-paths]
             :add-linters [:unused-namespaces]}
  :codeina {:sources ["src"]
            :target "gh-pages/doc"
            :src-dir-uri "http://github.com/metosin/compojure-api/blob/master/"
            :src-linenum-anchor-prefix "L"}
  :aliases {"all" ["with-profile" "dev:dev,logging:dev,1.8"]
            "start-thingie" ["run"]
            "aot-uberjar" ["with-profile" "uberjar" "do" "clean," "ring" "uberjar"]
            "test-ancient" ["midje"]
            "deploy!" ^{:doc "Recompile sources, then deploy if tests succeed."}
                      ["do" ["clean"] ["midje"] ["deploy" "clojars"]]})
