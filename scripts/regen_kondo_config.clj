#!/usr/bin/env bb

(ns regen-kondo-config
  (:require [clojure.string :as str]))

(def renames
  ;; rename to .clj
  {"src/compojure/api/common.cljc" "resources/clj-kondo.exports/metosin/compojure-api/compojure/api/common.clj"
   "src/compojure/api/core.cljc" "resources/clj-kondo.exports/metosin/compojure-api/compojure/api/core.clj"
   "src/compojure/api/meta.cljc" "resources/clj-kondo.exports/metosin/compojure-api/compojure/api/meta.clj"
   "dev/compojure_api_kondo_hooks/compojure/core.clj" "resources/clj-kondo.exports/metosin/compojure-api/compojure_api_kondo_hooks/compojure/core.clj"
   "dev/compojure_api_kondo_hooks/plumbing/core.clj" "resources/clj-kondo.exports/metosin/compojure-api/compojure_api_kondo_hooks/plumbing/core.clj"
   "dev/compojure_api_kondo_hooks/schema/macros.clj" "resources/clj-kondo.exports/metosin/compojure-api/compojure_api_kondo_hooks/schema/macros.clj"
   "dev/compojure_api_kondo_hooks/plumbing/fnk/impl.clj" "resources/clj-kondo.exports/metosin/compojure-api/compojure_api_kondo_hooks/plumbing/fnk/impl.clj"
   })

(def restructured-macro-names
  '#{context GET ANY HEAD PATCH DELETE OPTIONS POST PUT})

(defn -main [& args]
  (doseq [[from to] renames]
    (spit to
          (str/replace (slurp from) ":clj-kondo" ":default #_\"the redundant :default is intentional, see ./scripts/regen_kondo_config.clj\"")))
  (spit "resources/clj-kondo.exports/metosin/compojure-api/config.edn"
        {:hooks
         {:macroexpand
          (reduce
            (fn [m n]
              (let [core-macro (symbol "compojure.api.core" (name n))
                    sweet-macro (symbol "compojure.api.sweet" (name n))]
                (-> m
                    (assoc core-macro core-macro
                           sweet-macro core-macro))))
            {} restructured-macro-names)}}))

(when (= *file* (System/getProperty "babashka.file")) (-main))
