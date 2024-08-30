# Compojure-api 1.1.x

[Clojars 1.1.14](https://clojars.org/metosin/compojure-api/versions/1.1.14)
[![Slack](https://img.shields.io/badge/clojurians-ring_swagger-blue.svg?logo=slack)](https://clojurians.slack.com/messages/ring-swagger/)

Stuff on top of [Compojure](https://github.com/weavejester/compojure) for making sweet web apis.

- [API Docs](https://cljdoc.org/d/metosin/compojure-api/1.1.14/doc/readme) & [Wiki](https://github.com/metosin/compojure-api/wiki)
- [Schema](https://github.com/Prismatic/schema) for input & output data coercion
- [Swagger](https://swagger.io/) for api documentation, via [ring-swagger](https://github.com/metosin/ring-swagger)
- Extendable route DSL via [metadata handlers](https://github.com/metosin/compojure-api/wiki/Creating-your-own-metadata-handlers)
- Client negotiable formats: [JSON](https://www.json.org/), [EDN](https://github.com/edn-format/edn), [YAML](https://yaml.org/) & [Transit](https://github.com/cognitect/transit-format) (JSON & MessagePack)
- Data-driven [resources](https://github.com/metosin/compojure-api/wiki/Resources-and-Liberator)
- [Bi-directional](https://github.com/metosin/compojure-api/wiki/Routing#bi-directional-routing) routing
- Bundled middleware for common api behavior ([exception handling](https://github.com/metosin/compojure-api/wiki/Exception-handling), parameters & formats)
- Route functions & macros for putting things together, including the [Swagger-UI](https://github.com/wordnik/swagger-ui) via [ring-swagger-ui](https://github.com/metosin/ring-swagger-ui)

## Latest version

[1.1.14](https://clojars.org/metosin/compojure-api/versions/1.1.14)

## For information and help

### [Check wiki for documentation](https://github.com/metosin/compojure-api/wiki)

[Clojurians slack](https://clojurians.slack.com/) ([join](https://clojurians.net/)) has a channel [#ring-swagger](https://clojurians.slack.com/messages/ring-swagger/) for talk about any libraries using Ring-swagger. You can also ask questions about Compojure-api and Ring-swagger on other channels at Clojurians Slack or at #clojure on Freenode IRC (mention `compojure-api` or `ring-swagger` to highlight us).

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

Copied code from tools.macro has license:

```
Copyright (c) Rich Hickey. All rights reserved.
The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (https://opensource.org/license/epl-1-0/)
which can be found in the file epl-v10.html at the root of this distribution. By using this software in any fashion, you are agreeing to
be bound bythe terms of this license. You must not remove this notice, or any other, from this software.
```

All other code:

Copyright © 2014-2016 [Metosin Oy](https://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
