(ns example.domain
  (:require [schema.core :as s]
            [plumbing.core :as p]
            [clojure.string :as str]))

(s/defschema Pizza
  {:id s/Int
   :name s/Str
   (s/optional-key :description) s/Str
   :size (s/enum :L :M :S)
   :origin {:country (s/enum :FI :PO)
            :city s/Str}})

(s/defschema Kebab
  {:id s/Int
   :name s/Str
   :type (s/enum :doner :shish :souvlaki)})

(s/defschema Sausage
  {:id s/Int
   :type (s/enum :musta :jauho)
   :meat s/Int})

(s/defschema Beer
  {:id s/Int
   :type (s/enum :ipa :apa)})

(defn entities []
  (p/for-map [[n v] (ns-publics 'example.domain)
              :when (s/schema-name @v)]
    (str/lower-case n) @v))
