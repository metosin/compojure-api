# Compojure-api [![Build Status](https://api.travis-ci.org/metosin/compojure-api.svg?branch=master)](https://travis-ci.org/metosin/compojure-api)

Stuff on top of [Compojure](https://github.com/weavejester/compojure) for making sweet web apis.

- [Schema](https://github.com/Prismatic/schema) & [clojure.spec](https://clojure.org/about/spec) (2.0.0) for input & output data coercion
- [Swagger](http://swagger.io/) for api documentation, via [ring-swagger](https://github.com/metosin/ring-swagger) & [spec-tools](https://github.com/metosin/spec-tools)
- [Async](https://github.com/metosin/compojure-api/wiki/Async) with async-ring, [manifold](https://github.com/ztellman/manifold) and [core.async](https://github.com/clojure/core.async) (2.0.0)
- Client negotiable formats: [JSON](http://www.json.org/), [EDN](https://github.com/edn-format/edn) & [Transit](https://github.com/cognitect/transit-format), optionally [YAML](http://yaml.org/) and [MessagePack](http://msgpack.org/)
- Data-driven [resources](https://github.com/metosin/compojure-api/wiki/Resources-and-Liberator)
- [Bi-directional](https://github.com/metosin/compojure-api/wiki/Routing#bi-directional-routing) routing
- Bundled middleware for common api behavior ([exception handling](https://github.com/metosin/compojure-api/wiki/Exception-handling), parameters & formats)
- Extendable route DSL via [metadata handlers](https://github.com/metosin/compojure-api/wiki/Creating-your-own-metadata-handlers)
- Route functions & macros for putting things together, including the [Swagger-UI](https://github.com/wordnik/swagger-ui) via [ring-swagger-ui](https://github.com/metosin/ring-swagger-ui)
- Requires Clojure 1.9.0 & Java 1.8

[API Docs](http://metosin.github.io/compojure-api/doc/) & [Wiki](https://github.com/metosin/compojure-api/wiki)

## Latest version

[![Clojars Project](http://clojars.org/metosin/compojure-api/latest-version.svg)](http://clojars.org/metosin/compojure-api)

Latest non-alpha: `[metosin/compojure-api "1.1.12"]`.

See [CHANGELOG](https://github.com/metosin/compojure-api/blob/master/CHANGELOG.md) for details.

## For information and help

### [Read the Version 1.0 Blog Post](http://www.metosin.fi/blog/compojure-api-100/)

### [Schema & Spec Coercion with 2.0.0](https://github.com/metosin/compojure-api/wiki/Coercion)

### [Check wiki for documentation](https://github.com/metosin/compojure-api/wiki)

[Clojurians slack](https://clojurians.slack.com/) ([join](http://clojurians.net/)) has a channel [#ring-swagger](https://clojurians.slack.com/messages/ring-swagger/) for talk about any libraries using Ring-swagger. You can also ask questions about Compojure-api and Ring-swagger on other channels at Clojurians Slack or at #clojure on Freenode IRC (mention `compojure-api` or `ring-swagger` to highlight us).

## Examples

### Hello World Api

```clj
(require '[compojure.api.sweet :refer :all])
(require '[ring.util.http-response :refer :all])

(def app
  (api
    (GET "/hello" []
      :query-params [name :- String]
      (ok {:message (str "Hello, " name)}))))
```

### Hello World, async

```clj
(require '[compojure.api.sweet :refer :all])
(require '[clojure.core.async :as a])

(GET "/hello-async" []
  :query-params [name :- String]
  (a/go
    (a/<! (a/timeout 500))
    (ok {:message (str "Hello, " name)})))
```

<sub>* requires server to be run in [async mode](https://github.com/metosin/compojure-api/wiki/Async)</sub>

### Hello World, async & data-driven

```clj
(require '[compojure.api.sweet :refer :all])
(require '[clojure.core.async :as a])
(require '[schema.core :as s])

(context "/hello-async" []
  (resource
    {:get
     {:parameters {:query-params {:name String}}
      :responses {200 {:schema {:message String}}
                  404 {}
                  500 {:schema s/Any}}
      :handler (fn [{{:keys [name]} :query-params}]
                 (a/go
                   (a/<! (a/timeout 500))
                   (ok {:message (str "Hello, " name)})))}}))
```

<sub>* Note that empty body responses can be specified with `{}` or `{:schema s/Any}`

### Hello World, async, data-driven & clojure.spec

```clj
(require '[compojure.api.sweet :refer :all])
(require '[clojure.core.async :as a])
(require '[clojure.spec.alpha :as s])

(s/def ::name string?)
(s/def ::message string?)

(context "/hello-async" []
  (resource
    {:coercion :spec
     :get {:parameters {:query-params (s/keys :req-un [::name])}
           :responses {200 {:schema (s/keys :req-un [::message])}}
           :handler (fn [{{:keys [name]} :query-params}]
                      (a/go
                        (a/<! (a/timeout 500))
                        (ok {:message (str "Hello, " name)})))}}))
```

### Api with Schema & Swagger-docs

```clj
(require '[compojure.api.sweet :refer :all])
(require '[schema.core :as s])

(s/defschema Pizza
  {:name s/Str
   (s/optional-key :description) s/Str
   :size (s/enum :L :M :S)
   :origin {:country (s/enum :FI :PO)
            :city s/Str}})

(def app
  (api
    {:swagger
     {:ui "/api-docs"
      :spec "/swagger.json"
      :data {:info {:title "Sample API"
                    :description "Compojure Api example"}
             :tags [{:name "api", :description "some apis"}]
             :consumes ["application/json"]
             :produces ["application/json"]}}}

    (context "/api" []
      :tags ["api"]

      (GET "/plus" []
        :return {:result Long}
        :query-params [x :- Long, y :- Long]
        :summary "adds two numbers together"
        (ok {:result (+ x y)}))

      (POST "/echo" []
        :return Pizza
        :body [pizza Pizza]
        :summary "echoes a Pizza"
        (ok pizza)))))
```

![swagger-api](https://raw.githubusercontent.com/wiki/metosin/compojure-api/swagger-api.png)

## More samples

* official samples: https://github.com/metosin/compojure-api/tree/master/examples
* great full app: https://github.com/yogthos/memory-hole
* 2.0.0 sample: https://github.com/metosin/c2
* RESTful CRUD APIs Using Compojure-API and Toucan: https://www.demystifyfp.com/clojure/blog/restful-crud-apis-using-compojure-api-and-toucan-part-1/
* clojurice, An opinionated starter app for full-stack web applications in Clojure: https://github.com/jarcane/clojurice
* Web Development with Clojure, Second Edition: https://pragprog.com/book/dswdcloj2/web-development-with-clojure-second-edition

To try it yourself, clone this repository and do either:

1. `lein run`
2. `lein repl` & `(go)`

## Quick start for a new project

Use a Leiningen template, with or without tests:

```
lein new compojure-api my-api
lein new compojure-api my-api +midje
lein new compojure-api my-api +clojure-test
```

## License

Copyright © 2014-2018 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
