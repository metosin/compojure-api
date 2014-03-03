# Compojure-api

[![Build Status](https://travis-ci.org/metosin/compojure-api.png?branch=0.7.0)](https://travis-ci.org/metosin/compojure-api)

Utility belt on top of the awesome [Compojure](https://github.com/weavejester/compojure) to help creating sweet web apis with Clojure.

- Contains a [Swagger](https://github.com/wordnik/swagger-core/wiki) implementation, using [ring-swagger](https://github.com/metosin/ring-swagger)
- Uses [Schema](https://github.com/Prismatic/schema) for creating and mapping data models
- Bundled middleware for common api stuff (exception mapping, data formats & serialization)
- New route macros for glueing everything together

## Latest version

[![Latest version](https://clojars.org/metosin/compojure-api/latest-version.svg)](https://clojars.org/metosin/compojure-api)

See also:
- [Swagger-UI](https://github.com/wordnik/swagger-ui).
- [Ring-http-response](https://github.com/metosin/ring-http-response)

# Building Documented Apis

## Middlewares

If you targetting non-clojure clients, you most propably want to serve JSON. For this there is a modified set json-middlewares of [ring-json](https://github.com/weavejester/ring-json) in the namespace `compojure.api.json`.

There is also `compojure.api.middleware/api-middleware`, which packages many common middlewares needed with web apis:

- `ring.middleware.http-response/catch-response` for catching thrown error response
- `ring.swagger.middleware/catch-validation-errors` for catching model validation errors
- `compojure.api.json/json-support` for json request & response parsing
- `compojure.handler/api` for normal parameter handling

To set up middlewares, one can use the `with-middleware` macro:

```clojure
(require '[compojure.api.core :refer [with-middleware]])
(require '[compojure.api.middleware :refer [api-middleware]])
(require '[ring.util.http-response :refer [ok]])
(require '[compojure.core :refer :all)

(defroutes app
  (with-middleware [api-middleware]
    (context "/api" []
      (GET "/ping" [] (ok {:ping "pong"}})))))
```

Or the short form `defapi`:

```clojure
(require '[compojure.api.middleware :refer [defapi]])
(require '[ring.util.http-response :refer [ok]])
(require '[compojure.core :refer :all)

(defapi app
  (context "/api" []
    (GET "/ping" [] (ok {:ping "pong"}}))))
```

## Routes

Instead of using [vanilla Compojure routes](https://github.com/weavejester/compojure/wiki), one can use enchanced versions found in `compojure.api.routes` and `compojure.api.core` namespaces. The latter has tuned versions of Compojure's http-method functions with `*` in the end (`GET*`, `POST*` etc.) to denote that they are not interchangable with the original Compojure versions.

For ease of development there is a `compojure.api.sweet` namespace acting as a public entry point for all of the stuff mentioned earlier.

A sample application with `compojure.api.sweet`:

```clojure
(require '[ring.util.http-response :refer :all])
(require '[compojure.api.sweet :refer :all])

(defapi app
  (context "/api" []
    (GET* "/user/:id" [id] (ok {:id id}))
    (POST* "/echo" [{body :body-params}] (ok body))))
```

## Route documentation

Compojure-api uses [Swagger](https://github.com/wordnik/swagger-core/wiki) for route documentation.

Enabling Swagger in your application is done by mounting a `swaggered` -route macro on to the root of your api. There can be multiple `swaggered` apis in one web application. Behind the scenes, `swaggered` does some heavy macro-peeling to reconstruct the route tree (would have been easier with [Bidi](https://github.com/juxt/bidi, right). It also can follow `defroutes`.

You also need to mount the `swagger-docs` route to publish the api definitions.

There is also a portable [Swagger-UI](https://github.com/wordnik/swagger-ui) pre-integrated in form of `swagger-ui` route. To use this, you just need to add a dependency to latest `metosin/ring-swagger-ui` to your project.

a full sample app:

```clojure
(require '[ring.util.http-response :refer :all])
(require '[compojure.api.sweet :refer :all])

(defapi app
  (swagger-ui)
  (swagger-docs)
  (swaggered "test"
    :description "Swagger test api"
    (context "/api" []
      (GET* "/user/:id" [id] (ok {:id id}))
      (POST* "/echo" [{body :body-params}] (ok body)))))
```

By default, Swagger-UI is mounted to the root '/' and api-listing to `/api/api-docs`.

Most route functions & macros have a loose (DSLy) syntax taking optional parameters and having an easy way to add meta-data.

```clojure
  (swagger-docs "/api/api-docs"
    :title "Cool api"
    :apiVersion "1.0.0"
    :description "Compojure Sample Web Api"
    :termsOfServiceUrl "http://www.metosin.fi"
    :contact "pizza@example.com"
    :license "Eclipse 1.0"
    :licenseUrl "http://www.eclipse.org/legal/epl-v10.html")
```


See source code & examples for more details.

## Models

To describe an Api one needs to describe it's datas. Compojure-api uses the [Schema](https://github.com/Prismatic/schema)-based [ring-swagger](https://github.com/metosin/ring-swagger) for managing api data models.

Ring-Swagger add some JSON-goodies on top of the vanilla Schema, naming a few:
- mappings from Schema to JSON Schema (not yet feature complete)
  - basic types, nested maps, arrays, references, enums etc.
- symmetic serialization and coercion of `Set`, `Keyword`, `java.lang.Date`, `org.joda.time.DateTime` and `org.joda.time.LocalDate`

a sample schema:

```clojure
(require '[ring.swagger.schema :refer :all])
(require '[schema.core :as s])

(defmodel Thingie {:id Long :tags #{(s/enum :kikka :kukka)}})

(coerce! Thingie {:id 123 :tags ["kikka" "kikka"]})
; => {:id 123 :tags #{:kikka}}

(coerce! Thingie {:id 123 :tags ["kakka"]})
; => ExceptionInfo throw+: {:type :ring.swagger.schema/validation, :error {:tags #{(not (#{:kikka :kukka} :kakka))}}}  ring.swagger.schema/coerce! (schema.clj:85)
```

## Models and routes

Models are integrated into endpoint-macros, allowing one to easily define input and return models. There is also smart schema-aware destructuring and automatic coersion for the data.

```clojure
  (POST* "/echo"
    :return   Thingie
    :body     [thingie Thingie]
    :summary  "echos a thingie"
    :nickname "echoThingie"
    (ok thingie)) ;; gets called only if the thingie is valid
```

See the [examples](https://github.com/metosin/compojure-api/tree/master/src/compojure/api/example/handler.clj) for more samples.

# Quickstart for a new project

Clone the [examples-repo](https://github.com/metosin/compojure-api-examples). A Leiningen template coming sooner or later.

# Running the embedded example(s)

`lein ring server`

## Features and weird things

- All routes are collected at compile-time
  - there is basically no runtime penalty for describing your apis
  - nested routes composed via vanilla Compojure `defroutes` is not supported, but there is a `compojure.api.routes/defroutes` which has the needed meta-data to enable the auto-wiring needed. `compojure.api.sweet` used the latter by default.
  - all runtime code between route-macros are ignored in route collections. See [tests](https://github.com/metosin/compojure-api/blob/master/test/compojure/api/swagger_test.clj)
  - `swaggered` peels the macros until it reaches `compojure.core` Vars. You should be able to write your own DSL-macros on top of those
- Collected routes are stored in an Atom after compilation => AOT from swaggered apps should be disabled when Uberjarring => routes should be written to file for allowing route precompilation
- Compojure doesn't have a decent support adding meta-data to routes
  - There is a way, but it's not nice (see 'Describing your Apis'))
  - This library does a [dirty deed](https://github.com/metosin/compojure-api/blob/master/src/compojure/api/pimp.clj) to overcome this

## TODO

- `url-for` for endpoints
- smart destructuring & coarcing of any parameters (`:query-params`, `:path-params`)
- parametrizable automatic coercing: **no** (no coercing), **loose** (allow extra keys), **strict** (disallow extra keys)
- support for swagger error messages
- support for swagger consumes
- support for AOT compilation with uberjars (by persisting route definitions)
- collect routes from root, not from `swaggered`
- include external common use middlewares (ring-middleware-format, ring-cors etc.)

## License

Copyright © 2014 Metosin Oy

Distributed under the Eclipse Public License, the same as Clojure.
