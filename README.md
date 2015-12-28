# Compojure-api [![Build Status](https://api.travis-ci.org/metosin/compojure-api.svg?branch=master)](https://travis-ci.org/metosin/compojure-api) [![Dependencies Status](https://jarkeeper.com/metosin/compojure-api/status.svg)](https://jarkeeper.com/metosin/compojure-api)

Stuff on top of [Compojure](https://github.com/weavejester/compojure) for making sweet web apis.

- [API Docs](http://metosin.github.io/compojure-api/doc/)
- [Schema](https://github.com/Prismatic/schema) for input & output data coercion
- [Swagger 2.0](https://github.com/wordnik/swagger-core/wiki) for api documentation, via [ring-swagger](https://github.com/metosin/ring-swagger)
- Extendable route DSL via [metadata handlers](https://github.com/metosin/compojure-api/wiki/Creating-your-own-metadata-handlers)
- Bi-directional routing
- Bundled middleware for common api behavior (exception mapping, data formats & serialization)
- Route macros for putting things together, including the [Swagger-UI](https://github.com/wordnik/swagger-ui) via [ring-swagger-ui](https://github.com/metosin/ring-swagger-ui)

## Latest version

[![Clojars Project](http://clojars.org/metosin/compojure-api/latest-version.svg)](http://clojars.org/metosin/compojure-api)

## For information and help

### [Check wiki for documentation](https://github.com/metosin/compojure-api/wiki)

[Clojurians slack](https://clojurians.slack.com/) ([join](http://clojurians.net/)) has a channel [#ring-swagger](https://clojurians.slack.com/messages/ring-swagger/) for talk about any libraries using Ring-swagger. You can also ask questions about Compojure-api and Ring-swagger on other channels at Clojurians Slack or at #clojure on Freenode IRC (mention `compojure-api` or `ring-swagger` to highlight us).

## Examples

### Hello World

```clj
(ns example
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]))

(defapi app
  (GET* "/hello" []
    :query-params [name :- String]
    (ok {:message (str "Hello, " name)})))
```
 
### Api with Schema & Swagger-docs
 
 ```clj
 (ns example
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema Pizza
  {:name s/Str
   :size (s/enum :L :M :S)
   :origin {:country (s/enum :FI :PO)
            :city s/Str}})

(defapi app
  (swagger-ui)
  (swagger-docs
    {:info {:title "My Swagger API"
            :description "Compojure Api example"}
     :tags [{:name "api", :description "sample api"}]})
  (context* "/api" []
    :tags ["api"]
    (GET* "/hello" []
      :query-params [name :- String]
      (ok {:message (str "Hello, " name)}))
    (POST* "/pizza" []
      :return Pizza
      :body [pizza Pizza]
      :summary "echoes a pizza"
      (ok pizza))))
```

![swagger-api](https://raw.githubusercontent.com/wiki/metosin/compojure-api/swagger-api.png)

## More samples

This repo contains [a sample application](./examples/src/examples/thingie.clj).

To try it yourself, clone this repository and do either:

1. `lein run`
2. `lein repl` & `(go)`

## Quick start for  new project

Clone the [examples-repository](https://github.com/metosin/compojure-api-examples).

Use a Leiningen template, with or without tests:

```
lein new compojure-api my-api
lein new compojure-api my-api +midje
lein new compojure-api my-api +clojure-test
```

## License

Copyright © 2014-2015 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
