# Compojure-api [![Build Status](https://api.travis-ci.org/metosin/compojure-api.svg?branch=master)](https://travis-ci.org/metosin/compojure-api) [![Dependencies Status](https://jarkeeper.com/metosin/compojure-api/status.svg)](https://jarkeeper.com/metosin/compojure-api)

Stuff on top of [Compojure](https://github.com/weavejester/compojure) for making sweet web apis.

- [API Docs](http://metosin.github.io/compojure-api/doc/)
- [Schema](https://github.com/Prismatic/schema) for input & output data coercion
- [Swagger 2.0](https://github.com/wordnik/swagger-core/wiki) for api documentation, via [ring-swagger](https://github.com/metosin/ring-swagger)
- simple extendable DSL via [metadata handlers](#creating-your-own-metadata-handlers)
- bundled middleware for common api behavior (exception mapping, data formats & serialization)
- route macros for putting things together, including the [Swagger-UI](https://github.com/wordnik/swagger-ui) via [ring-swagger-ui](https://github.com/metosin/ring-swagger-ui)

## Latest version

[![Clojars Project](http://clojars.org/metosin/compojure-api/latest-version.svg)](http://clojars.org/metosin/compojure-api)

## For information and help

### [Check wiki for documentation](https://github.com/metosin/compojure-api/wiki)

[Clojurians slack](https://clojurians.slack.com/) ([join](http://clojurians.net/)) has a channel [#ring-swagger](https://clojurians.slack.com/messages/ring-swagger/) for talk about any libraries using Ring-swagger. You can also ask questions about Compojure-api and Ring-swagger on other channels at Clojurians Slack or at #clojure on Freenode IRC (mention `compojure-api` or `ring-swagger` to highlight us).

## Migration from Swagger 1.2 to 2.0

If you are upgrading your existing pre `0.20.0` compojure-api app to use `0.20.0` or later, you have to migrate the Swagger models
from 1.2 to 2.0. See [Migration guide](https://github.com/metosin/compojure-api/wiki/Migration-from-Swagger-1.2-to-2.0) for details.

## Sample application

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
