(ns ^:no-doc compojure.api.impl.json
  "Internal JSON formatting"
  (:require [muuntaja.core :as m]))

(def instance
  (m/create))

(defn generate-string [x]
  (slurp (m/encode instance "application/json" x)))

(defn parse-string [x]
  (m/decode instance "application/json" x))
