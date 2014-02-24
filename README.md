# Compojure-api

[![Build Status](https://travis-ci.org/metosin/compojure-api.png?branch=0.7.0)](https://travis-ci.org/metosin/compojure-api)

Collection on helpers on top of [Compojure](https://github.com/weavejester/compojure) for helping to create sweet web apis.

Contains a [Swagger](https://github.com/wordnik/swagger-core/wiki) implementation for Compojure, on top of [ring-swagger](https://github.com/metosin/ring-swagger) using [Schema](https://github.com/Prismatic/schema) to describe and coarse the data models.

Currently work-in-progress - Apis might change.

## Latest version

[![Latest version](https://clojars.org/metosin/compojure-api/latest-version.svg)](https://clojars.org/metosin/compojure-api)

You can also use the pre-packaged [Swagger-UI](https://github.com/wordnik/swagger-ui).

## Swagger for existing Compojure projects

- ensure you have [json-middleware](https://github.com/ring-clojure/ring-json) set up.
- wrap your Compojure routes with `swaggered`-macro for collecting your routes
- enable Swagger-doc genaration by adding a `swagger-docs`-route
- (enable Swagger-Ui by a adding a `swagger-ui`-route)
- enjoy

### Quickstart

```clojure
(ns compojure.api.example.handler
  (:require [compojure.core :refer :all]
            [ring.util.response :refer :all]
            [compojure.api.swagger :refer :all]))

(defroutes app
  (swagger-ui)
  (swagger-docs)
  (swaggered "things"
    :description "Things Api"
    (context "/api" []
      (GET "/thing" [] (response {:get "thing"}))
      (POST "/thing" [] (response {:post "thing"}))
      (DELETE "/thing" [] (response {:delete "thing"})))))
```

- browse to ```/api/api-docs``` & ```/api/api-docs/things``` to see the swagger details.

## Describing your Apis
Best way to start developing Schema-aware web apis, is to use `compojure.api.sweet`-package, which imports most of the required things you need.

See the [full example](https://github.com/metosin/compojure-api/tree/master/src/compojure/api/example/handler.clj) for things that do work.

## Quickstart for a new app

Clone the [examples-repo](https://github.com/metosin/compojure-api-examples).

## Running the embedded example(s)

```lein ring server```

## Features and quirks

- All Routes are collected at compile-time
  - there is basically no runtime penalty for describing your apis
  - `swaggered` uses macro-expansion/peeling to read the routes. Nested routes composed via vanilla Compojure `defroutes` is not supported, but there is a `compojure.api.routes/defroutes` which has the needed meta-data to enable the auto-wiring needed. `compojure.api.sweet` used the latter by default.
  - All runtime code between route-macros are ignored in route collections. See [tests](https://github.com/metosin/compojure-api/blob/master/test/compojure/api/swagger_test.clj) -> You should use macros to build your routing DSLs.
- Routes are collected into an Atom at compile-time => AOT from swaggered apps should be disabled when Uberjarring
- Compojure doesn't have a decent support adding meta-data to routes
  - There is a way, but kinda morbid (see 'Describing your Apis'))
  - This library has a [dirty hack](https://github.com/metosin/compojure-api/blob/master/src/compojure/api/pimp.clj) for this
     - in future, there will propably be a drop-in replacement for `compojure.core` supporting meta-data (and Schema-aware destructuring)

## TODO

- better documentation
- `url-for` for endpoints
- smart destructuring & coarcing of any parameters (`:query-params`, `:path-params`)
- declarative body coercing: **no** (no coercing), **loose** (allow extra keys), **strict** (disallow extra keys)
- swagger error messages
- swagger consumes
- support for AOT compilation with uberjars (by persisting route definitions)
- collect routes from root, not from `swaggered`
- include external common use middlewares (ring-middleware-format, ring-cors etc.)

## License

Copyright © 2014 Metosin Oy

Distributed under the Eclipse Public License, the same as Clojure.
