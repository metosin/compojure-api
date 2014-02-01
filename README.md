# Compojure-api

Collection on helpers on top of [Compojure](https://github.com/weavejester/compojure) for helping to create sweet web apis. Target is to be a drop-in replacement for Compojure.

Contains a [Swagger](https://github.com/wordnik/swagger-core/wiki) implementation for Compojure, on top of [ring-swagger](https://github.com/metosin/ring-swagger) using [Schema](https://github.com/Prismatic/schema) to describe, validate and coarse the data models.

(There are other Swagger-implementations for Clojure, at least [Swag](https://developers.helloreverb.com/swagger/) and [Octohipster](https://github.com/myfreeweb/octohipster)).

Currently work-in-progress. There is a separate example [repository](https://github.com/metosin/compojure-api-examples) for latest working stuff.

## Installation

Add the following dependency to your `project.clj` file:

    [metosin/compojure-swagger "0.0.1"]

## Quickstart

1) Start with vanilla Compojure app (with [json-middleware](https://github.com/ring-clojure/ring-json)):

```clojure
(ns examples.example1
  (:require [compojure.core :refer :all]
            [ring.util.response :refer :all]))

(defroutes app
  (context "/api" []
    (GET "/thing" [] (response {:get "thing"}))
    (POST "/thing" [] (response {:post "thing"}))
    (DELETE "/thing" [] (response {:delete "thing"}))))
```

2) Start server and browse to ```/api/thing``` to see the JSON response

3) Import the ```compojure.swagger.core```:

```clojure
(ns examples.example1
  (:require [compojure.core :refer :all]
            [ring.util.response :refer :all]
            [compojure.swagger.core :refer :all]))
```

4) Create a Swagger-app by wrapping you routes with ```swaggered```-macro:

```clojure
  (swaggered "things"
    :description "Things Api"
    (context "/api" []
      (GET "/thing" [] (response {:get "thing"}))
      (POST "/thing" [] (response {:post "thing"}))
      (DELETE "/thing" [] (response {:delete "thing"}))))
```

5) Add ```swagger-docs```-route to generate swagger jsons descriptions

```clojure
  (swagger-docs "/api/docs"
    :title "Example Api"
    :description "Described it is.")
```

6) Browse to ```/api/docs``` & ```/api/docs/things``` to see the swagger details.

## Swagger-ui

You have three options to get the Swagger-UI for browsing and consuming your Apis.

### External

Requires [CORS-support](https://github.com/r0man/ring-cors) for your APIs. Example of external.

### Embedded

Package the Swagger-UI yourself to go with the app (recommened option for production)

### Ring-Swagger-UI

Embed the prepackaged swagger-UI directly to your app.

1) Add the following dependency to your `project.clj` file:

    [metosin/ring-swagger-ui "0.0.1"]

2) Add ```swagger-ui```-route to start serving the ui

```clojure
  (swagger-ui)
```

3) Browse to ```/``` to see the Swagger-UI

## Final code

In project.clj:

    [metosin/compojure-swagger "0.0.1"]
    [metosin/ring-swagger-ui "0.0.1"]

```clojure
(ns examples.example1
  (:require [compojure.core :refer :all]
            [ring.util.response :refer :all]
            [compojure.swagger.core :refer :all]))

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
      (DELETE "/thing" [] (response {:delete "thing"}))))
```

## Running the sample(s)

```lein ring server``` (samples will be pushed later external directory/respository)

## Adding meta-data to your Routes

TODO

## Features and quirks

- Ring-Swagger & Compojure-Swagger are not feature-complete, see TODO
- All Routes swaggered at compile-time, there should be no runtime penalty for api descriptions
- Routes are stored in an Atom => One should disable AOT when Uberjarring
- ```swaggered``` can only see routes in the same lexical scope (as it uses macro-peeling)
- As Compojure currently does not allow easy declaration of meta-data to routes..., to use rich swagger meta-data, one has to either a) pimp... compojure or b) wrap all Compojure-route macros (on the roadmap)

## TODO
- [ ] error messages
- [ ] consumes
- [ ] full samples
- [ ] smart destructuring of query parameters
- [ ] swagger-ui witn partially generated html
- [ ] travis
- [ ] cors-support
- [ ] license

## License

Copyright © 2014 Metosin Oy

Distributed under the Eclipse Public License, the same as Clojure.
