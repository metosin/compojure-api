(ns compojure.api.sweet
  (:require [potemkin :refer [import-vars]]
            compojure.core
            compojure.api.core
            compojure.swagger.core))

(import-vars

  ;; compojure routing
  [compojure.core

   let-request
   routing
   routes
   defroutes
   context
   let-routes]

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

  ;; swaggered
  [compojure.swagger.core

   swagger-ui
   swagger-docs
   swaggered]

  ;; common stuff
  [compojure.api.core

    ok
    ->Long
    defapi
    with-middleware])

