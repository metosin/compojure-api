# Compojure-api

[![Build Status](https://travis-ci.org/metosin/compojure-api.png?branch=0.7.0)](https://travis-ci.org/metosin/compojure-api)

Stuff on top of [Compojure](https://github.com/weavejester/compojure) for making sweet web apis.

- contains a [Swagger](https://github.com/wordnik/swagger-core/wiki) implementation, using the [ring-swagger](https://github.com/metosin/ring-swagger)
- uses [Schema](https://github.com/Prismatic/schema) for creating and mapping data models
- bundled middleware for common api behavior (exception mapping, data formats & serialization)
- route macros for putting things together, including the [Swagger-UI](https://github.com/wordnik/swagger-ui)

## Latest version

```clojure
[metosin/compojure-api "0.11.1"]
```

## Sample application

```clojure
(ns examples.thingie
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [ring.swagger.schema :refer [defmodel]]
            [schema.core :as s]))

(defmodel Thingie {:id Long
                   :hot Boolean
                   :tag (s/enum :kikka :kukka)})

(defroutes* legacy-route
  (GET* "/legacy/:value" [value]
    (ok {:value value})))

(defapi app
  (swagger-ui)
  (swagger-docs
    :title "Sample api")
  (swaggered "thingie"
    :description "There be thingies"
    (context "/api" []
      legacy-route

      (GET* "/plus" []
        :query-params [x :- Long y :- Long]
        :summary      "x+y with query-parameters"
        (ok {:total (+ x y)}))

      (POST* "/minus" []
        :body-params [x :- Long y :- Long]
        :summary     "x-y with body-parameters"
        (ok {:total (- x y)}))

      (GET* "/times/:x/:y" []
        :path-params [x :- Long y :- Long]
        :summary     "x*y with path-parameters"
        (ok {:total (* x y)}))

      (GET* "/echo" []
        :return   Thingie
        :query    [thingie Thingie]
        :summary  "echos a thingie from query-params"
        (ok thingie)) ;; here be coerced thingie

      (POST* "/echo" []
        :return   Thingie
        :body     [thingie Thingie]
        :summary  "echos a thingie from json-body"
        (ok thingie))))) ;; here be coerced thingie
```

To try it yourself, clone this repo and hit `lein start-thingie` (Jetty) or `lein http-kit-thingie` (Http-kit).

## Quickstart for a new project

Clone the [examples-repo](https://github.com/metosin/compojure-api-examples).

A Leiningen template coming sooner or later.

# Building Documented Apis

## Middlewares

There is pre-packaged middleware `api-middleware` for common web api usage, found in`compojure.api.middleware`. It's a enchanced version of `compojure.handler/api` adding the following:

- catching slinghotted http-errors (`ring.middleware.http-response/catch-response`)
- catching model validation errors (`ring.swagger.middleware/catch-validation-errors`)
- json request & response parsing (`compojure.api.json/json-support`)

### Mounting middlewars

To help setting up custom middleware there is a `middlewares` macro:

```clojure
(ns example
  (:require [ring.util.http-response :refer [ok]]
            [compojure.api.middleware :refer [api-middleware]]
            [compojure.api.core :refer [middlewares]]
            [compojure.core :refer :all]))

(defroutes app
  (middlewares [api-middleware]
    (context "/api" []
      (GET "/ping" [] (ok {:ping "pong"})))))
```

There is also a short form for the common case of `api-middleware`:

```clojure
(ns example2
  (:require [ring.util.http-response :refer [ok]]
            [compojure.api.core :refer [defapi]]
            [compojure.core :refer :all]))

(defapi app
  (context "/api" []
    (GET "/ping" [] (ok {:ping "pong"}))))
```

## Routes

You can use [vanilla Compojure routes](https://github.com/weavejester/compojure/wiki) or their enchanced versions from `compojure.api.core`. Enchanced versions have `*` in their name (`GET*`, `POST*`, `defroutes*` etc.) so that they don't get mixed up with the originals. Enchanced version can be used exactly as their ancestors but have also new behavior, more on that later.

Namespace `compojure.api.sweet` is a public entry point for all routing, importing Vars from `compojure.api.core`, `compojure.api.swagger` and `compojure.core`. If ignoring the `*`s with some macros, it can be used as a drop-in-place replacement for `compojure.core`.

### sample sweet application

```clojure
(ns example3
  (:require [ring.util.http-response :refer [ok]]
            [compojure.api.sweet :refer :all]))

(defapi app
  (context "/api" []
    (GET* "/user/:id" [id] (ok {:id id}))
    (POST* "/echo" {body :body-params} (ok body))))
```

## Route documentation

Compojure-api uses [Swagger](https://github.com/wordnik/swagger-core/wiki) for route documentation.

Enabling Swagger in your application is done by mounting a `swaggered` -route macro on to the root of your app. There can be multiple `swaggered` apis in one web application. Behind the scenes, `swaggered` does some heavy macro-peeling to reconstruct the route tree. If you intend to split your route-tree with `defroutes`, use `defroutes*` instead so that their routes get also collected.

`swagger-docs` mounts the api definitions.

There is also a `swagger-ui` route for mounting the external [Swagger-UI](https://github.com/wordnik/swagger-ui).

### sample swaggered app

```clojure
(ns example4
  (:require [ring.util.http-response :refer [ok]]
            [compojure.api.sweet :refer :all]))

(defroutes* legacy-route
  (GET* "/ping/:id" [id]
    (ok {:id id})))

(defapi app
  (swagger-ui)
  (swagger-docs)
  (swaggered "test"
    :description "Swagger test api"
    (context "/api" []
      legacy-route
      (POST* "/echo" {body :body-params} (ok body)))))
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

Two coercers are available (and automatically selected with smart destucturing): one for json and another for string-based formats (query-parameters & path-parameters). See [Ring-Swagger](https://github.com/metosin/ring-swagger#schema-coersion) for more details.

### sample schema

```clojure
(require '[ring.swagger.schema :refer :all])
(require '[schema.core :as s])

(defmodel Thingie {:id Long
                   :tag (s/enum :kikka :kukka)})

(coerce! Thingie {:id 123
                  :tag "kikka"})
; => {:id 123 :tag :kikka}

(coerce! Thingie {:id 123
                  :tags "kakka"})
; => ExceptionInfo throw+: {:type :ring.swagger.schema/validation, :error {:tags disallowed-key, :tag missing-required-key}}  ring.swagger.schema/coerce! (schema.clj:88)
```

## Models, routes and meta-data

The enchanced route-macros allow you to define extra meta-data by adding a) meta-data as a map or b) as pair of keyword-values in Liberator-style. With meta-data you can set both input and return models and some Swagger-specific data like nickname and summary. Input models have smart schema-aware destructuring and do automatic data coersion.

```clojure
  (POST* "/echo" []
    :return   Thingie
    :body     [thingie Thingie]
    :summary  "echos a thingie from json-body"
    :nickname "echoThingiePost"
    (ok thingie)) ;; here be coerced thingie
```

```clojure
  (GET* "/echo" []
    :return   Thingie
    :query    [thingie Thingie]
    :summary  "echos a thingie from query-params"
    :nickname "echoThingieQuery"
    (ok thingie)) ;; here be coerced thingie
```

you can also wrap models in containers (`Vector`, `List`, `Set`) and add extra metadata:

```clojure
  (POST* "/echos" []
    :return   [Thingie]
    :body     [thingies #{Thingie} {:description "set on thingies"}]
    (ok thingies))
```

## Query, Path and Body parameters

Both query- and path-parameters can also be destructured using the [Plumbing](https://github.com/Prismatic/plumbing) syntax with optional type-annotations:

```clojure
(GET* "/sum" []
  :query-params [x :- Long, y :- Long]
  (ok {:total (+ x y)}))

(GET* "/times/:x/:y" []
  :path-params [x :- Long, y :- Long]
  (ok {:total (* x y)}))

(POST* "/minus" []
  :body-params [x :- Long, y :- Long]
  (ok {:total (- x y)}))
```

## Route-specific middlewares

Key `:middlewares` takes a vector of middlewares to be applied to the route. Note that the middlewares are wrapped around the route, so they don't see any restructured bindinds and by so are more reusable.

```clojure
 (DELETE* "/user/:id" []
   :middlewares [audit-support (for-roles :admin)]
   (ok {:name "Pertti"})))
```

## Creating your own metadata handlers

Compojure-api handles the route metadatas by calling the multimethod `compojure.api.meta/restructure-param` with metadata key as a dispatch value.

Multimethods take three parameters:

1. metadata key
2. metadata value
3. accumulator map with keys
    - `:lets`, a vector of let bindings applied first before the actual body
    - `:letks`, a vector of letk bindings applied second before the actual body
    - `:middlewares`, a vector of route specific middlewares (applied from left to right)
    - `:parameters`, meta-data of a route (without the key & value for the current multimethod)
    - `:body`, a sequence of the actual route body

.. and should return the modified accumulator. Multimethod calls are reduced to produce the final accumulator for code generation. Defined key-value -based metadatas for routes are guaranteed to run on top-to-bottom order of the so all the potential `let` and `letk` variable overrides can be solved by the client. Default implementation is to keep the key & value as a route metadata.

You can add your own metadata-handlers by implementing the multimethod:

```clojure
(defmethod compojure.api.meta/restructure-param :auth
  [_ token {:keys [parameters lets body middlewares] :as acc}]
  "Make sure the request has X-AccessToken header and that it's value is 123. Binds the value into a variable"
  (-> acc
      (update-in [:lets] into [{{token "x-accesstoken"} :headers} '+compojure-api-request+])
      (assoc :body `((if (= ~token "123")
                      (do ~@body)
                      (ring.util.http-response/forbidden "Auth required"))))))
```

using it:

```clojure
(GET* "/current-session" []
  :auth token
  (ok {:token token}))
 ```

macroexpanding-1 it too see what's get generated:

```clojure
(compojure.core/GET
 "/current-session"
 [:as +compojure-api-request+]
 (compojure.api.common/meta-container
  {}
  (clojure.core/let
   [{{token "x-accesstoken"} :headers} +compojure-api-request+]
   (plumbing.core/letk
    []
    (if
     (clojure.core/= token "123")
     (do (ok {:token token}))
     (ring.util.http-response/forbidden "Auth required"))))))
```

## Running the embedded example(s)

`lein ring server`

## Features and weird things

- All routes are collected at compile-time
  - there is basically no runtime penalty for describing your apis
  - all runtime code between route-macros are ignored when macro-peeling route-trees. See [tests](https://github.com/metosin/compojure-api/blob/master/test/compojure/api/swagger_test.clj)
  - `swaggered` peels the macros until it reaches `compojure.core` Vars. You can write your own DSL-macros on top of those

## Roadmap

- collect routes from root, not from `swaggered` => removes the global swagger-atom
- macroextend only once (now twice: once with the peeling, second time with the real code)
- type-safe `:params` destructuring
- allow vanilla schemas instead of defmodels
- allow anonymous models with `:return`, `:body` and `:query`
- `url-for` for endpoints (bidi, bidi, bidi)
- support for swagger error messages
- support for swagger consumes
- include external common use middlewares (ring-middleware-format, ring-cors etc.)
- `FILE*`
- `WS*`

## Contributing

Pull Requests welcome. Please run the tests (`lein midje`) and make sure they pass before you submit one.

## License

Copyright © 2014 Metosin Oy

Distributed under the Eclipse Public License, the same as Clojure.
