(ns ^:no-doc compojure.api.impl.json
  "Internal JSON formatting"
  (:require [muuntaja.core :as m]))

(def muuntaja
  (m/create))
