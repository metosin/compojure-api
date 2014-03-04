(ns compojure.api.common
  (:use [clojure.walk :as walk]))

(defn path-vals
  "Returns vector of tuples containing path vector to the value and the value."
  [m]
  (letfn
    [(pvals [l p m]
       (reduce
         (fn [l [k v]]
           (if (map? v)
             (pvals l (conj p k) v)
             (cons [(conj p k) v] l)))
         l m))]
    (pvals [] [] m)))

(defn assoc-in-path-vals
  "Re-created a map from it's path-vals extracted with (path-vals)."
  [c] (reduce (partial apply assoc-in) {} c))

(defn ->map
  "Converts a map-like form (list of tuples, record a map) into a map."
  [m] (into {} m))

(defmacro re-resolve
  "Extracts original var from a (potemkined) var or a symbol or returns nil"
  [x]
  (let [evaluated (if (symbol? x) x (eval x))
        resolved  (cond
                    (var? evaluated)    evaluated
                    (symbol? evaluated) (resolve evaluated)
                    :else       nil)
        metadata  (meta resolved)]
    (if metadata
      (let [s (symbol (str (:ns metadata) "/" (:name metadata)))]
        `(var ~s)))))

(defn eval-re-resolve [x] (eval `(re-resolve ~x)))

(defn ->Long [s] (if (string? s) (java.lang.Long/parseLong s) (Long. s)))

;;
;; meta-data-container
;;

(defmacro meta-container [meta & form]
  `(do ~@form))

(defn unwrap-meta-container [container]
  {:post [(map? %)]}
  (or
    (if (sequential? container)
      (let [[sym meta-data] container]
        (if (and (symbol? sym) (= #'meta-container (resolve sym)))
          meta-data)))
    {}))

(def meta-container? #{#'meta-container})


