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

(defmacro fn->
  "Creates a function that threads on input with some->"
  [& body] `(fn [x#] (some-> x# ~@body)))

(defmacro fn->>
  "Creates a function that threads on input with some->>"
  [& body] `(fn [x#] (some->> x# ~@body)))

(defn ->map
  "Converts a map-like form (list of tuples, record a map) into a map."
  [m] (into {} m))

(defn record?
  "Is a record?"
  [x] (instance? clojure.lang.IRecord x))

(defn prewalk-record->map
  "Prewalks a form converting all records into maps"
  [x] (walk/prewalk (fn [x] (if (record? x) (->map x) x)) x))

(defn map-def
  "Returns a (fn [x]) which executes (f x) if the parameter x
   is a map. Otherwise return the x untouched."
  [f] (fn [x] (if (map? x) (f x) x)))

(defmacro map-defn
  "Returns a (fn b) which executes body if the parameter
   is a map. Otherwise return the x untouched."
  [b & body]
  (assert (and (vector? b) (= 1 (count b))))
  `(fn [x#] (if (map? x#) (let [~(first b) x#] ~@body) x#)))

(defn remove-empty-keys
  "removes empty keys from a map"
  [m] (into {} (filter (fn-> second nil? not) m)))

(defn name-of [x]
  (cond
    (var? x) (-> x meta :name name)
    (nil? x) nil
    :else    (name x)))

(defn value-of [x]
  (if (var? x) (var-get x) x))

(defn extract-fn-parameters
  "extracts key & value pairs from beginning of a list until
   a list is found."
  [form]
  (let [parameters (->> form (take-while (comp not list?)) (apply hash-map))
        form (drop (* 2 (count parameters)) form)]
    [parameters form]))

(defn extract-map-parameters
  "extract possible map from beginning of a seq if it has more elements."
  [c] {:pre [(sequential? c)]}
  (if (and (map? (first c)) (> (count c) 1)) [(first c) (rest c)] [{} c]))

(defn extract-parameters
  "Extract parameters from head of the list. Parameters can be:
     1) a map (if followed by any form) [{:a 1 :b 2} :body] => {:a 1 :b 2}
     2) list of keywords & values   [:a 1 :b 2 :body] => {:a 1 :b 2}
     3) else => {}
   Returns a tuple with parameters and body without the parameters"
  [c]
  {:pre [(sequential? c)]}
  (if (and (map? (first c)) (> (count c) 1))
    [(first c) (rest c)]
    (if (keyword? (first c))
      (let [parameters (->> c
                         (partition 2)
                         (take-while (comp keyword? first))
                         (mapcat identity)
                         (apply hash-map))
            form       (drop (* 2 (count parameters)) c)]
        [parameters form])
      [{} c])))

(defn ->Long [s] (java.lang.Long/parseLong s))
