# Compojure-api

Collection on helpers on top of [Compojure](https://github.com/weavejester/compojure) for helping to create sweet web apis.

Contains a [Swagger](https://github.com/wordnik/swagger-core/wiki) implementation for Compojure, on top of [ring-swagger](https://github.com/metosin/ring-swagger) using [Schema](https://github.com/Prismatic/schema) to describe and coarse the data models.

Currently work-in-progress.

## Installation

Add the following dependency to your `project.clj` file:

    [metosin/compojure-api "0.0.1"]

You can also use the pre-packaged [Swagger-UI](https://github.com/wordnik/swagger-ui) by adding to your `project.clj` file:

    [metosin/ring-swagger-ui "2.0.4"]

## Swagger for existing Compojure projects


- ensure you have [json-middleware](https://github.com/ring-clojure/ring-json) 
- wrap your Compojure routes with `swaggered`-macro for collecting your routes
- enable Swagger-doc genaration by adding a `swagger-docs`-route
- enable Swagger-Uuiby a adding a `swagger-ui`-route
- enjoy

### Sample app

```clojure
(ns compojure.api.example.handler
  (:require [compojure.core :refer :all]
            [ring.util.response :refer :all]
            [compojure.api.swagger :refer :all]))

(defroutes app
  (swagger-ui)
  (swagger-docs "/api/docs"
    :title "Example Api"
    :description "Described it is.")
  (swaggered "things"
    :description "Things Api"
    (context "/api" []
      (GET "/thing" [] (response {:get "thing"}))
      (POST "/thing" [] (response {:post "thing"}))
      (DELETE "/thing" [] (response {:delete "thing"})))))
```

- browse to ```/api/docs``` & ```/api/docs/things``` to see the swagger details.

## Describing your Apis

TODO. For now, check [examples](https://github.com/metosin/compojure-api/tree/master/src/compojure/api/example) how to add meta-data.

## Building with Compojure-Api

Clone the [examples-repo](https://github.com/metosin/compojure-api-examples)

## Running the embedded example(s)

```lein ring server```

## Features and quirks

- Ring-Swagger & Compojure-Swagger are not yet feature-complete, see TODO
- All Routes swaggered at compile-time
  - there is ~no runtime penalty for describing your apis
  - `swaggered` currently sees only routes in the same lexical scope (as it uses macro-peeling) -> no `defroutes` in between
  - All runtime-logic from routes are ignored in route collections. See [tests](https://github.com/metosin/compojure-api/blob/master/test/compojure/api/swagger_test.clj#L6-L51) 
- Routes are stored in an Atom => AOT from swaggered handlersshould be disabled  when Uberjarring (we could store the routes to a file if this is an real issue)
- Compojure does not allow clean way to declare extra meta-data to routes
  - it works oob, but does look nice (see 'Describing your Apis'))
  - there is a [dirty hack](https://github.com/metosin/compojure-api/blob/master/src/compojure/api/pimp.clj) to make setting the metadata easier
  - in future, there will propably be a drop-in replacement for `compojure.core` supporting meta-data (and Schema-aware destructuring)

## TODO
- [ ] describing error messages
- [ ] describing consumes
- [ ] collect routes via inlining `defroutes` or other `^:inline` stuff
- [ ] collect routes from root, not from `swaggered`
- [ ] smart destructuring of any-type parameters
- [ ] include external middlewares (ring-middleware-format, ring-cors etc.)
- [ ] travis

## License

Copyright © 2014 Metosin Oy

Distributed under the Eclipse Public License, the same as Clojure.
