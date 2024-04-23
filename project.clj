(defproject metosin/compojure-api "2.0.0-alpha32-SNAPSHOT"
  :description "Compojure Api"
  :url "https://github.com/metosin/compojure-api"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[prismatic/schema "1.4.1"]
                 [prismatic/plumbing "0.6.0"]
                 [ikitommi/linked "1.3.1-alpha1"] ;; waiting for the original
                 [metosin/jsonista "0.3.1"] ;; dependency conflicts
                 [metosin/muuntaja "0.6.10"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-joda "2.10.1"]
                 [ring/ring-core "1.12.1"]
                 [compojure "1.7.1" ]
                 [metosin/spec-tools "0.10.6"]
                 [metosin/ring-http-response "0.9.3"]
                 [metosin/ring-swagger-ui "3.52.3"]
                 [metosin/ring-swagger "0.26.2"]

                 [com.fasterxml.jackson.core/jackson-annotations "2.12.0"]
                 [clj-time "0.15.2"]
                 [joda-time "2.10.5"]
                 [riddley "0.2.0"]]
  :pedantic? :abort
  :profiles {:uberjar {:aot :all
                       :ring {:handler examples.thingie/app}
                       :source-paths ["examples/thingie/src"]
                       :dependencies [[org.clojure/clojure "1.10.1"]
                                      [http-kit "2.3.0"]
                                      [reloaded.repl "0.2.4"]
                                      [com.stuartsierra/component "0.4.0"]]}
             :dev {:plugins [[lein-clojars "0.9.1"]
                             [lein-ring "0.12.5"]
                             [funcool/codeina "0.5.0"]]
                   :dependencies [[org.clojure/clojure "1.10.1"]
                                  [org.clojure/core.async "0.6.532"]
                                  [javax.servlet/javax.servlet-api "4.0.1"]
                                  [peridot "0.5.2"]
                                  [com.stuartsierra/component "0.4.0"]
                                  [expound "0.8.2"]
                                  [metosin/jsonista "0.3.1"]
                                  [reloaded.repl "0.2.4"]
                                  [metosin/muuntaja-msgpack "0.6.6"]
                                  [metosin/muuntaja-yaml "0.6.6"]
                                  [org.immutant/immutant "2.1.10"]
                                  [http-kit "2.3.0"]
                                  [criterium "0.4.5"]]
                   :jvm-opts ["-Dcompojure.api.meta.static-context-coach={:default :assert :verbose true}"]
                   :test-paths ["test19"]
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
                        ["releases" {:url "https://clojars.org/repo"
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
  :aliases {"all" ["with-profile" "dev:dev,async"]
            "start-thingie" ["run"]
            "aot-uberjar" ["with-profile" "uberjar" "do" "clean," "ring" "uberjar"]
            "test-ancient" ["test"]
            "perf" ["with-profile" "default,dev,perf"]
            "deploy!" ^{:doc "Recompile sources, then deploy if tests succeed."}
            ["do" ["clean"] ["test"] ["deploy" "clojars"]]})
