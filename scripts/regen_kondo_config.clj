#!/usr/bin/env bb

(ns regen-kondo-config
  (:require [clojure.string :as str]))

(def renames
  ;; rename to .clj
  {"src/compojure/api/common.cljc" "resources/clj-kondo.exports/metosin/compojure-api/compojure/api/common.clj"
   "src/compojure/api/core.cljc" "resources/clj-kondo.exports/metosin/compojure-api/compojure/api/core.clj"
   "src/compojure/api/meta.cljc" "resources/clj-kondo.exports/metosin/compojure-api/compojure/api/meta.clj"})


(defn -main [& args]
  (doseq [[from to] renames]
    (spit to
          (str/replace (slurp from) ":clj-kondo" ":default")))
  (spit "resources/clj-kondo.exports/metosin/compojure-api/config.edn"
        '{:linters {:unresolved-namespace {:exclude [(compojure.api.routes)]}}
          :hooks
          {:macroexpand
           {compojure.api.core/GET compojure.api.core/GET}}}))

(when (= *file* (System/getProperty "babashka.file")) (-main))
