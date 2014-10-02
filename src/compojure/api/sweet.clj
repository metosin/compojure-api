(ns compojure.api.sweet
  (:require compojure.api.core
            compojure.api.swagger
            compojure.core
            [potemkin :refer [import-vars]]))

(import-vars

  ;; compojure routing
  [compojure.core

   let-request
   routing
   routes
   context
   let-routes]

  ;; with enchanced methods
  [compojure.api.core

   defapi
   middlewares

   defroutes*

   GET*
   ANY*
   HEAD*
   PATCH*
   DELETE*
   OPTIONS*
   POST*
   PUT*]

  ;; swaggered
  [compojure.api.swagger

   swagger-ui
   swagger-docs
   swaggered])
