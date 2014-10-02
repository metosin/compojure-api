(ns compojure.api.legacy
  (:require compojure.core
            [potemkin :refer [import-vars]]))

(import-vars

  ;; compojure routing
  [compojure.core

   defroutes

   GET
   ANY
   HEAD
   PATCH
   DELETE
   OPTIONS
   POST
   PUT])
