#!/usr/bin/env bb

(ns regen-kondo-config)

(defn -main [& args]
  (spit "resources/clj-kondo.exports/metosin/compojure-api/config.edn"
        '{:hooks
          {:macroexpand
           {compojure.api.core/GET compojure.api.core/GET}}}))

(when (= *file* (System/getProperty "babashka.file")) (-main))
