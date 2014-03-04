# Compojure-api

[![Build Status](https://travis-ci.org/metosin/compojure-api.png?branch=0.7.0)](https://travis-ci.org/metosin/compojure-api)

Stuff on top of [Compojure](https://github.com/weavejester/compojure) for making sweet web apis.

- contains a [Swagger](https://github.com/wordnik/swagger-core/wiki) implementation, using the [ring-swagger](https://github.com/metosin/ring-swagger)
- uses [Schema](https://github.com/Prismatic/schema) for creating and mapping data models
- bundled middleware for common api behavior (exception mapping, data formats & serialization)
- route macros for glueing everything together, also for the [Swagger-UI](https://github.com/wordnik/swagger-ui)

## Latest version

```clojure
[metosin/compojure-api "0.7.3"]
```

# Building Documented Apis

## Middlewares

If you targetting non-clojure clients, you most propably want to serve JSON. Compojure-Api has it's own modifications of [ring-json middlewares](https://github.com/weavejester/ring-json) in the namespace `compojure.api.json`.

There is also `compojure.api.middleware/api-middleware`, which packages many common middlewares:

- `ring.middleware.http-response/catch-response` for catching erronous http responses
- `ring.swagger.middleware/catch-validation-errors` for catching model validation errors
- `compojure.api.json/json-support` for json request & response parsing
- `compojure.handler/api` for normal parameter handling

### Mounting middlewars

`with-middleware` to set up the middlewares:

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

same in short form `defapi`:

```clojure
(require '[compojure.api.middleware :refer [defapi]])
(require '[ring.util.http-response :refer [ok]])
(require '[compojure.core :refer :all)

(defapi app
  (context "/api" []
    (GET "/ping" [] (ok {:ping "pong"}}))))
```

## Routes

You can use either [vanilla Compojure routes](https://github.com/weavejester/compojure/wiki) or their enchanced versions found from this lib. Enchanced versions can be used mostly as an drop-in-replacement from their Compojure counterparts. Macros with `*` in their name (`GET*`, `POST*`) denotes it can handle special syntax that would not work with the original counterparts.

Namespace `compojure.api.sweet` acts as a entry point for most of the functions and macros in this lib.

### sample sweet application

```clojure
(require '[ring.util.http-response :refer :all])
(require '[compojure.api.sweet :refer :all])

(defapi app
  (context "/api" []
    (GET "/user/:id" [id] (ok {:id id}))
    (POST "/echo" [{body :body-params}] (ok body))))
```

## Route documentation

Compojure-api uses [Swagger](https://github.com/wordnik/swagger-core/wiki) for route documentation.

Enabling Swagger in your application is done by mounting a `swaggered` -route macro on to the root of your app. There can be multiple `swaggered` apis in one web application. Behind the scenes, `swaggered` does some heavy macro-peeling to reconstruct the route tree. To follow defined routes, use `defroutes*`.

`swagger-docs` mounts the api definitions.

There is also a `swagger-ui` route for mounting the external [Swagger-UI](https://github.com/wordnik/swagger-ui).

### sample swaggered app

```clojure
(require '[ring.util.http-response :refer :all])
(require '[compojure.api.sweet :refer :all])

(defroutes* ping-route
  (GET "/user/:id" [id] (ok {:id id})))

(defapi app
  (swagger-ui)
  (swagger-docs)
  (swaggered "test"
    :description "Swagger test api"
    (context "/api" []
      ping-route
      (POST "/echo" [{body :body-params}] (ok body)))))
```

By default, Swagger-UI is mounted to the root `/` and api-listing to `/api/api-docs`.

Most route functions & macros have a loose (DSL) syntax taking optional parameters and having an easy way to add meta-data.

```clojure
  ; with defaults
  (swagger-docs)

  ; all said
  (swagger-docs "/api/api-docs"
    :title "Cool api"
    :apiVersion "1.0.0"
    :description "Compojure Sample Web Api"
    :termsOfServiceUrl "http://www.metosin.fi"
    :contact "pizza@example.com"
    :license "Eclipse 1.0"
    :licenseUrl "http://www.eclipse.org/legal/epl-v10.html")
```

See source code & [examples](https://github.com/metosin/compojure-api/blob/master/src/compojure/api/example/handler.clj#) for more details.

## Models

Compojure-api uses the [Schema](https://github.com/Prismatic/schema)-based [ring-swagger](https://github.com/metosin/ring-swagger) for managing it's data models. Models are presented as hererogenous Schema-maps defined by `defmodel`.

Ring-Swagger add some JSON-goodies on top of the Schema, naming a few:
- mappings from Schema to JSON Schema (basic types, maps, nested maps, arrays, references, enums etc.)
- symmetic serialization and coercion of `Set`, `Keyword`, `java.lang.Date`, `org.joda.time.DateTime` and `org.joda.time.LocalDate`

### sample schema

```clojure
(require '[ring.swagger.schema :refer :all])
(require '[schema.core :as s])

(defmodel Thingie {:id Long
	               :tags #{(s/enum :kikka :kukka)}})

(coerce! Thingie {:id 123
	              :tags ["kikka" "kikka"]})
; => {:id 123 :tags #{:kikka}}

(coerce! Thingie {:id 123
	              :tags ["kakka"]})
; => ExceptionInfo throw+: {:type :ring.swagger.schema/validation, :error {:tags #{(not (#{:kikka :kukka} :kakka))}}}  ring.swagger.schema/coerce! (schema.clj:85)
```

## Models and routes

Route-macros with special syntax (`*`) manage you models. You can define both input models & return models. Input models have smart schema-aware destructuring and do automatic data coersion.

```clojure
  (POST* "/echo"
    :return   Thingie
    :body     [thingie Thingie]
    :summary  "echos a thingie"
    :nickname "echoThingie"
    (ok thingie)) ;; gets called only if the thingie is valid
```

you can also wrap models in containers and add extra metadata:

```clojure
  (POST* "/echos"
    :return   [Thingie]
    :body     [thingies #{Thingie} {:description "set on thingies"}]
    (ok thingies))
```

### Full sample app

```clojure
(require '[ring.util.http-response :refer :all])
(require '[compojure.api.sweet :refer :all])
(require '[ring.swagger.schema :refer :all])
(require '[schema.core :as s])

(defmodel Thingie {:id Long
	               :tags #{(s/enum :kikka :kukka)}})

(defapi app
  (swagger-ui)
  (swagger-docs)
  (swaggered "test"
    :description "Swagger test api"
    (context "/api" []
      (POST* "/echo"
        :return   Thingie
        :body     [thingie Thingie]
        :summary  "echos a thingie"
        :nickname "echoThingie"
        (ok thingie))
      (POST* "/echos"
        :return   [Thingie]
        :body     [thingies #{Thingie} {:description "set on thingies"}]
        (ok thingies))))
```

## Quickstart for a new project

Clone the [examples-repo](https://github.com/metosin/compojure-api-examples).

A Leiningen template coming sooner or later.

## Running the embedded example(s)

`lein ring server`

## Features and weird things

- All routes are collected at compile-time
  - there is basically no runtime penalty for describing your apis
  - nested routes composed via vanilla Compojure `defroutes` is not supported, but there is a `compojure.api.routes/defroutes*` which has the needed meta-data to enable the auto-wiring needed. `compojure.api.sweet` includes the latter.
  - all runtime code between route-macros are ignored in route collections. See [tests](https://github.com/metosin/compojure-api/blob/master/test/compojure/api/swagger_test.clj)
  - `swaggered` peels the macros until it reaches `compojure.core` Vars. You should be able to write your own DSL-macros on top of those
- Collected routes are stored in an Atom after compilation => AOT from swaggered apps should be disabled when Uberjarring => routes should be written to file for allowing route precompilation

## TODO

- `url-for` for endpoints (bidi, bidi, bidi)
- smart destructuring & coarcing of any parameters (`:query-params`, `:path-params`)
- parametrizable automatic coercing: **no** (no coercing), **loose** (allow extra keys), **strict** (disallow extra keys)
- support for swagger error messages
- support for swagger consumes
- support for AOT compilation with uberjars (by persisting route definitions)
- collect routes from root, not from `swaggered`
- include external common use middlewares (ring-middleware-format, ring-cors etc.)
- websockets

## Contributing

Pull Requests welcome. Please run the tests (`lein midje`) and make sure they pass before you submit one.

## License

Copyright © 2014 Metosin Oy

Distributed under the Eclipse Public License, the same as Clojure.
