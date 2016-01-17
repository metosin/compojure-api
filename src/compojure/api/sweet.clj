(ns compojure.api.sweet
  (:require compojure.api.core
            compojure.api.api
            compojure.api.routes
            compojure.api.swagger
            ring.swagger.json-schema
            [potemkin :refer [import-vars]]))

(import-vars

  [compojure.api.core

   routes
   defroutes
   let-routes
   undocumented
   middlewares

   context

   GET
   ANY
   HEAD
   PATCH
   DELETE
   OPTIONS
   POST
   PUT]

  [compojure.api.api

   api
   defapi]

  [compojure.api.routes

   path-for]

  [compojure.api.swagger

   swagger-ui
   swagger-docs]

  [ring.swagger.json-schema

   describe])
