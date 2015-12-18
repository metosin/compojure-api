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

## Wiki

[Check wiki for documentation](https://github.com/metosin/compojure-api/wiki)

## Migration from Swagger 1.2 to 2.0

If you are upgrading your existing pre `0.20.0` compojure-api app to use `0.20.0` or later, you have to migrate the Swagger models
from 1.2 to 2.0. See [Migration guide](https://github.com/metosin/compojure-api/wiki/Migration-from-Swagger-1.2-to-2.0) for details.

## Sample application

```clojure
(ns examples.thingie
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]))

;;
;; Schemas
;;

(s/defschema Thingie 
  {:id Long
   :hot Boolean
   :tag (s/enum :kikka :kukka)
   :chief [{:name String
            :type #{{:id String}}}]})

;;
;; Routes
;;

(defroutes* legacy-route
  (GET* "/legacy/:value" [value]
    (ok {:value value})))

(defapi app
  (swagger-ui)
  (swagger-docs 
    {:info {:title "Sample api"}})
    
  (GET* "/" []
    :no-doc true
    (ok "hello world"))

  (context* "/api" []
    :tags ["thingie"]

    (GET* "/plus" []
      :return       Long
      :query-params [x :- Long, {y :- Long 1}]
      :summary      "x+y with query-parameters. y defaults to 1."
      (ok (+ x y)))

    (POST* "/minus" []
      :return      Long
      :body-params [x :- Long, y :- Long]
      :summary     "x-y with body-parameters."
      (ok (- x y)))

    (GET* "/times/:x/:y" []
      :return      Long
      :path-params [x :- Long, y :- Long]
      :summary     "x*y with path-parameters"
      (ok (* x y)))

    (POST* "/divide" []
      :return      Double
      :form-params [x :- Long, y :- Long]
      :summary     "x/y with form-parameters"
      (ok (/ x y)))

    (GET* "/power" []
      :return      Long
      :header-params [x :- Long, y :- Long]
      :summary     "x^y with header-parameters"
      (ok (long (Math/pow x y))))

    legacy-route

    (PUT* "/echo" []
      :return   [{:hot Boolean}]
      :body     [body [{:hot Boolean}]]
      :summary  "echoes a vector of anonymous hotties"
      (ok body))

    (POST* "/echo" []
      :return   (s/maybe Thingie)
      :body     [thingie (s/maybe Thingie)]
      :summary  "echoes a Thingie from json-body"
      (ok thingie)))

  (context* "/context" []
    :tags ["context*"]
    :summary "summary inherited from context"
    (context* "/:kikka" []
      :path-params [kikka :- s/Str]
      :query-params [kukka :- s/Str]
      (GET* "/:kakka" []
        :path-params [kakka :- s/Str]
        (ok {:kikka kikka
             :kukka kukka
             :kakka kakka})))))
```

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

## Other resources

- Using [Buddy](https://github.com/funcool/buddy) with Compojure-api: https://gist.github.com/Deraen/ef7f65d7ec26f048e2bb

## License

Copyright © 2014-2015 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
