(ns compojure.api.legacy
  (:require [potemkin :refer [import-vars]]
            compojure.core))

(import-vars

  ;; compojure routing
  [compojure.core

   GET
   ANY
   HEAD
   PATCH
   DELETE
   OPTIONS
   POST
   PUT])
