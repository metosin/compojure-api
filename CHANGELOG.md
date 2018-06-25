## 2.0.0-SNAPSHOT

* **BREAKING**: Use Muuntaja 0.6.0
  * See all changes in the [Muuntaja CHANGELOG](https://github.com/metosin/muuntaja/blob/master/CHANGELOG.md)
  * Highlights:
    * Change default JSON Serializer from [Cheshire]() to [Jsonista]()
    * Both [Joda Time](http://www.joda.org/joda-time/) and [java.time](https://docs.oracle.com/javase/8/docs/api/java/time/package-summary.html) are supported out-of-the-box
    * up to [6x faster encoding](https://github.com/metosin/jsonista#performance)
    * different [configuration params](https://cljdoc.xyz/d/metosin/jsonista/0.2.1/api/jsonista.core#object-mapper), guarded by migration assertion
    * **BREAKING**: by default Jackson tries to encode everything, 
       * e.g. `java.security.SecureRandom` can be serialized, via reflection
    * **BREAKING**: decoding doesn't try to keep the field order for small maps
  * `muuntaja.core/install` helper to add new formats:
  
```clj
(require '[compojure.api.sweet :refer :all])
(require '[ring.util.http-response :refer :all])

(require '[muuntaja.core :as m])
(require '[muuntaja.format.msgpack]) ;; [metosin/muuntaja-msgpack]
(require '[muuntaja.format.yaml])    ;; [metosin/muuntaja-yaml]

(def formats 
  (m/create
    (-> m/default-options
        (m/install muuntaja.format.msgpack/format)
        (m/install muuntaja.format.yaml/format)
        ;; return byte[] for NIO servers
        (assoc :return :bytes))))

(api
  {:formats formats}
  (POST "/ping" []
    (ok {:ping "pong"})))
```  

* add `compojure.api.middleware/wrap-format` to support multiple apis (or api + external static routes)in a project, fixes [#374](https://github.com/metosin/compojure-api/issues/374)

```clj
(require '[compojure.api.sweet :refer :all])
(require '[ring.util.http-response :refer [ok]])
(require '[compojurea.api.middeware :as middleware])

(-> (routes
      (api
        (POST "/echo1" []
          :body [body s/Any]
          (ok body)))
      (api

        (POST "/echo2" []
          :body [body s/Any]
          (ok body))))
    (middleware/wrap-format))
```

* update deps:

```clj
[metosin/ring-swagger "0.26.1"] is available but we use "0.26.0"
[metosin/muuntaja "0.6.0-SNAPSHOT"] is available but we use "0.5.0"
```

## 2.0.0-alpha20 (2018-05-15)

* welcome [spec transformers!](http://testi.metosin.fi/blog/spec-transformers/)! might break custom `coercion` implementations

* update deps:

```clj
[potemkin "0.4.5"] is available but we use "0.4.4"
[prismatic/schema "1.1.9"] is available but we use "1.1.7"
[frankiesardo/linked "1.3.0"] is available but we use "1.2.9"
[compojure "1.6.1"] is available but we use "1.6.0"
[metosin/spec-tools "0.7.0"] is available but we use "0.6.1"
[metosin/jsonista "0.2.0"] is available but we use "0.1.1"
```

## 2.0.0-alpha19 (2018-03-13)

* Deal with coercion exceptions in async handlers, fixes [#371](https://github.com/metosin/compojure-api/issues/371), by [Benjamin Teuber](https://github.com/bsteuber)
* updated deps:

```clj
[metosin/ring-swagger "0.26.0"] is available but we use "0.25.0"
[metosin/spec-tools "0.6.1"] is available but we use "0.5.1"
```

## 1.1.12 (2018-02-27)

Maintenance 1.1 release, adding several patches from 2.0 branch.

- Backport: Fix context child resolution with compojure-bindings [#370](https://github.com/metosin/compojure-api/issues/370)
- Backport: merge-vector [#311](https://github.com/metosin/compojure-api/issues/311)
- Backport: Fix metadata position on defmacro to activate CIDER indent style [#261](https://github.com/metosin/compojure-api/issues/261)

## 2.0.0-alpha18 (2018-01-18)

* updated deps:

```clj
[metosin/muuntaja "0.5.0"] is available but we use "0.4.1"
```

## 2.0.0-alpha17 (2018-01-10)

* **BREAKING**: drop `defapi`. `def` + `api` should be used instead.
* Cleanup conflicting transitive dependencies
* Supports both old (2.*) and new (3.*) swagger-uis.

```clj
[metosin/muuntaja "0.4.2"] is available but we use "0.4.1"
[metosin/ring-swagger "0.25.0"] is available but we use "0.24.3"
```

## 2.0.0-alpha16

* Rolled back the latest swagger-ui, which fails in `config not found`. There is an [issue in ring-swagger](https://github.com/metosin/ring-swagger/pull/123).

```clj
[metosin/ring-swagger-ui "2.2.10"] is available but we use "3.0.17"
```

## 2.0.0-alpha15

* updated deps:

```clj
[metosin/muuntaja "0.4.1"] is available but we use "0.4.0"
```

## 2.0.0-alpha14

* Fixes Muuntaja-bug of randomly failing on `:body` parameters with some server setups.

* updated deps:

```clj
[prismatic/plumbing "0.5.5"] is available but we use "0.5.4"
[metosin/muuntaja "0.4.0"] is available but we use "0.3.3"
[ring/ring-core "1.6.3"] is available but we use "1.6.2"
[metosin/spec-tools "0.5.1"] is available but we use "0.5.0"
```

## 2.0.0-alpha13 (2017-11-18)

* Better error messages for bad `letk` syntax by [Erik Assum](https://github.com/slipset), fixes [#354](https://github.com/metosin/compojure-api/issues/354)
* `:coercion` applies for `context` parameters too (not just childs)

* updated deps:

```clj
[metosin/muuntaja "0.3.3"] is available but we use "0.3.2"
```

## 2.0.0-alpha12 (2017-10-26)

* route sequences also produce swagger docs.

## 2.0.0-alpha11 (2017-10-25)

* `dynamic-context` is removed in favor of `:dynamic true` meta-data for contexts:

```clj
(require '[compojure.api.help :as help])

(help/help :meta :dynamic)
; :dynamic
;
; If set to to `true`, makes a `context` dynamic,
; e.g. body is evaluated on each request. NOTE:
; Vanilla Compojure has this enabled by default
; while compojure-api default to `false`, being
; much faster. For details, see:
;
; https://github.com/weavejester/compojure/issues/148
;
; (context "/static" []
;   (if (= 0 (random-int 2))
;      ;; mounting decided once
;      (GET "/ping" [] (ok "pong")))
;
; (context "/dynamic" []
;   :dynamic true
;   (if (= 0 (random-int 2))
;      ;; mounted for 50% of requests
;      (GET "/ping" [] (ok "pong")))
```

* You can now include sequences of routes in `routes` and `context`:

```clj
(context "/api" []
  (for [path ["/ping" "/pong"]]
    (GET path [] (ok {:path path}))))
```

* updated deps:

```clj
[metosin/ring-swagger "0.24.3"] is available but we use "0.24.2"
```

## 2.0.0-alpha10 (21.10.2017)

* `ANY` produces swagger-docs for all methods, thanks to [Anthony](https://github.com/acron0)

* Updated deps:

```clj
[metosin/spec-tools "0.5.0"] is available but we use "0.4.0"
```

## 2.0.0-alpha9 (18.10.2017)

* Stringify `:pred` under Spec `:problems`, fixes [#345](https://github.com/metosin/compojure-api/issues/345)
* Better error message if `:spec` coercion is tried without required dependencies
* Don't memoize keyword-specs for now - allow easier redefining

## 2.0.0-alpha8 (11.10.2017)

* Aligned with the latest spec-tools: `[metosin/spec-tools "0.4.0"]`

* To use Clojure 1.9 & Spec with Swagger, these need to be imported:

```clj
[org.clojure/clojure "1.9.0-beta2"]
[metosin/spec-tools "0.4.0"]
```

* Support `ring.middleware.http-response` exception handling directly:

```clj
(require '[compojure.api.sweet :refer :all])
(require '[ring.util.http-response :as http])

(api
  {:exceptions {:handlers {::http/response handle-thrown-http-exceptions-here}}
  (GET "/throws" []
    (http/bad-request! {:message "thrown response"})))
```

* Use Muuntaja for all JSON transformations, drop direct dependency to Cheshire.
* Muuntaja `api` instance (if not undefined, e.g. `nil`) is injected into `:compojure.api.request/muuntaja` for endpoints to use.
  * `path-for` and `path-for*` now use this to encode path-parameters.

```clj
(require '[compojure.api.sweet :refer :all])
(require '[compojure.api.request :as request])
(require '[muuntaja.core :as m])

(api
  (GET "/ping" {:keys [::request/muuntaja]}
    (ok {:json-string (slurp (m/encode muuntaja "application/json" [:this "is" 'JSON]))})))
```

* **FIXED**: separate `Muuntaja`-instance optins are merged correctly.

```clj
(require '[compojure.api.sweet :refer :all])
(require '[ring.util.http-response :refer [ok]])
(require '[metosin.transit.dates :as transit-dates])
(require '[muuntaja.core :as m])

(def muuntaja
  (m/create
    (-> muuntaja/default-options
        (update-in
          [:formats "application/transit+json"]
          merge
          {:decoder-opts {:handlers transit-dates/readers}
           :encoder-opts {:handlers transit-dates/writers}}))))

(api
  {:formats muuntaja}
  (GET "/pizza" []
    (ok {:now (org.joda.time.DateTime/now)})))
```

* dropped dependencies:

```clj
[cheshire "5.7.1"]
[org.tobereplaced/lettercase "1.0.0"]
```

* updated dependencies:

```clj
[potemkin "0.4.4"] is available but we use "0.4.3"
[metosin/ring-swagger "0.24.2"] is available but we use "0.24.1"
[metosin/spec-tools "0.4.0"] is available but we use "0.3.2"
```

## 2.0.0-alpha7 (31.7.2017)

* drop direct support for `application/yaml` & `application/msgpack`. If you want to add them back, you need to manually add the dependencies below and configure Muuntaja to handle those:

```clj
(require '[muuntaja.core :as muuntaja])
(require '[muuntaja.format.yaml :as yaml-format])
(require '[muuntaja.format.msgpack :as msgpack-format])

(api
  {:formats (-> muuntaja/default-options)
                (yaml-format/with-yaml-format)
                (msgpack-format/with-msgpack-format))}
  ...)
```

* dropped dependencies:

```clj
[circleci/clj-yaml "0.5.6"]
[clojure-msgpack "1.2.0"]
```

## 2.0.0-alpha6 (26.7.2017)

* spec coericon also calls `s/unform` after `s/conform`, e.g. specs like `(s/or :int spec/int? :keyword spec/keyword?)` work now too.

* updated dependencies:

```clj
[circleci/clj-yaml "0.5.6"] is available but we use "0.5.5"
[metosin/muuntaja "0.3.2"] is available but we use "0.3.1"
[ring/ring-core "1.6.2"] is available but we use "1.6.1"
[metosin/ring-swagger "0.24.1"] is available but we use "0.24.0"
```

## 1.1.11 (25.7.2017)

* updated deps for the 1.*
* **BREAKING**: in `compojure.api.swagger`, the `swagger-ui` and `swagger-docs` now take options map with `path` key instead of separate optional path & vararg opts.
  - normally you would use swagger api-options or `swagger-routes` and thus be unaffected of this.

* updated dependencies:

```clj
[prismatic/plumbing "0.5.4"] is available but we use "0.5.3"
[compojure "1.6.0"] is available but we use "1.5.2"
[prismatic/schema "1.1.6"] is available but we use "1.1.3"
[ring-middleware-format "0.7.2"] is available but we use "0.7.0"
[metosin/ring-http-response "0.9.0"] is available but we use "0.8.1"
[metosin/ring-swagger "0.24.1"] is available but we use "0.22.14"
```

## 2.0.0-alpha5 (2.7.2017)

* Spec coercion endpoints produce now Swagger2 data
  * thanks to latest [spec-tools](https://github.com/metosin/spec-tools#swagger2-integration)

* To use Clojure 1.9 & Spec with Swagger, these need to be imported:

```clj
[org.clojure/clojure "1.9.0-alpha17"]
[metosin/spec-tools "0.3.0"]
```

* To use Clojure 1.8 & Spec with Swagger, these need to be imported:

```clj
[org.clojure/clojure "1.8.0"]
[metosin/spec-tools "0.3.0" :exclusions [org.clojure/spec.alpha]]
[clojure-future-spec "1.9.0-alpha17"]
```

* If the dependencies are found, the following entry should appear on log:

```
INFO :spec swagger generation enabled in compojure.api
```

* updated deps:

```
[metosin/spec-tools "0.3.0"] is available but we use "0.2.2"
```

* Schema coercion errors have the :schema as `pr-str` value.
* `resource` body-params are associated over existing instead of merged. e.g. extra params are really stripped off.

## 2.0.0-alpha4 (19.6.2017)

* Update to latest [Muuntaja](https://github.com/metosin/muuntaja).
  * by default, allow empty input body for all formats
  * see [changelog](https://github.com/metosin/muuntaja/blob/master/CHANGELOG.md#030-1962017)

* updated deps:

```
[metosin/muuntaja "0.3.1"] is available but we use "0.2.2"
```

## 2.0.0-alpha3 (13.6.2017)

* move `compojure.api.request` back to `src`.

## 2.0.0-alpha2 (13.6.2017)

* **BREAKING**: Simplified pluggable coercion.
  * **NO SWAGGER-DOCS YET**, see https://github.com/metosin/spec-swagger
  * guide in wiki: https://github.com/metosin/compojure-api/wiki/Coercion
  * injected in request under `:compojure.api.request/coercion`
  * new namespace `compojure.api.coercion`, replacing `compojure.api.coerce`.
  * `:coercion` can be set to `api`, `context`, endpoint macros or a `resource`. It can be either:
     * something satisfying `compojure.api.coercion.core/Coercion`
     * a Keyword for looking up a predefined `Coercion` via `compojure.api.coercion.core/named-coercion` multimethod.
  * `coercion` is stored in Route `:info`
  * signature of `Coercion`:

```clj
(defprotocol Coercion
  (get-name [this])
  (get-apidocs [this spec data])
  (encode-error [this error])
  (coerce-request [this model value type format request])
  (coerce-response [this model value type format request]))
```

### Predefined coercions

* `:schema` (default) resolves to `compojure.api.coercion.schema/SchemaCoercion`
* `:spec` resolves to `compojure.api.coercion.spec/SpecCoercion`
  * automatically available if [spec-tools](https://github.com/metosin/spec-tools) is found in classpath
  * to enable runtime conforming, use [Spec Records](https://github.com/metosin/spec-tools#spec-records)
  * works both with vanilla specs & [data-specs](https://github.com/metosin/spec-tools#data-specs)
* `nil` removes the coercion (was: `nil` or `(constantly nil)`).

#### Spec with resources

```clj
(require '[compojure.api.sweet :refer :all])
(require '[clojure.spec.alpha :as s])
(require '[spec-tools.spec :as spec])

(s/def ::id spec/int?)
(s/def ::name spec/string?)
(s/def ::description spec/string?)
(s/def ::type spec/keyword?)
(s/def ::new-pizza (s/keys :req-un [::name ::type] :opt-un [::description]))
(s/def ::pizza (s/keys :req-un [::id ::name ::type] :opt-un [::description]))

(resource
  {:coercion :spec
   :summary "a spec resource, no swagger yet"
   :post {:parameters {:body-params ::new-pizza}
          :responses {200 {:schema ::pizza}}
          :handler (fn [{new-pizza :body-params}]
                     (ok (assoc new-pizza :id 1)))}})
```

#### Spec with endpoints

```clj
(require '[spec-tools.data-spec :as ds])

(s/def ::id spec/int?)

(context "/spec" []
  :coercion :spec

  (POST "/pizza" []
    :summary "a spec endpoint"
    :return ::pizza
    :body [new-pizza ::new-pizza]
    (ok (assoc new-pizza :id 1)))

  (POST "/math/:x" []
    :summary "a spec endpoint"
    :return {:total int?}
    :path-params [x :- spec/int?]
    :query-params [y :- spec/int?,
                   {z :- spec/int? 0}]
    (ok {:total (+ x y z)})))
```

* To use Clojure 1.9 & Spec, these need to be imported:

```clj
[org.clojure/clojure "1.9.0-alpha17"]
[metosin/spec-tools "0.2.2"]
```

* To use Clojure 1.8 & Spec, these need to be imported:

```clj
[org.clojure/clojure "1.8.0"]
[metosin/spec-tools "0.2.2" :exclusions [org.clojure/spec.alpha]]
[clojure-future-spec "1.9.0-alpha17"]
```

* **BREAKING**: Clojure 1.7.0 is no longer supported (no back-port for `clojure.spec`).

* use ClassLoader -scoped Schema memoization instead of api-scoped - same for anonymous map specs

* `:body-params` is available for exception handlers, fixes [#306](https://github.com/metosin/compojure-api/issues/306) & [#313](https://github.com/metosin/compojure-api/issues/313)

* **BREAKING**: Restructuring internal key changes in `compojure.api.meta`:
  * `:swagger` is removed in favor of `:info`.
  * swagger-data is pushed to `[:info :public]` instead of `[:swagger]`
  * top-level `:info` can contain:
    - `:static-context?` -> `true` if the `context` is internally optimized as static
    - `:name`, route name
    - `:coercion`, the defined coercion

## 2.0.0-alpha1 (30.5.2017)

* More descriptive error messages, fixes [#304](https://github.com/metosin/compojure-api/issues/304) and [#306](https://github.com/metosin/compojure-api/issues/306):
  * when request or response validation fails, more info is provided both to exception hanlders and via default implementations to external clients:

```clj
(let [app (GET "/" []
              :return {:x String}
              (ok {:kikka 2}))]
  (try
    (app {:request-method :get, :uri "/"})
    (catch Exception e
      (ex-data e))))
; {:type :compojure.api.exception/response-validation,
;  :validation :schema,
;  :in [:response :body],
;  :schema {:x java.lang.String},
;  :errors {:x missing-required-key,
;           :kikka disallowed-key},
;  :response {:status 200,
;             :headers {},
;             :body {:kikka 2}}}
```

```clj
(let [app (GET "/" []
            :query-params [x :- String]
            (ok))]
  (try
    (app {:request-method :get, :uri "/" :query-params {:x 1}})
    (catch Exception e
      (ex-data e))))
; {:type :compojure.api.exception/request-validation,
;  :validation :schema,
;  :value {:x 1},
;  :in [:request :query-params],
;  :schema {Keyword Any, :x java.lang.String},
;  :errors {:x (not (instance? java.lang.String 1))},
;  :request {:request-method :get,
;            :uri "/",
;            :query-params {:x 1},
;            :route-params {},
;            :params {},
;            :compojure/route [:get "/"]}}
```

* Introduce `dynamic-context` that works like `context` before the fast context optimization ([#253](https://github.com/metosin/compojure-api/pull/253)).
  * If you build routes dynamically inside `context`, they will not work as intended. If you need this, replace `context` with `dynamic-context`.
  * See issue [#300](https://github.com/metosin/compojure-api/issues/300).

  For example:

```clj
;; compojure-api 1.1
(context "/static" []
  (if (its-noon?)
    (GET "/noon-route" [] (ok "it's noon")))

;; compojure-api 1.2:
(dynamic-context "/static" []
  (if (its-noon?)
    (GET "/noon-route" [] (ok "it's noon")))
```

* Remove restructuring migration helpers for `1.0.0` (for `:parameters` and `:middlewares`)

## 1.2.0-alpha8 (18.5.2017)

* **BREAKING**: `resource` function is always 1-arity, options and info are merged.

* `resource` can have `:middleware` on both top-level & method-level.
  * top-level mw are applied first if the resource can handle the request
  * method-level mw are applied second if the method matches

```clj
(def mw [handler value]
  (fn [request]
    (println value)
    (handler request)))

(resource
  {:middleware [[mw :top1] [mw :top2]]
   :get {:middleware [[mw :get1] [mw :get2]]}
   :post {:middleware [[mw :post1] [mw :post2]]}
   :handler (constantly (ok))})
```

* updated deps:

```clj
[prismatic/schema "1.1.6"] is available but we use "1.1.5"
```

## 1.2.0-alpha7 (15.5.2017)

* **BREAKING**: `resource` separates 1-arity `:handler` and 3-arity `:async-handler`. Rules:
  * if resource is called with 1-arity, `:handler` is used, sent via `compojure.response/render`
  * if resource is called with 3-arity, `:async-handler` is used, with fallback to `:handler`.
    * sent via `compojure.response/send` so [manifold](https://github.com/ztellman/manifold) `Deferred` and [core.async](https://github.com/clojure/core.async) `ManyToManyChannel` can be returned.

```clj
(require '[compojure.api.sweet :refer :all])
(require '[clojure.core.async :as a])
(require '[manifold.deferred :as d])

(resource
  {:summary "async resource"
   :get {:summary "normal ring async"
         :async-handler (fn [request respond raise]
                          (future
                            (Thread/sleep 100)
                            (respond (ok {:hello "world"})))
                          nil)}
   :put {:summary "core.async"
         :handler (fn [request]
                    (a/go
                      (a/<! (a/timeout 100))
                      (ok {:hello "world"})))}
   :post {:summary "manifold"
          :handler (fn [request]
                     (d/future
                       (Thread/sleep 100)
                       (ok {:hello "world"})))}})
```

* updated deps:

```clj
[ring/ring-core "1.6.1"] is available but we use "1.6.0"
```

## 1.2.0-alpha6 (12.5.2017)

* depend directly on `[ring/ring-core "1.6.0"]`
* `compojure.api.core` depends on `compojure.api.async`
  * both [manifold](https://github.com/ztellman/manifold) `Deferred` and [core.async](https://github.com/clojure/core.async) `ManyToManyChannel` can be returned from endpoints.
* `resource` now supports async (3-arity) handlers as well.

```clj
(resource
  {:parameters {:query-params {:x Long}}
   :handler (fn [request respond raise]
              (future
                (res (ok {:total (-> request :query-params :x)})))
              nil)})
```

* updated deps:

```clj
[ring/ring-core "1.6.0"]
[cheshire "5.7.1"] is available but we use "5.7.0"
[compojure "1.6.0"] is available but we use "1.5.2"
[prismatic/schema "1.1.5"] is available but we use "1.1.4"
[prismatic/plumbing "0.5.4"] is available but we use "0.5.3"
[metosin/ring-http-response "0.9.0"] is available but we use "0.8.2"
[metosin/ring-swagger "0.24.0"] is available but we use "0.23.0"
[compojure "1.6.0"] is available but we use "1.6.0-beta3"
```

## 1.2.0-alpha5 (31.3.2017)

* Use the latest Muuntaja.
* Test with `[org.clojure/clojure "1.9.0-alpha15"]` (requires Midje `1.9.0-alpha6`)

```clj
[metosin/muuntaja "0.2.1"] is available but we use "0.2.0-20170323.064148-15"
```

## 1.2.0-alpha4 (23.3.2017)

* Initial support for Async Ring, using CPS, [manifold](https://github.com/ztellman/manifold) or [core.async](https://github.com/clojure/core.async)
  * more info at https://github.com/metosin/compojure-api/wiki/Async

* `compojure.api.core/ring-handler` to turn a compojure-api route into a 1-arity function
  * can be passed into servers requiring handlers to be `Fn`

* `:params` are populated correctly from `:body-params`

* Allow `nil` paths in routing, allows easy (static) conditional routing like:

```clj
(defn app [dev-mode?]
  (api
    (GET "ping" [] (ok "pong"))
    (if dev-mode?
      (GET "/drop-the-db" [] (ok "dropped")))))
```

* Support `java.io.File` as response type, mapping to file downloads
  * no response coercion
  * fixes [#259](https://github.com/metosin/compojure-api/issues/259)

```clj
(GET "/file" []
  :summary "a file download"
  :return java.io.File
  :produces #{"image/png"}
  (-> (io/resource "screenshot.png")
      (io/input-stream)
      (ok)
      (header "Content-Type" "image/png"))))
```

* Fix help-for for some restructure methods [#275](https://github.com/metosin/compojure-api/pull/275) by [Nicolás Berger](https://github.com/nberger)
* **BREAKING**: in `compojure.api.swagger`, the `swagger-ui` and `swagger-docs` now take options map with `path` key instead of separate optional path & vararg opts.
  - normally you would use swagger api-options or `swagger-routes` and thus be unaffected of this.
* **BREAKING**: `middleware` is removed because it dangerously applied the
middleware even to requests that didn't match the contained routes. New `route-middleware`
only applies middlewares when the request is matched against contained routes.
  * `route-middleware` is not exposed in `sweet` namespace but is available at `compojure.api.core`

* Updated deps:

```clj
[metosin/muuntaja "0.2.0-20170323.064148-15"] is available but we use "0.2.0-20170122.164054-8"
[prismatic/schema "1.1.4"] is available but we use "1.1.3"
[metosin/ring-swagger-ui "2.2.10"] is available but we use "2.2.8"
[metosin/ring-swagger "0.23.0"] is available but we use "0.22.14"
[metosin/ring-http-response "0.8.2"] is available but we use "0.8.1"
```

## 1.2.0-alpha3 (31.1.2017)

* Class-based exception handling made easier, the `[:exceptions :handlers]` options also allows exception classes as keys.
  * First do a `:type`-lookup, then by Exception class and it's superclasses.
  * Fixes [#266](https://github.com/metosin/compojure-api/issues/272)

```clj
(api
  {:exceptions
   {:handlers
     {::ex/default handle-defaults
      java.sql.SQLException handle-all-sql-exceptions}}}
   ...)
```

* Lovely inline-help, `compojure.api.help/help`.

```clojure
(require '[compojure.api.help :refer [help]])

(help)
; ------------------------------------------------------------
; Usage:
;
; (help)
; (help topic)
; (help topic subject)
;
; Topics:
;
; :meta
;
; Topics & subjects:
;
; :meta :body
; :meta :body-params
; :meta :coercion
; :meta :components
; :meta :consumes
; :meta :description
; :meta :form-params
; :meta :header-params
; :meta :middleware
; :meta :multipart-params
; :meta :name
; :meta :no-doc
; :meta :operationId
; :meta :path-params
; :meta :produces
; :meta :responses
; :meta :return
; :meta :summary
; :meta :swagger
; :meta :tags

(help/help :meta :middleware)
; ------------------------------------------------------------
;
; :middleware
;
; Applies the given vector of middleware to the route.
; Middleware is presented as data in a Duct-style form:
;
; 1) ring mw-function (handler->request->response)
;
; 2) mw-function and it's arguments separately - mw is
;    created by applying function with handler and args
;
; (defn require-role [handler role]
;   (fn [request]
;     (if (has-role? request role)
;       (handler request)
;       (unauthorized))))
;
; (def require-admin (partial require-role :admin))
;
; (GET "/admin" []
;   :middleware [require-admin]
;   (ok))
;
; (GET "/admin" []
;   :middleware [[require-role :admin]]
;   (ok))
;
; (GET "/admin" []
;   :middleware [#(require-admin % :admin)]
;   (ok))
;
```

* help can be of anything. contributing to help:

```clojure
(defmethod help/help-for [:restructuring :query-params] [_ _]
  (help/text
    "Restructures query-params with plumbing letk notation.\n"
    "Example: read x and optionally y (defaulting to 1)"
    "from query parameters. Body of the endpoint sees the"
    "coerced values.\n"
    (help/code
      "(GET \"/ping\""
      "  :query-params [x :- Long, {y :- Long 1}]"
      "  (ok (+ x y)))")))
```


* Updated deps:

```clj
[metosin/muuntaja "0.2.0-20170130.142747-9"] is available but we use "0.2.0-20170122.164054-8"
```

## 1.2.0-alpha2 (22.1.2017)

**this is an alpha release, feedback welcome**

* **BREAKING**: Requires Java 1.8 (as Muuntaja requires it)
* Fix Cider indentation for route macros, by [Joe Littlejohn](https://github.com/joelittlejohn)
* Restructuring `:body` does not keywordize all keys,
  * e.g. EDN & Transit keys are not transformed, JSON keys based on the JSON decoder settings (defaulting to `true`).
* `resource` under `context`  requires exact routing match, fixes [#269](https://github.com/metosin/compojure-api/issues/269)
* Endpoints can return `compojure.api.routes/Routes`, returned routes don't commit to swagger-docs - as they can be generated at runtime
* **BREAKING**: Better request & response coercion
  * in `compojure.api.middleware`, the `default-coercion-matchers` is removed in favour of `create-coercion` & `default-coercion-options`
  * uses negotiated format information provided by [Muuntaja](https://github.com/metosin/muuntaja#request), fixes [#266](https://github.com/metosin/compojure-api/issues/266)
  * old custom `coercion` should work as before, as the contract has not changed
  * **Old defaults**: coerce everything (request & response body) with `json-coercion-matcher`
  * **New defaults**: see the table below:

| Format | Request | Response |
| --------|:-------:|:------------:|
| `application/edn` | validate | validate |
| `application/transit+json` | validate | validate |
| `application/transit+msgpack` | validate | validate |
| `application/json` | `json-coercion-matcher` | validate |
| `application/msgpack` | `json-coercion-matcher` | validate |
| `application/x-yaml` | `json-coercion-matcher` | validate |

defaults as code:

```clj
(def default-coercion-options
  {:body {:default (constantly nil)
          :formats {"application/json" json-coercion-matcher
                    "application/msgpack" json-coercion-matcher
                    "application/x-yaml" json-coercion-matcher}}
   :string string-coercion-matcher
   :response {:default (constantly nil)
              :formats {}}})
```

to create a valid `coercion` (for api or to routes):

```clj
;; create (with defaults)
(mw/create-coercion)
(mw/create-coercion mw/default-coercion-options)

;; no response coercion
(mw/create-coercion (dissoc mw/default-coercion-options :response)

;; disable all coercion
nil
(mw/create-coercion nil)
```

* Route-records printing is cleaned up

```clj
(context "/api" []
  (GET "/ping" [] (ok))
  (POST "/echo" []
    :body [data {:name s/Str}]
    :return {:name s/Str}
    (ok data))
  (context "/resource" []
    (resource
      {:get {:handler (constantly (ok))}})))
; #Route {:path "/api",
;         :childs [#Route {:path "/ping"
;                          :method :get}
;                  #Route {:path "/echo",
;                          :method :post,
;                          :info {:parameters {:body {:name Str}},
;                                 :responses {200 {:schema {:name Str}
;                                                  :description ""}}}}
;                  #Route {:path "/resource"
;                          :childs [#Route{:childs [#Route{:path "/"
;                                                          :method :get}]}]}]}
```

* Updated deps:

```clj
[cheshire "5.7.0"] is available but we use "5.6.3"
[metosin/muuntaja "0.2.0-20170122.164054-8"] is available but we use "0.2.0-20161031.085120-3"
[metosin/ring-http-response "0.8.1"] is available but we use "0.8.0"
[metosin/ring-swagger "0.22.14"] is available but we use "0.22.12"
[metosin/ring-swagger-ui "2.2.8"] is available but we use "2.2.5-0"
```

## 1.2.0-alpha1

* **BREAKING**: use [Muuntaja](https://github.com/metosin/muuntaja) instead of [ring-middleware-format](https://github.com/ngrunwald/ring-middleware-format), [#255](https://github.com/metosin/compojure-api/pull/255)
  for format negotiation, encoding and decoding.
  - 4x more throughput on 1k JSON request-response echo
  - api key `:format` has been deprecated (fails at api creation time), use `:formats` instead. It consumes either a
    Muuntaja instance, Muuntaja options map or `nil` (unmounts it). See [how to configure Muuntaja](https://github.com/metosin/muuntaja/wiki/Configuration) how to use.
* ~~**EXPERIMENTAL**~~: fast `context`s, [#253](https://github.com/metosin/compojure-api/pull/253) - use static routes if a `context` doesn't do any lexical bindings
  - up to 4x faster `context` routing.
* Support delayed child route resolution.
* Removed pre 0.23.0 api option format assertions.
* `:middleware` for `api` & `api-middleware`, run last just before the actual routes. Uses same syntax as with the routing macros.

```clj
(api
  {:middleware [no-cache [wrap-require-role :user]]}
  ...)
```

* Updated deps:

```clj
[metosin/muuntaja "0.2.0-SNAPSHOT"]
[metosin/ring-swagger "0.22.12"] is available but we use "0.22.11"
```

* Removed deps:

```clj
[ring-middleware-format "0.7.0"]
```

## 1.1.10 (11.1.2017)

* Updated dependencies to [avoid a path traversal vulnerability](https://groups.google.com/forum/#!topic/clojure/YDrKBV26rnA) in Ring.

```clj
[compojure "1.5.2"] is available but we use "1.5.1"
[metosin/ring-http-response "0.8.1"] is available but we use "0.8.0"
[metosin/ring-swagger "0.22.14"] is available but we use "0.22.11"
[metosin/ring-swagger-ui "2.2.8"] is available but we use "2.2.5-0"
```

## 1.1.9 (23.10.2016)

* Fix `:header-params` with resources, [#254](https://github.com/metosin/compojure-api/issues/254)
* updated dependencies:

```clj
[frankiesardo/linked "1.2.9"] is available but we use "1.2.7"
[metosin/ring-swagger "0.22.11"] is available but we use "0.22.10"
[metosin/ring-swagger-ui "2.2.5-0"] is available but we use "2.2.2-0"
```

## 1.1.8 (29.8.2016)

* Lot's of new swagger-bindings from Ring-swagger:
  * `schema.core.defrecord`
  * `org.joda.time.LocalTime`
  * primitive arrays, fixes [#177](https://github.com/metosin/compojure-api/issues/177)
  * `s/Any` in body generates empty object instead of nil
* Bundled with latest swagger-ui `2.2.2-0`

* Updated deps:

```clj
[metosin/ring-swagger "0.22.10"] is available but we use "0.22.9"
[metosin/ring-swagger-ui "2.2.2-0"] is available but we use "2.2.1-0"
```

## 1.1.7 (24.8.2016)

* Bundled with the latest Swagger-ui (2.2.1-0)

* Updated deps:

```clj
[metosin/ring-swagger-ui "2.2.1-0"] is available but we use "2.1.4-0"
```

## 1.1.6 (1.8.2016)

* `:content-type` of user-defined formats are pushed into Swagger `:produces` and `:consumes`, thanks to [Waldemar](https://github.com/Velrok).

```clj
(def custom-json-format
  (ring.middleware.format-response/make-encoder cheshire.core/generate-string "application/vnd.vendor.v1+json"))

(api
  {:format {:formats [custom-json-format :json :edn]}}
  ...)
```

## 1.1.5 (27.7.2016)

* New api-options `[:api :disable-api-middleware?]` to disable the api-middleware completely. With this set, `api` only produces the (reverse) route-tree + set's swagger stuff and sets schema coercions for the api.
  * Thanks to [Alan Malloy](https://github.com/amalloy) for contributing!

```clj
(api
  {:api {:disable-api-middleware? true}
   ;; Still available
   :swagger {:ui "/api-docs"
             :spec "/swagger.json"
             :data {:info {:title "api"}}}}
  ...)
```

* `:data` in `swagger-routes` can be overridden even if run outside of `api`:

```clj
(def app
  (routes
    (swagger-routes
      {:ui "/api-docs"
       :spec "/swagger.json"
       :data {:info {:title "Kikka"}
              :paths {"/ping" {:get {:summary "ping get"}}}}})
    (GET "/ping" [] "pong"))))
```

* unsetting `:format` option in `api-middleware` causes all format-middlewares not to mount
* unsetting `:exceptions` option in `api-middleware` causes the exception handling to be disabled
* unsetting `:coercion` translates to same as setting it to `(constantly nil)`

```clj
(api
  {:exceptions nil ;; disable exception handling
   :format nil     ;; disable ring-middleware-format
   :coercion nil}  ;; disable all schema-coercion
  ;; this will be really thrown
  (GET "/throw" []
    (throw (new RuntimeException))))
```

* updated dependencies:

```clj
[prismatic/schema "1.1.3"] is available but we use "1.1.2"
[frankiesardo/linked "1.2.7"] is available but we use "1.2.6"
```

## 1.1.4 (9.7.2016)

* fix reflection warning with logging, thanks to [Matt K](https://github.com/mtkp).
* Empty contexts (`/`) don't accumulate to the path, see https://github.com/weavejester/compojure/issues/125

* **NOTE**: update of `ring-http-response` had a [breaking change](https://github.com/metosin/ring-http-response/blob/master/CHANGELOG.md#080-2862016):
  - first argument for `created` is `url`, not `body`. Has 2-arity version which takes both `url` & `body` in align to the [spec](http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html) & [ring](https://github.com/ring-clojure/ring/blob/master/ring-core/src/ring/util/response.clj#L37)
   - fixes [#12](https://github.com/metosin/ring-http-response/issues/12).


* updated dependencies:

```clj
[compojure "1.5.1"] is available but we use "1.5.0"
[metosin/ring-http-response "0.8.0"] is available but we use "0.7.0"
[cheshire "5.6.3"] is available but we use "5.6.1"
```

## 1.1.3 (14.6.2016)

* updated dependencies:

```clj
[prismatic/schema "1.1.2"] is available but we use "1.1.1"
[metosin/ring-http-response "0.7.0"] is available but we use "0.6.5"
[metosin/ring-swagger "0.22.9"] is available but we use "0.22.8"
[reloaded.repl "0.2.2"] is available but we use "0.2.1"
[peridot "0.4.4"] is available but we use "0.4.3"
[reloaded.repl "0.2.2"] is available but we use "0.2.1"
```

## 1.1.2 (21.5.2016)

* Response headers are mapped correctly, fixes [#232](https://github.com/metosin/compojure-api/issues/232)

* updated dependencies:

```clj
[metosin/ring-swagger "0.22.8"] is available but we use "0.22.7"
```

## 1.1.1 (18.5.2016)

* Allow usage of run-time parameters with `:swagger`.

```clj
(let [runtime-data {:x-name :boolean
                    :operationId "echoBoolean"
                    :description "Ehcoes a boolean"
                    :parameters {:query {:q s/Bool}}}]
  (api
    (GET "/route" []
      :swagger runtime-data
      (ok {:it "works"}))))
```

* Copy & coerce compojure `:route-params` into `:path-params` with resources
  * Fixes [#231](https://github.com/metosin/compojure-api/issues/231).

```clj
(resource
  {:parameters {:path-params {:id s/Int}}
   :responses {200 {:schema s/Int}}
   :handler (fnk [[:path-params id]]
              (ok (inc id)))})
```

* updated dependencies:

```clj
[prismatic/schema "1.1.1"] is available but we use "1.1.0"
```

## 1.1.0 (25.4.2016)

* **BREAKING**: Move `compojure.api.swgger/validate` to `compojure.api.validator/validate`.
* **BREAKING**: If a `resource` doesn't define a handler for a given `request-method` or for top-level, nil is returned (instead of throwing exeption)
* **BREAKING** Resource-routing is done by `context`. Trying to return a `compojure.api.routing/Route` from an endpoint like `ANY` will throw descriptive (runtime-)exception.

```clj
(context "/hello" []
  (resource
    {:description "hello-resource"
     :responses {200 {:schema {:message s/Str}}}
     :post {:summary "post-hello"
            :parameters {:body-params {:name s/Str}}
            :handler (fnk [[:body-params name]]
                       (ok {:message (format "hello, %s!" name)}))}
     :get {:summary "get-hello"
           :parameters {:query-params {:name s/Str}}
           :handler (fnk [[:query-params name]]
                      (ok {:message (format "hello, %s!" name)}))}}))
```

* api-level swagger-options default to `{:ui nil, :spec nil}`. Setting up just the spec or ui, doesn't automatically setup the other (like previously)
* Strip nils from `:middleware`, fixes [#228](https://github.com/metosin/compojure-api/issues/228)
* `describe` works with anonymous body-schemas (via ring-swagger `0.22.7`), Fixes [#168](https://github.com/metosin/compojure-api/issues/168)
* Support compojure-api apps in [Google App Engine](https://cloud.google.com/appengine) by allowing [scjsv](https://github.com/metosin/scjsv) to be excluded (uses [json-schema-validator](https://github.com/fge/json-schema-validator), which uses rogue threads):

```clj
[metosin/compojure-api "1.1.0" :exclusions [[metosin/scjsv]]]
```

* updated dependencies:

```clj
[metosin/ring-swagger "0.22.7"] is available but we use "0.22.6"
[prismatic/plumbing "0.5.3"] is available but we use "0.5.2"
[cheshire "5.6.1"] is available but we use "5.5.0"
```

## 1.0.2 (27.3.2016)

* Parameter order is unreversed for fnk-style destructurings for small number of paramerers, fixes [#224](https://github.com/metosin/compojure-api/issues/224)
* Moved internal coercion helpers from `compojure.api.meta` to `compojure.api.coerce`.
* New `compojure.api.resource/resource` (also in `compojure.api.sweet`) for building resource-oriented services
  * Yields (presumably) better support for [Liberator](http://clojure-liberator.github.io/liberator/), fixes [#185](https://github.com/metosin/compojure-api/issues/185)

```clj
(defn resource
  "Creates a nested compojure-api Route from enchanced ring-swagger operations map and options.
  By default, applies both request- and response-coercion based on those definitions.

  Options:

  - **:coercion**       A function from request->type->coercion-matcher, used
                        in resource coercion for :body, :string and :response.
                        Setting value to `(constantly nil)` disables both request- &
                        response coercion. See tests and wiki for details.

  Enchancements to ring-swagger operations map:

  1) :parameters use ring request keys (query-params, path-params, ...) instead of
  swagger-params (query, path, ...). This keeps things simple as ring keys are used in
  the handler when destructuring the request.

  2) at resource root, one can add any ring-swagger operation definitions, which will be
  available for all operations, using the following rules:

    2.1) :parameters are deep-merged into operation :parameters
    2.2) :responses are merged into operation :responses (operation can fully override them)
    2.3) all others (:produces, :consumes, :summary,...) are deep-merged by compojure-api

  3) special key `:handler` either under operations or at top-level. Value should be a
  ring-handler function, responsible for the actual request processing. Handler lookup
  order is the following: operations-level, top-level, exception.

  4) request-coercion is applied once, using deep-merged parameters for a given
  operation or resource-level if only resource-level handler is defined.

  5) response-coercion is applied once, using merged responses for a given
  operation or resource-level if only resource-level handler is defined.

  Note: Swagger operations are generated only from declared operations (:get, :post, ..),
  despite the top-level handler could process more operations.

  Example:

  (resource
    {:parameters {:query-params {:x Long}}
     :responses {500 {:schema {:reason s/Str}}}
     :get {:parameters {:query-params {:y Long}}
           :responses {200 {:schema {:total Long}}}
           :handler (fn [request]
                      (ok {:total (+ (-> request :query-params :x)
                                     (-> request :query-params :y))}))}
     :post {}
     :handler (constantly
                (internal-server-error {:reason \"not implemented\"}))})"
  ([info]
   (resource info {}))
  ([info options]
   (let [info (merge-parameters-and-responses info)
         root-info (swaggerize (root-info info))
         childs (create-childs info)
         handler (create-handler info options)]
     (routes/create nil nil root-info childs handler))))
```

* updated dependencies:

```clj
[compojure "1.5.0"] is available but we use "1.4.0"
[prismatic/schema "1.1.0"] is available but we use "1.0.5"
[metosin/ring-swagger "0.22.6"] is available but we use "0.22.4"
```

## 1.0.1 (28.2.2016)

* For response coercion, the original response is available in `ex-data` under `:response`.
This can be used in logging, "what did the route try to return". Thanks to [Tim Gilbert](https://github.com/timgilbert).
* Response coercion uses the `:default` code if available and response code doesn't match

```clj
(GET "/" []
  :responses {200 {:schema {:ping s/Str}}
              :default {:schema {:error s/int}}}
  ...)
```

## 1.0.0 (17.2.2016)

**[compare](https://github.com/metosin/compojure-api/compare/0.24.5...1.0.0)**

**[compare to RC2](https://github.com/metosin/compojure-api/compare/1.0.0-RC2...1.0.0)**

* updated dependencies:

```clj
[prismatic/schema "1.0.5"] is available but we use "1.0.4"
```

### 1.0.0-RC2 (11.2.2016)

**[compare to RC1](https://github.com/metosin/compojure-api/compare/1.0.0-RC1...1.0.0-RC2)**

* Swagger-routes mounted via api-options are mounted before other routes, fixes [#218](https://github.com/metosin/compojure-api/issues/218)
* Routes are now resolved also from from Vars, fixes [#219](https://github.com/metosin/compojure-api/issues/219)
* Better handling of `:basePath` with `swagger-routes`, thanks to [Hoxu](https://github.com/hoxu).
* Updated dependencies:

```clj
[metosin/ring-swagger "0.22.4"] is available but we use "0.22.3"
```

### 1.0.0-RC1 (2.2.2016)

**[compare](https://github.com/metosin/compojure-api/compare/0.24.5...1.0.0-RC1)**

* Move from compile-time to runtime route resolution.
  * Most of the internal macro magic has been vaporized
  * Uses internally (invokable) Records & Protocols, allowing easier integration to 3rd party libs like [Liberator](http://clojure-liberator.github.io/liberator/)
     * even for large apps (100+ routes), route compilation takes now millis, instead of seconds
  * sub-routes can be created with normal functions (or values), making it easier to:
     * pass in app-level dependencies from libs like [Component](https://github.com/stuartsierra/component)
     * reuse shared request-handling time parameters like path-parameters and authorization info

```clj
(defn more-routes [db version]
  (routes
    (GET "/version" []
      (ok {:version version}))
    (POST "/thingie" []
      (ok (thingie/create db)))))

(defn app [db]
  (api
    (context "/api/:version" []
      :path-params [version :- s/Str]
      (more-routes db version)
      (GET "/kikka" []
        (ok "kukka")))))
```

### Breaking changes

* **BREAKING** Vanilla Compojure routes will not produce any swagger-docs (as they do not satisfy the
`Routing` protocol. They can still be used for handling request, just without docs.
  * a new api-level option `[:api :invalid-routes-fn]` to declare how to handle routes not satisfying
  the `Routing` protocol. Default implementation logs invalid routes as WARNINGs.

* **BREAKING** compojure.core imports are removed from `compojure.api.sweet`:
  * `let-request`, `routing`, `wrap-routes`

* **BREAKING** Asterix (`*`) is removed from route macro & function names, as there is no reason to mix compojure-api & compojure route macros.
  * `GET*` => `GET`
  * `ANY*` => `ANY`
  * `HEAD*` => `HEAD`
  * `PATCH*` => `PATCH`
  * `DELETE*` => `DELETE`
  * `OPTIONS*` => `OPTIONS`
  * `POST*` => `PUT`
  * `context*` => `context`
  * `defroutes*` => `defroutes`

* **BREAKING** `swagger-docs` and `swagger-ui` are no longer in `compojure.api.sweet`
  * Syntax was hairy and when configuring the spec-url it needed to be set to both in order to work
  * In future, there are multiple ways of setting the swagger stuff:
    * via api-options `:swagger` (has no defaults)
    * via `swagger-routes` function, mounting both the `swagger-ui` and `swagger-docs` and wiring them together
      * by default, mounts the swagger-ui to `/` and the swagger-spec to `/swagger.json`
    * via the old `swagger-ui` & `swagger-docs` (need to be separately imported from `compojure.api.swagger`).
    * see https://github.com/metosin/compojure-api/wiki/Swagger-integration for details

```clj
(defapi app
  (swagger-routes)
  (GET "/ping" []
    (ok {:message "pong"})))

(defapi app
  {:swagger {:ui "/", :spec "/swagger.json"}}
  (GET "/ping" []
    (ok {:message "pong"})))
```

* **BREAKING**: api-level coercion option is now a function of `request => type => matcher` as it is documented.
Previously required a `type => matcher` map. Options are checked against `type => matcher` coercion input, and a
descriptive error is thrown when api is created with the old options format.

* **BREAKING**: Renamed `middlewares` to `middleware` and `:middlewares` key (restructuring) to `:middleware`
  * will break at macro-expansion time with helpful exception

* **BREAKING**: Middleware must be defined as data: both middleware macro and :middleware restructuring
take a vector of middleware containing either
  * a) fully configured middleware (function), or
  * b) a middleware templates in form of `[function args]`
  * You can also use anonymous or lambda functions to create middleware with correct parameters,
  these are all identical:
      * `[[wrap-foo {:opts :bar}]]`
      * `[#(wrap-foo % {:opts :bar})]`
      * `[(fn [handler] (wrap-foo handler {:opts :bar}))]`
  * Similar to [duct](https://github.com/weavejester/duct/wiki/Components#handlers)

* **BREAKING**: (Custom restructuring handlers only) `:parameters` key used by `restructure-param`
has been renamed to `:swagger`.
  * will break at macro-expansion time with helpful exception

* **BREAKING** `public-resource-routes` & `public-resources` are removed from `compojure.api.middleware`.

* **BREAKING**: `compojure.api.legacy` namespace has been removed.

### Migration guide

https://github.com/metosin/compojure-api/wiki/Migration-Guide-to-1.0.0

### Other stuff

* Additional route functions/macros in `compojure.api.core`:
  * `routes` & `letroutes`, just like in the Compojure, but supporting `Routing`
  * `undocumented` - works just like `routes` but without any route definitions. Can be used to wrap legacy routes which setting the api option to fail on missing docs.

* top-level `api` is now just function, not a macro. It takes an optional options maps and a top-level route function.

* Coercer cache is now at api-level with 10000 entries.

* Code generated from restructured route macros is much cleaner now

* Coercion is on by default for standalone (apiless) endpoints.

```clj
(fact "coercion is on for apiless routes"
  (let [route (GET "/x" []
                :query-params [x :- Long]
                (ok))]
    (route {:request-method :get :uri "/x" :query-params {}}) => throws))
```

* Removed deps:

```clojure
[backtick "0.3.3"]
```

## 0.24.5 (17.1.2016)

**[compare](https://github.com/metosin/compojure-api/compare/0.24.4...0.24.5)**

* Fixed path parameter handling in cases where path parameter is followed by an extension
([#196](https://github.com/metosin/compojure-api/issues/196), [metosin/ring-swagger#82](https://github.com/metosin/ring-swagger/issues/82))
* [Updated ring-swagger](https://github.com/metosin/ring-swagger/blob/master/CHANGELOG.md#0223-1712016)
* Added `compojure.api.exception/with-logging` helper to add logging to exception handlers.
  * Check extended wiki guide on [exception handling](https://github.com/metosin/compojure-api/wiki/Exception-handling#logging)

* Updated deps:

```clojure
[metosin/ring-swagger "0.22.3"] is available
```

## 0.24.4 (13.1.2016)

**[compare](https://github.com/metosin/compojure-api/compare/0.24.3...0.24.4)**

- [Updated ring-swagger](https://github.com/metosin/ring-swagger/blob/master/CHANGELOG.md#0222-1312016)

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
