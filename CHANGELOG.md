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
