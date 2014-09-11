## 0.16.2 (11.9.2014)

- Fixed #47: `:middlewares` broke route parameters

## 0.16.1 (11.9.2014)

- Compiled without AOT
- Removed `:yaml-in-html` and `:clojure` from default response formats

## 0.16.0 (10.9.2014)

- Some cleaning
  - Requires now clojure 1.6.0 for `clojure.walk`
- Support other formats in addition to JSON
  - Uses the [ring-middleware-format](https://github.com/ngrunwald/ring-middleware-format) to parse requests and encode responses
- Fixes #43: Middlewares added to route with :middlewares shouldn't leak to other routes in same context anymore

## 0.15.2 (4.9.2014)

- Update to latest `ring-swagger`

## 0.15.1 (19.8.2014)

- Update to latest `ring-swagger`
  - Fixes #16: If Schema has many properties, they are now shown in correct order on Swagger-UI
    - `hash-map` loses the order if it has enough properties
    - Use [flatland.ordered.map/ordered-map](https://github.com/flatland/ordered) when Schema has many properties and you want to keep the order intact
    - `(s/defschema Thingie (ordered-map :a String ...))`

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
- update ring-swagger to `0.9.1` with support for vanila `schema.core/defschema` schemas
  - big internal cleanup, removing model var-resolutions, lot's of internal fn's removed
- added `defroutes` to `compojure.api.legacy`
- removed defns from `compojure.api.common`: `->Long`, `fn->`, `fn->>`
- cleaner output from `compojure.api.meta/restructure` (doesn't generate empty `lets` & `letks`)

## 0.11.6 (5.6.2014)

- added `compojure.api.legacy` ns to have the old Compojure http-method macros (`GET`, `POST`,...)

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

- Really updated ring-swagger dependancy as I forgot that last with previous release

## 0.11.3 (12.5.2014)

- remove non-first trailing spaces from compojure-routes for swagger-docs.
- updated depedencies:
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

- fixed bug with missing `+compojure-api-request+` when having both compojure descruction & compojure-api desruction in place
- added support for `:body-params` (with strict schema):

```clojure
(POST* "/minus" []
  :body-params [x :- Long y :- Long]
  :summary      "x-y with body-parameters"
  (ok {:total (- x y)}))
```

## 0.9.1 (9.4.2014)

- update `ring-swagger` to `0.8.4` to get better basepath-resolution (with reverse-proxys)

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

- update `ring-swagger` to `0.8.1`, all json-schema generation now done there.

## 0.8.3 (15.3.2014)

- coerce return values with smart destructuring, thanks to [Arttu Kaipiainen](https://github.com/arttuka).
- update `ring-http-response` to `0.4.0`
- handle json-parse-errors by returning json
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
  - consume `Date` & `DateTime` both with and without millis: `"2014-02-18T18:25:37.456Z"` & `"2014-02-18T18:25:37Z"`
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

- Fixed JSON Array -> Clojure Set coarcing with Strings

## 0.4.0 (13.2.2014)

- Initial public version
