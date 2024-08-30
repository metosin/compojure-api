(ns compojure-api-dev.expand-kondo-feature)

(defn visit-forms-in-file [file]
  {:pre [(string? file)]}
  (spit (str/replace "\n" new-forms)
        file))

(defn -main [& files]
  (run! visit-forms-in-file files))
