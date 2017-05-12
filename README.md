# Compojure-api [![Build Status](https://api.travis-ci.org/metosin/compojure-api.svg?branch=master)](https://travis-ci.org/metosin/compojure-api) [![Downloads](https://jarkeeper.com/metosin/compojure-api/downloads.svg)](https://jarkeeper.com/metosin/compojure-api) [![Dependencies Status](https://jarkeeper.com/metosin/compojure-api/status.svg)](https://jarkeeper.com/metosin/compojure-api)

Stuff on top of [Compojure](https://github.com/weavejester/compojure) for making sweet web apis.

- [Schema](https://github.com/Prismatic/schema) for input & output data coercion
- [Swagger](http://swagger.io/) for api documentation, via [ring-swagger](https://github.com/metosin/ring-swagger)
- [Async](https://github.com/metosin/compojure-api/wiki/Async) with async-ring, [manifold](https://github.com/ztellman/manifold) and [core.async](https://github.com/clojure/core.async)
- Extendable route DSL via [metadata handlers](https://github.com/metosin/compojure-api/wiki/Creating-your-own-metadata-handlers)
- Client negotiable formats: [JSON](http://www.json.org/), [EDN](https://github.com/edn-format/edn), [YAML](http://yaml.org/) & [Transit](https://github.com/cognitect/transit-format) (JSON & MessagePack)
- Data-driven [resources](https://github.com/metosin/compojure-api/wiki/Resources-and-Liberator)
- [Bi-directional](https://github.com/metosin/compojure-api/wiki/Routing#bi-directional-routing) routing
- Bundled middleware for common api behavior ([exception handling](https://github.com/metosin/compojure-api/wiki/Exception-handling), parameters & formats)
- Route functions & macros for putting things together, including the [Swagger-UI](https://github.com/wordnik/swagger-ui) via [ring-swagger-ui](https://github.com/metosin/ring-swagger-ui)

[API Docs](http://metosin.github.io/compojure-api/doc/) & [Wiki](https://github.com/metosin/compojure-api/wiki)

## Latest version

[![Clojars Project](http://clojars.org/metosin/compojure-api/latest-version.svg)](http://clojars.org/metosin/compojure-api)

Latest non-alpha: `[metosin/compojure-api "1.1.10"]`.

See [CHANGELOG](https://github.com/metosin/compojure-api/blob/master/CHANGELOG.md) for details.

## For information and help

### [Read the Version 1.0 Blog Post](http://www.metosin.fi/blog/compojure-api-100/)

### [Check wiki for documentation](https://github.com/metosin/compojure-api/wiki)

[Clojurians slack](https://clojurians.slack.com/) ([join](http://clojurians.net/)) has a channel [#ring-swagger](https://clojurians.slack.com/messages/ring-swagger/) for talk about any libraries using Ring-swagger. You can also ask questions about Compojure-api and Ring-swagger on other channels at Clojurians Slack or at #clojure on Freenode IRC (mention `compojure-api` or `ring-swagger` to highlight us).

## Examples

### Hello World

```clj
(require '[compojure.api.sweet :refer :all])
(require '[ring.util.http-response :refer :all])

(defapi app
  (GET "/hello" []
    :query-params [name :- String]
    (ok {:message (str "Hello, " name)})))
```

### Api with Schema & Swagger-docs

 ```clj
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
             :tags [{:name "api", :description "some apis"}]}}}

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

https://github.com/metosin/compojure-api/tree/master/examples

Nice full app: https://github.com/yogthos/memory-hole

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

Copyright © 2014-2017 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
