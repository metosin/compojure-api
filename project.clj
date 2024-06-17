(defproject metosin/compojure-api "1.1.15-SNAPSHOT"
  :description "Compojure Api"
  :url "https://github.com/metosin/compojure-api"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[prismatic/schema "1.1.12"]
                 [prismatic/plumbing "0.5.5"]
                 [ikitommi/linked "1.3.1-alpha1"] ;; waiting for the original
                 [metosin/muuntaja "0.6.6"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-joda "2.10.1"]
                 [ring/ring-core "1.8.0"]
                 [compojure "1.6.1" ]
                 [metosin/spec-tools "0.10.6"]
                 [metosin/ring-http-response "0.9.1"]
                 [metosin/ring-swagger-ui "3.24.3"]
                 [metosin/ring-swagger "1.0.0"]

                 ;; Fix dependency conflicts
                 [clj-time "0.15.2"]
                 [joda-time "2.10.5"]
                 [riddley "0.2.0"]]
  :pedantic? :abort
  :profiles {:uberjar {:aot :all
                       :ring {:handler examples.thingie/app}
                       :source-paths ["examples/thingie/src"]
                       :dependencies [[org.clojure/clojure "1.9.0"]
                                      [http-kit "2.3.0"]
                                      [reloaded.repl "0.2.4"]
                                      [com.stuartsierra/component "0.4.0"]]}
             :dev {:plugins [[lein-clojars "0.9.1"]
                             [lein-midje "3.2.1"]
                             [lein-ring "0.12.5"]
                             [funcool/codeina "0.5.0"]]
                   :dependencies [[org.clojure/clojure "1.9.0"]
                                  [org.clojure/core.unify "0.6.0"]
                                  [org.clojure/core.async "0.6.532"]
                                  [javax.servlet/javax.servlet-api "4.0.1"]
                                  [peridot "0.5.2"]
                                  [com.stuartsierra/component "0.4.0"]
                                  [expound "0.8.2"]
                                  [metosin/jsonista "0.2.5"]
                                  [reloaded.repl "0.2.4"]
                                  [midje "1.9.9" :exclusions [commons-codec org.clojure/tools.namespace]]
                                  [metosin/muuntaja-msgpack "0.6.6"]
                                  [metosin/muuntaja-yaml "0.6.6"]
                                  [org.immutant/immutant "2.1.10"]
                                  [http-kit "2.3.0"]
                                  [criterium "0.4.5"]
                                  ;; compojure.api.integration-test
                                  [ring-middleware-format "0.7.4" :exclusions [org.clojure/core.memoize]]]
                   :ring {:handler examples.thingie/app
                          :reload-paths ["src" "examples/thingie/src"]}
                   :source-paths ["examples/thingie/src" "examples/thingie/dev-src"]
                   :main examples.server}
             :perf {:jvm-opts ^:replace ["-server"
                                         "-Xmx4096m"
                                         "-Dclojure.compiler.direct-linking=true"]}
             :logging {:dependencies [[org.clojure/tools.logging "0.5.0"]
                                      [org.slf4j/jcl-over-slf4j "1.7.30"]
                                      [org.slf4j/jul-to-slf4j "1.7.30"]
                                      [org.slf4j/log4j-over-slf4j "1.7.30"]
                                      [ch.qos.logback/logback-classic "1.2.3" ]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.1"]]}
             :1.11 {:dependencies [[org.clojure/clojure "1.11.3"]]}
             :1.12 {:dependencies [[org.clojure/clojure "1.12.0-alpha11"]]}
             :async {:jvm-opts ["-Dcompojure-api.test.async=true"]
                     :dependencies [[manifold "0.1.8" :exclusions [org.clojure/tools.logging]]]}}
  :eastwood {:namespaces [:source-paths]
             :add-linters [:unused-namespaces]}
  :codeina {:sources ["src"]
            :target "gh-pages/doc"
            :src-uri "http://github.com/metosin/compojure-api/blob/master/"
            :src-uri-prefix "#L"}
  :deploy-repositories [["snapshot" {:url "https://clojars.org/repo"
                                     :username [:gpg :env/clojars_user]
                                     :password [:gpg :env/clojars_token]
                                     :sign-releases false}]
                        ["release" {:url "https://clojars.org/repo"
                                    :username [:gpg :env/clojars_user]
                                    :password [:gpg :env/clojars_token]
                                    :sign-releases false}]]
  :release-tasks [["clean"]
                  ["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["deploy" "release"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :aliases {"all" ["with-profile" "dev:dev,logging:dev,1.10:dev,1.11:dev,1.12"]
            "start-thingie" ["run"]
            "aot-uberjar" ["with-profile" "uberjar" "do" "clean," "ring" "uberjar"]
            "test-ancient" ["midje"]
            "perf" ["with-profile" "default,dev,perf"]
            "deploy!" ^{:doc "Recompile sources, then deploy if tests succeed."}
            ["do" ["clean"] ["midje"] ["deploy" "clojars"]]})
