## 0.8.1 (6.3.2104)

- update `ring-swagger` to `0.7.3`
- initial support for smart query parameter destruction (arrays and nested params don't get swagger-ui love yet - but work otherwise ok)

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
