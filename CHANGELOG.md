## Unreleased

- Log any exceptions and add option to define function used to
log exceptions. ([#191](https://github.com/metosin/compojure-api/issues/191))
  - Previously only default exception handler logged exceptions, but
  `request-validation`, `request-parsing`, `response-validation` or
  `schema-error` exceptions were not logged. Now they are logged.
  - To disable logging completely, provide no-op `log-fn`:
  `:exceptions {:log-fn (fn [e] nil)}`

## 0.24.4 (13.1.2016)

**[compare](https://github.com/metosin/compojure-api/compare/0.24.3...0.24.4)**

- Updated ring-swagger:
  - Discard all extra map keys from properties ([metosin/ring-swagger#77](https://github.com/metosin/ring-swagger/issues/77))
  - All Schema [extra keys](https://github.com/Prismatic/schema/blob/master/src/cljx/schema/core.cljx#L765)
  are now exposed as Swagger additional properties.
    - Previously only `s/Keyword` were supported.
  - Fix JSON Schema `nil` default value ([metosin/ring-swagger#79](https://github.com/metosin/ring-swagger/issues/79))


* Updated deps:

```clojure
[metosin/ring-swagger "0.22.2"] is available
[metosin/ring-swagger-ui "2.1.4-0"] is available
[potemkin "0.4.3"] is available
```

## 0.24.3 (14.12.2015)

**[compare](https://github.com/metosin/compojure-api/compare/0.24.2...0.24.3)**

* coercer-cache is now per Route instead beeing global and based on a
FIFO size 100 cache. Avoids potential memory leaks when using anonymous coercion matchers (which never hit the cache).

* Updated deps:

```clj
[prismatic/schema "1.0.4"] is available but we use "1.0.3"
```

## 0.24.2 (8.12.2015)

**[compare](https://github.com/metosin/compojure-api/compare/0.24.1...0.24.2)**

* Memoize coercers (for `schema` & `matcher` -input) for better performance.
  * [Tests](https://github.com/metosin/compojure-api/blob/master/test/compojure/api/perf_test.clj) show 0-40% lower latency,
depending on input & output schema complexity.
  * Tested by sending json-strings to `api` and reading json-string out.
  * Measured a 80% lower latency with a real world large Schema.
* Updated deps:

```clj
[potemkin "0.4.2"] is available but we use "0.4.1"
```

## 0.24.1 (29.11.2015)

**[compare](https://github.com/metosin/compojure-api/compare/0.24.0...0.24.1)**

* uses [`[Ring-Swagger "0.22.1"]`](https://github.com/metosin/ring-swagger/blob/master/CHANGELOG.md#0221-29112015)
* `clojure.tools.logging` is used with default uncaugt exception handling if it's found
on the classpath. Fixes [#172](https://github.com/metosin/compojure-api/issues/172).
* Both `api` and `defapi` produce identical swagger-docs. Fixes [#159](https://github.com/metosin/compojure-api/issues/159)
* allow any swagger data to be overriden at runtime either via swagger-docs or via middlewares. Fixes [#170](https://github.com/metosin/compojure-api/issues/170).

```clojure
[metosin/ring-swagger "0.22.1"] is available but we use "0.22.0"
[metosin/ring-swagger-ui "2.1.3-4"] is available but we use "2.1.3-2"
[prismatic/plumbing "0.5.2] is available but we use "0.5.1"
```

## 0.24.0 (8.11.2015)

**[compare](https://github.com/metosin/compojure-api/compare/0.23.1...0.24.0)**

- **BREAKING**: Dropped support for Clojure 1.6
- **BREAKING**: Supports and depends on Schema 1.0.
- **BREAKING**: `ring-middleware-format` accepts transit options in a new format:

```clj
;; pre 0.24.0:

(api
  {:format {:response-opts {:transit-json {:handlers transit/writers}}
            :params-opts   {:transit-json {:options {:handlers transit/readers}}}}}
  ...)

;; 0.24.0 +

(api
  {:format {:response-opts {:transit-json {:handlers transit/writers}}
            :params-opts   {:transit-json {:handlers transit/readers}}}}
  ...)
```

- Uses upstream [ring-middleware-format](https://github.com/ngrunwald/ring-middleware-format)
instead of Metosin fork.
- Uses now [linked](https://github.com/frankiesardo/linked) instead of
[ordered](https://github.com/amalloy/ordered) for maps where order matters.
- `swagger-ui` now supports passing arbitrary options to `SwaggerUI`
([metosin/ring-swagger#67](https://github.com/metosin/ring-swagger/issues/67)).
* Updated deps:

```clojure
[prismatic/schema "1.0.3"] is available but we use "0.4.4"
[prismatic/plumbing "0.5.1] is available but we use "0.4.4"
[metosin/schema-tools "0.7.0"] is available but we use "0.5.2"
[metosin/ring-swagger "0.22.0"] is available but we use "0.21.0"
[metosin/ring-swagger-ui "2.1.3-2"] is available but we use "2.1.2"
```

## 0.23.1 (3.9.2015)

**[compare](https://github.com/metosin/compojure-api/compare/0.23.0...0.23.1)**

* Routes are kept in order for swagger docs, Fixes [#138](https://github.com/metosin/compojure-api/issues/138).

## 0.23.0 (1.9.2015)

**[compare](https://github.com/metosin/compojure-api/compare/0.22.2...0.23.0)**

* Ring-swagger 0.21.0
  * **BREAKING**: new signature for dispatching custom JSON Schema transformations, old signature will break (nicely at compile-time), see [Readme](https://github.com/metosin/ring-swagger/blob/master/README.md) for details.
  * Support for collections in query parameters. E.g. `:query-params [x :- [Long]]` & url `?x=1&x=2&x=3` should result in `x` being `[1 2 3]`.
* **BREAKING**: `:validation-errors :error-handler`, `:validation-errors :catch-core-errors?`
  and `:exceptions :exception-handler` options have been removed.
  * These have been replaced with general `:exceptions :handlers` options.
  * Fails nicely at compile-time
  * **BREAKING**: New handler use different arity than old handler functions.
    * new arguments: Exception, ex-info and request.
* Move `context` from `compojure.api.sweet` to `compojure.api.legacy`. Use `context*` instead.
* Updated deps:

```clojure
[metosin/ring-swagger "0.21.0-SNAPSHOT"] is available but we use "0.20.4"
[compojure "1.4.0"] is available but we use "1.3.4"
[prismatic/schema "0.4.4"] is available but we use "0.4.3"
[metosin/ring-http-response "0.6.5"] is available but we use "0.6.3"
[metosin/schema-tools "0.5.2"] is available but we use "0.5.1"
[metosin/ring-swagger-ui "2.1.2"] is available but we use "2.1.5-M2"
[peridot "0.4.1"] is available but we use "0.4.0"
```

## 0.22.2 (12.8.2015)

**[compare](https://github.com/metosin/compojure-api/compare/0.22.1...0.22.2)**

* fixes [150](https://github.com/metosin/compojure-api/issues/150)

## 0.22.1 (12.7.2015)

**[compare](https://github.com/metosin/compojure-api/compare/0.22.0...0.22.1)**

* fixes [137](https://github.com/metosin/compojure-api/issues/137) & [134](https://github.com/metosin/compojure-api/issues/134), thanks to @thomaswhitcomb!
* updated deps:

```clojure
[metosin/ring-http-response "0.6.3"] is available but we use "0.6.2"
[midje "1.7.0"] is available but we use "1.7.0-SNAPSHOT"
```

## 0.22.0 (30.6.2015)

**[compare](https://github.com/metosin/compojure-api/compare/0.21.0...0.22.0)**

* Optional integration with [Component](https://github.com/stuartsierra/component).
  Use either `:components`-option of `api-middleware` or `wrap-components`-middleware
  to associate the components with your API. Then you can use `:components`-restructuring
  to destructure your components using letk syntax.
* fix for [#123](https://github.com/metosin/compojure-api/issues/123)
* support for pluggable coercion, at both api-level & endpoint-level with option `:coercion`. See the[the tests](./test/compojure/api/coercion_test.clj).
  * coercion is a function of type - `ring-request->coercion-type->coercion-matcher` allowing protocol-based coercion in the future
  ** BREAKING**: if you have created custom restructurings using `src-coerce`, they will break (nicely at compile-time)

* new restucturing `:swagger` just for swagger-docs. Does not do any coercion.

```clojure
(GET* "/documented" []
  :swagger {:responses {200 {:schema User}
                        404 {:schema Error
                             :description "Not Found"} }
            :paramerers {:query {:q s/Str}
                         :body NewUser}}}
  ...)
```

```clojure
[cheshire "5.5.0"] is available but we use "5.4.0"
[backtick "0.3.3"] is available but we use "0.3.2"
[lein-ring "0.9.6"] is available but we use "0.9.4"
```

## 0.21.0 (25.5.2015)

* `:multipart-params` now sets `:consumes ["multipart/form-data"]` and `:form-params` sets
`:consumes ["application/x-www-form-urlencoded"]`
* **experimental**: File upload support using `compojure.api.upload` namespace.

```clojure
(POST* "/upload" []
  :multipart-params [file :- TempFileUpload]
  :middlewares [wrap-multipart-params]
  (ok (dissoc file :tempfile))))
```

* **breaking**: use plain Ring-Swagger 2.0 models with `:responses`. A helpful `IllegalArgumentException` will be thrown at compile-time with old models.
* new way:

```clojure
:responses {400 {:schema ErrorSchema}}
:responses {400 {:schema ErrorSchema, :description "Eror"}}
```

* allow configuring of Ring-Swagger via `api-middleware` options with key `:ring-swagger`:

```clojure
(defapi app
  {:ring-swagger {:ignore-missing-mappings? true}})
  (swagger-docs)
  (swagger-ui)
  ...)
```

* Bidirectinal routing, inspired by [bidi](https://github.com/juxt/bidi) - named routes & `path-for`:

```clojure
(fact "bidirectional routing"
  (let [app (api
              (GET* "/api/pong" []
                :name :pong
                (ok {:pong "pong"}))
              (GET* "/api/ping" []
                (moved-permanently (path-for :pong))))]
    (fact "path-for resolution"
      (let [[status body] (get* app "/api/ping" {})]
        status => 200
        body => {:pong "pong"}))))
```

* a validator for the api

```clojure
(require '[compojure.api.sweet :refer :all])
(require '[compojure.api.swagger :refer [validate])

(defrecord NonSwaggerRecord [data])

(def app
  (validate
    (api
      (swagger-docs)
      (GET* "/ping" []
        :return NonSwaggerRecord
        (ok (->NonSwaggerRecord "ping"))))))

; clojure.lang.Compiler$CompilerException: java.lang.IllegalArgumentException:
; don't know how to create json-type of: class compojure.api.integration_test.NonSwaggerRecord
```

* updated dependencies:

```clojure
[metosin/ring-swagger "0.20.4"] is available but we use "0.20.3"
[metosin/ring-http-response "0.6.2"] is available but we use "0.6.1"
[metosin/ring-swagger-ui "2.1.5-M2"]
[prismatic/plumbing "0.4.4"] is available but we use "0.4.3"
[prismatic/schema "0.4.3"] is available but we use "0.4.2"
```

## 0.20.4 (20.5.2015)

* response descriptions can be given also with run-time meta-data (`with-meta`), fixes [#96](https://github.com/metosin/compojure-api/issues/96)
  * in next MINOR version, we'll switch to (Ring-)Swagger 2.0 format.

```clojure
(context* "/responses" []
  :tags ["responses"]
  (GET* "/" []
    :query-params [return :- (s/enum :200 :403 :404)]
    :responses    {403 ^{:message "spiders?"} {:code s/Str} ; old
                   404 (with-meta {:reason s/Str} {:message "lost?"})} ; new
    :return       Total
    :summary      "multiple returns models"
    (case return
      :200 (ok {:total 42})
      :403 (forbidden {:code "forest"})
      :404 (not-found {:reason "lost"}))))
```

## 0.20.3 (17.5.2015)

* welcome `compojure.api.core/api`, the work-horse behind `compojure.api.core/defapi`.
* lexically bound route-trees, generated by `api`, pushed to request via ring-swagger middlewares.
  * no more `+compojure-api-routes+` littering the handler namespaces.
* fixes [#101](https://github.com/metosin/compojure-api/issues/101)
* fixes [#102](https://github.com/metosin/compojure-api/issues/102)
* update dependencies:

```clojure
[metosin/ring-swagger "0.20.3"] is available but we use "0.20.2"
[prismatic/plumbing "0.4.3"] is available but we use "0.4.2"
[peridot "0.4.0"] is available but we use "0.3.1"
[compojure "1.3.4"] is available but we use "1.3.3"
[lein-ring "0.9.4"] is available but we use "0.9.3"
```

## 0.20.1 (2.5.2015)

* use ring-swagger middleware swagger-data injection instead of own custom mechanism.
* fixed [#98](https://github.com/metosin/compojure-api/issues/98): 2.0 UI works when running with context on (Servlet-based) app-servers.
* Preserve response-schema names, fixes [#93](https://github.com/metosin/compojure-api/issues/93).
* updated dependencies:

```clojure
[metosin/ring-swagger "0.20.2"] is available but we use "0.20.0"
[prismatic/schema "0.4.2"] is available but we use "0.4.1"
```

## 0.20.0 (24.4.2015)

* New restructuring for `:no-doc` (a boolean) - endpoints with this don't get api documentation.
* Fixed [#42](https://github.com/metosin/compojure-api/issues/42) - `defroutes*` now does namespace resolution for the source
used for route peeling and source linking (the macro magic)
* Fixed [#91](https://github.com/metosin/compojure-api/issues/91) - `defroutes*` are now automatically accessed over a Var for better development flow.
* Fixed [#89](https://github.com/metosin/compojure-api/issues/89).
* Fixed [#82](https://github.com/metosin/compojure-api/issues/82).
* Fixed [#71](https://github.com/metosin/compojure-api/issues/71), [ring-swagger-ui](https://github.com/metosin/ring-swagger-ui)
is now a dependency.

* **breaking** `ring.swagger.json-schema/describe` is now imported into `compojure.api.sweet` for easy use. If your code
refers to it directly, you need remove the direct reference.

### Swagger 2.0 -support

#### [Migration Guide](https://github.com/metosin/compojure-api/wiki/Migration-from-Swagger-1.2-to-2.0)

* Routes are collected always from the root (`defapi` or `compojure.api.routes/api-root` within that)
* `compojure.api.routes/with-routes` is now `compojure.api.routes/api-root`
* **breaking** requires the latest swagger-ui to work
  * `[metosin/ring-swagger-ui "2.1.1-M2"]` to get things pre-configured
  * or package `2.1.1-M2` yourself from the [source](https://github.com/swagger-api/swagger-ui).
* **breaking**: api ordering is not implemented.
* **breaking**: restructuring `:nickname` is now `:operationId`
* **breaking**: restructuring `:notes` is now `:description`
* `swagger-docs` now takes any valid Swagger Spec data in. Using old format gives a warning is to STDOUT.

```clojure
(swagger-docs
  {:info {:version "1.0.0"
          :title "Sausages"
          :description "Sausage description"
          :termsOfService "http://helloreverb.com/terms/"
          :contact {:name "My API Team"
                    :email "foo@example.com"
                    :url "http://www.metosin.fi"}
          :license {:name "Eclipse Public License"
                    :url "http://www.eclipse.org/legal/epl-v10.html"}}
   :tags [{:name "kikka", :description "kukka"}]})
```

* Swagger-documentation default uri is changed from `/api/api-docs` to `/swagger.json`.
* `compojure.api.swagger/swaggered` is deprecated - not relevant with 2.0. Works, but prints out a warning to STDOUT
** in 2.0, apis are categorized by Tags, one can set them either to endpoints or to paths:

```clojure
(GET* "/api/pets/" []
  :tags ["pet"]
  (ok ...))
```

```clojure
(context* "/api/pets" []
  :tags ["pet"]
  (GET* "/" []
    :summary "get all pets"
    (ok ...)))
```

- updated deps:

```clojure
[metosin/ring-swagger "0.20.0"] is available but we use "0.19.4"
[prismatic/schema "0.4.1"] is available but we use "0.4.0"
```

## 0.19.3 (9.4.2015)
- Fixed [#79](https://github.com/metosin/compojure-api/issues/79) by [Jon Eisen](https://github.com/yanatan16)

- updated deps:

```clojure
[prismatic/plumbing "0.4.2"] is available but we use "0.4.1"
[prismatic/schema "0.4.1"] is available but we use "0.4.0"
[potemkin "0.3.13"] is available but we use "0.3.12"
[compojure "1.3.3"] is available but we use "1.3.2"
[metosin/ring-swagger "0.19.4"] is available but we use "0.19.3"
```

## 0.19.2 (31.3.2015)

- Compatibility with swagger-ui `2.1.0-M2` - `[metosin/ring-swagger-ui "2.1.0-M2-2]`
- updated deps:
```clojure
[metosin/ring-swagger "0.19.3"] is available but we use "0.19.2"
```

## 0.19.1 (31.3.2015)
- avoid reflection fixes by [Michael Blume](https://github.com/MichaelBlume)
- one can now wrap body & response-models in predicates and get the swagger docs out:

```clojure
  :return (s/maybe User)
  :responses {200 (s/maybe User)
              400 (s/either Cat Dog)}
```

- updated deps:

```clojure
[metosin/ring-swagger "0.19.2"] is available but we use "0.19.1"
```

## 0.19.0 (28.3.2015)

- added destructuring for `:headers`, thanks to [tchagnon](https://github.com/tchagnon)!
- `:path-param` allows any keywords, needed for the partial parameter matching with `context*`
- **BREAKING**: parameters are collected in (Ring-)Swagger 2.0 format, might break client-side `compojure.api.meta/restructure-param` dispatch functions - for the swagger documentation part. See https://github.com/metosin/ring-swagger/blob/master/test/ring/swagger/swagger2_test.clj & https://github.com/metosin/compojure-api/blob/master/src/compojure/api/meta.clj for examples of the new schemas.
- `context*` to allow setting meta-data to mid-routes. Mid-route meta-data are deep-merged into endpoint swagger-definitions at compile-time. At runtime, code is executed in place.

```clojure
(context* "/api/:kikka" []
  :summary "summary inherited from context"
  :path-params [kikka :- s/Str] ; enforced here at runtime
  :query-params [kukka :- s/Str] ; enforced here at runtime
  (GET* "/:kakka" []
    :path-params [kakka :- s/Str] ; enforced here at runtime
    (ok {:kikka kikka
         :kukka kukka
         :kakka kakka})))
```

- updated deps:

```clojure
[prismatic/plumbing "0.4.1"] is available but we use "0.3.7"
[potemkin "0.3.12"] is available but we use "0.3.11"
[prismatic/schema "0.4.0"] is available but we use "0.3.7"
[metosin/ring-http-response "0.6.1"] is available but we use "0.6.0"
[metosin/ring-swagger "0.19.0"] is available but we use "0.18.1"
[lein-ring "0.9.3"] is available but we use "0.9.2"
```

## 0.18.0 (2.3.2015)

- Support passing options to specific format middlewares (merged into defaults):
```clj
(defapi app
  {:format {:formats [:json-kw :yaml-kw :edn :transit-json :transit-msgpack]
            :params-opts {}
            :response-opts {}}
   :validation-errors {:error-handler nil
                       :catch-core-errors? nil}
   :exceptions {:exception-handler default-exception-handler}}
  ...)
```
- import `compojure.core/wrap-routes` into `compojure.api.sweet`
- **BREAKING**: in `compojure.api.middleware`, `ex-info-support` is now parameterizable `wrap-exception`
  - fixes [#68](https://github.com/metosin/compojure-api/issues/68)
- Update dependencies
```
[prismatic/plumbing "0.3.7"] is available but we use "0.3.5"
[compojure "1.3.2"] is available but we use "1.3.1"
[prismatic/schema "0.3.7"] is available but we use "0.3.3"
[metosin/ring-swagger "0.18.0"] is available but we use "0.15.0"
[metosin/ring-http-response "0.6.0"] is available but we use "0.5.2"
[metosin/ring-middleware-format "0.6.0"] is available but we use "0.5.0"
```

## 0.17.0 (10.1.2015)

- Depend on forked version of [`ring-middleware-format`](https://github.com/metosin/ring-middleware-format)
  - Transit support should now work
  - If you are depending on ring-middleware-format directly, you'll want to either
  update your dependency or exclude one from Compojure-api
- Update dependencies:
```clojure
[cheshire "5.4.0"] is available but we use "5.3.1"
[metosin/ring-swagger-ui "2.0.24"] is available but we use "2.0.17"
[lein-ring "0.9.0"] is available but we use "0.8.13"
```

## 0.16.6 (8.12.2014)

- fix #53
- update deps:
```clojure
[compojure "1.3.1"] is available but we use "1.2.1"
[metosin/ring-swagger "0.15.0"] is available but we use "0.14.1"
[peridot "0.3.1"] is available but we use "0.3.0"
```

## 0.16.5 (21.11.2014)

- fix anonymous Body & Return model naming issue [56](https://github.com/metosin/compojure-api/issues/56) by [Michael Blume](https://github.com/MichaelBlume)
- update deps:

```clojure
[prismatic/schema "0.3.3"] is available but we use "0.3.2"
[metosin/ring-http-response "0.5.2"] is available but we use "0.5.1"
```

## 0.16.4 (10.11.2014)

- use `[org.tobereplaced/lettercase "1.0.0"]` for camel-casing (see https://github.com/metosin/compojure-api-examples/issues/1#issuecomment-62580504)
- updated deps:
```clojure
[metosin/ring-swagger "0.14.1"] is available but we use "0.14.0"
```

## 0.16.3 (9.11.2014)

- support for `:form-parameters`, thanks to [Thomas Whitcomb](https://github.com/thomaswhitcomb)
- update deps:

```clojure
[prismatic/plumbing "0.3.5"] is available but we use "0.3.3"
[potemkin "0.3.11"] is available but we use "0.3.8"
[compojure "1.2.1"] is available but we use "1.1.9"
[prismatic/schema "0.3.2"] is available but we use "0.2.6"
[metosin/ring-http-response "0.5.1"] is available but we use "0.5.0"
[metosin/ring-swagger "0.14.0"] is available but we use "0.13.0"
[lein-ring "0.8.13"] is available but we use "0.8.11"
```

## 0.16.2 (11.9.2014)

- Fixed #47: `:middlewares` broke route parameters

## 0.16.1 (11.9.2014)

- Compiled without AOT
- Removed `:yaml-in-html` and `:clojure` from default response formats

## 0.16.0 (10.9.2014)

- Some cleaning
  - Requires now Clojure 1.6.0 for `clojure.walk`
- Support other formats in addition to JSON
  - Uses the [ring-middleware-format](https://github.com/ngrunwald/ring-middleware-format) to parse requests and encode responses
- Fixes #43: Middlewares added to route with :middlewares shouldn't leak to other routes in same context anymore

## 0.15.2 (4.9.2014)

- Update to latest `ring-swagger`

## 0.15.1 (19.8.2014)

- Update to latest `ring-swagger`
  - Fixes #16: If Schema has many properties, they are now shown in correct order on Swagger-UI
    - `hash-map` loses the order if it has enough properties
    - ~~Use [flatland.ordered.map/ordered-map](https://github.com/flatland/ordered) when Schema has many properties and you want to keep the order intact~~
    - ~~`(s/defschema Thingie (ordered-map :a String ...))`~~

## 0.15.0 (10.8.2014)

- Use latest `ring-swagger`
- `:body` and others no langer take description as third param, instead use `:body [body (describe Schema "The description")]`
  - `describe` works also for Java classes `:query-params [x :- (describe Long "first-param")]`
  - And inside defschema `(s/defschema Schema {:sub (describe [{:x Long :y String}] "Array of stuff")})`

## 0.14.0 (9.7.2014)

- return model coercion returns `500` instead of `400`, thanks to @phadej!
- added support for returning primitives, thanks to @phadej!

```clojure
(GET* "/plus" []
  :return       Long
  :query-params [x :- Long {y :- Long 1}]
  :summary      "x+y with query-parameters. y defaults to 1."
  (ok (+ x y)))
```

- `:responses` restructuring to (error) return codes and models, thanks to @phadej!

```clojure
(POST* "/number" []
  :return       Total
  :query-params [x :- Long y :- Long]
  :responses    {403 ^{:message "Underflow"} ErrorEnvelope}
  :summary      "x-y with body-parameters."
  (let [total (- x y)]
    (if (>= total 0)
      (ok {:total (- x y)})
      (forbidden {:message "difference is negative"}))))
```

## 0.13.3 (28.6.2014)

- support for `s/Uuid` via latest `ring-swagger`.
- fail-fast (with client-typos): remove default implementation from `compojure.api.meta/restructure-param`

## 0.13.2 (28.6.2014)

- restructure `:header-params` (fixes #31)
- remove vanilla compojure-examples, internal cleanup

## 0.13.1 (22.6.2014)

- allow primitives as return types (with help of `[metosin/ring-swagger 0.10.2]`)
  - all primitives are supported when wrapped into sequences and sets
  - directly, only `String` is supported as [Ring](https://github.com/ring-clojure/ring/blob/master/SPEC) doesn't support others
    - in future, there could be a special return value coercer forcing all other primitives as Strings

## 0.13.0 (21.6.2014)

- first take on removing the global route state => instead of global `swagger` atom, there is one defined `+routes+` var per namespace
  - requires a `compojure.api.core/with-routes` on api root to generate and hold the `+routes+` (automatically bundled with `defapi`)
- update ring-swagger to `0.10.1` to get support for `s/Keyword` as a nested schema key.

## 0.12.0 (17.6.2014)

- **possibly breaking change**: `middlewares` macro and `:middlewares` restructuring now use thread-first to apply middlewares
- update ring-swagger to `0.9.1` with support for vanilla `schema.core/defschema` schemas
  - big internal cleanup, removing model var-resolutions, lot's of internal fns removed
- added `defroutes` to `compojure.api.legacy`
- removed defns from `compojure.api.common`: `->Long`, `fn->`, `fn->>`
- cleaner output from `compojure.api.meta/restructure` (doesn't generate empty `lets` & `letks`)

## 0.11.6 (5.6.2014)

- added `compojure.api.legacy` ns to have the old Compojure HTTP-method macros (`GET`, `POST`,...)

## 0.11.5 (1.6.2014)

- Update dependencies:

```
[prismatic/plumbing "0.3.1"] is available but we use "0.2.2"
[compojure "1.1.8"] is available but we use "1.1.7"
[prismatic/schema "0.2.3"] is available but we use "0.2.2"
[metosin/ring-swagger "0.8.8"] is available but we use "0.8.7"
[peridot "0.3.0"] is available but we use "0.2.2"
```

## 0.11.4 (19.5.2014)

- Really updated ring-swagger dependency as I forgot that last with previous release

## 0.11.3 (12.5.2014)

- remove non-first trailing spaces from Compojure-routes for swagger-docs.
- updated dependencies:
  - `[metosin/ring-swagger "0.8.7"]`
  - `[metosin/ring-swagger-ui "2.6.16-2"]`
- Moved swagger-ui handler to ring-swagger

## 0.11.2 (7.5.2014)

- updated dependencies:
  - `[compojure "1.1.7"]`
  - `[prismatic/schema "0.2.2"]`
  - `[metosin/ring-swagger "0.8.5"]`

- `consumes` and `produces` are now feed to `ring-swagger` based on the installed middlewares.

## 0.11.1 (4.5.2014)

- fix for https://github.com/metosin/compojure-api/issues/19

## 0.11.0 (29.4.2014)

- change signature of `restructure-param` to receive key, value and the accumulator. Remove the key from accumulator parameters by default. No more alpha.
- separate restructuring into own namespace `meta`
- **new**: `:middlewares` restructuring to support adding middlewares to routes:

```clojure
 (DELETE* "/user/:id" []
   :middlewares [audit-support (for-roles :admin)]
   (ok {:name "Pertti"})))
```

- **breaking change**: `with-middleware` is renamed to `middlewares` & it applies middlewares in reverse order
- more docs on creating own metadata DSLs
- use `clojure.walk16` internally

## 0.10.4 (16.4.2014)

- fixed https://github.com/metosin/compojure-api/issues/12
- added http-kit example

## 0.10.3 (15.4.2014)

- renamed `clojure.walk` to `clojure.walk16`
- writing routes to `swagger` atom happens now at runtime, not compile-time. Works with AOT.

## 0.10.2 (14.4.2014)

- All `compojure.api.core` restructuring are now using `restructure-param` multimethod to allow external extensions. ALPHA.

## 0.10.1 (13.4.2014)

- FIXED https://github.com/metosin/compojure-api/issues/9
  - `swaggered` resources are now collected in order

## 0.10.0 (10.4.2014)

- fixed bug with missing `+compojure-api-request+` when having both Compojure destructuring & Compojure-api destructuring in place
- added support for `:body-params` (with strict schema):

```clojure
(POST* "/minus" []
  :body-params [x :- Long y :- Long]
  :summary      "x-y with body-parameters"
  (ok {:total (- x y)}))
```

## 0.9.1 (9.4.2014)

- update `ring-swagger` to `0.8.4` to get better basepath-resolution (with reverse-proxies)

## 0.9.0 (6.4.2014)

- support for Schema-aware `:path-parameters` and `query-parameters`:

```clojure
(GET* "/sum" []
  :query-params [x :- Long y :- Long]
  :summary      "sums x & y query-parameters"
  (ok {:total (+ x y)}))

(GET* "/times/:x/:y" []
  :path-params [x :- Long y :- Long]
  :summary      "multiplies x & y path-parameters"
  (ok {:total (* x y)}))
```

## 0.8.7 (30.3.2014)

- `swagger-ui` index-redirect work also under a context when running in an legacy app-server. Thanks to [Juha Syrjälä](https://github.com/jsyrjala) for the PR.

## 0.8.6 (29.3.2014)

- use `instanceof?` to match records instead of `=` with class. Apps can now be uberwarred with `lein ring uberwar`.

## 0.8.5 (25.3.2014)

- update `ring-swagger` to `0.8.3`, generate path-parameters on client side

## 0.8.4 (25.3.2014)

- update `ring-swagger` to `0.8.1`, all JSON-schema generation now done there.

## 0.8.3 (15.3.2014)

- coerce return values with smart destructuring, thanks to [Arttu Kaipiainen](https://github.com/arttuka).
- update `ring-http-response` to `0.4.0`
- handle json-parse-errors by returning JSON
- rewrite `compojure.api.core-integration-test` using `peridot.core`

## 0.8.2 (10.3.2014)

- Swagger path resolution works now with Compojure [regular expression matching in URL parameters](https://github.com/weavejester/compojure/wiki/Routes-In-Detail). Thanks to [Arttu Kaipiainen](https://github.com/arttuka).

```clojure
(context "/api" []
  (GET* ["/item/:name" :name #"[a-z/]+"] [name] identity))
```

- Sets really work now with smart destructuring of `GET*` and `POST*`. Addeds tests to verify.

## 0.8.1 (6.3.2104)

- update `ring-swagger` to `0.7.3`
- initial support for smart query parameter destructuring (arrays and nested params don't get swagger-ui love yet - but work otherwise ok)

```clojure
  (GET* "/echo" []
    :return Thingie
    :query  [thingie Thingie]
    (ok thingie)) ;; here be coerced thingie
```

## 0.8.0 (5.3.2014)

- Breaking change: `compojure.api.routes/defroutes` is now `compojure.api.core/defroutes*` to avoid namespace clashes & promote it's different.
- FIXED https://github.com/metosin/compojure-api/issues/4
  - reverted "Compojures args-vector is now optional with `compojure.api.core` web methods"

## 0.7.3 (4.3.2014)

- removed the Compojure Var pimp. Extended meta-data syntax no longer works with vanilla Compojure but requires the extended macros from `compojure.api.core`.
- update to `Ring-Swagger` to `0.7.2`

## 0.7.2 (3.3.2014)

- date-format can be overridden in the `json-response-support`, thanks to Dmitry Balakhonskiy
- Update `Ring-Swagger` to `0.7.1` giving support for nested Maps:

```clojure
  (defmodel Customer {:id String
                      :address {:street String
                                :zip Long
                                :country {:code Long
                                          :name String}}})
```

- schema-aware body destructuring with `compojure.api.core` web methods does now automatic coercion for the body
- Compojures args-vector is now optional with `compojure.api.core` web methods

```clojure
  (POST* "/customer"
    :return   Customer
    :body     [customer Customer]
    (ok customer))) ;; we have a coerced customer here
```

## 0.7.1 (1.3.2014)

- update `ring-swagger` to `0.7.0`
  - support for `schema/maybe` and `schema/both`
  - consume `Date` & `DateTime` both with and without milliseconds: `"2014-02-18T18:25:37.456Z"` & `"2014-02-18T18:25:37Z"`
- name-parameter of `swaggered` is stripped out of spaces.

## 0.7.0 (19.2.2014)

- update `ring-swagger` to `0.6.0`
  - support for [LocalDate](https://github.com/metosin/ring-swagger/blob/master/CHANGELOG.md).
- updated example to cover all the dates.
- `swaggered` doesn't have to contain container-element (`context` etc.) within, endpoints are ok:

```clojure
  (swaggered "ping"
    :description "Ping api"
    (GET* "/ping" [] (ok {:ping "pong"})))
```

- body parameter in `POST*` and `PUT*` now allows model sequences:

```clojure
  (POST* "/pizzas" []
    :body [pizzas [NewPizza] {:description "new pizzas"}]
    (ok (add! pizzas)))
```

## 0.6.0 (18.2.2014)

- update `ring-swagger` to `0.5.0` to get support for [Data & DateTime](https://github.com/metosin/ring-swagger/blob/master/CHANGELOG.md).

## 0.5.0 (17.2.2014)

- `swaggered` can now follow symbols pointing to a `compojure.api.routes/defroutes` route definition to allow better route composition.
- `compojure.api.sweet` now uses `compojure.api.routes/defroutes` instead of `compojure.core/defroutes`

## 0.4.1 (16.2.2014)

- Fixed JSON Array -> Clojure Set coercing with Strings

## 0.4.0 (13.2.2014)

- Initial public version
