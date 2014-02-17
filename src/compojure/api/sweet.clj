(ns compojure.api.sweet
  (:require [potemkin :refer [import-vars]]
            compojure.core
            compojure.api.core
            compojure.api.routes
            compojure.api.swagger))

(import-vars

  ;; compojure routing
  [compojure.core

   let-request
   routing
   routes
   context
   let-routes]

  ;; with enchanced defroutes
  [compojure.api.routes

   defroutes]

  ;; with enchanced methods
  [compojure.api.core

   GET*
   ANY*
   HEAD*
   PATCH*
   DELETE*
   OPTIONS*
   POST*
   PUT*]

   ;; routing
  [compojure.api.core

    defapi
    with-middleware]

  ;; swaggered
  [compojure.api.swagger

   swagger-ui
   swagger-docs
   swaggered]

  ;; common stuff
  [compojure.api.common

   ->Long])

