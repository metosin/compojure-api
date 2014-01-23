(ns compojure.api.sweet
  (:require [potemkin :refer :all]))

(import-vars
  [compojure.api.core
    ok
    ->Long
    defapi
    with-middeware]
  [clojure.data
    diff])

