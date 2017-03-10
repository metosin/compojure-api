(ns compojure.api.integration-test
  (:require [compojure.api.sweet :refer :all]
            [compojure.api.test-utils :refer :all]
            [compojure.api.exception :as ex]
            [compojure.api.swagger :as swagger]
            [midje.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ring.util.http-predicates :as http]
            [schema.core :as s]
            [ring.swagger.core :as rsc]
            [ring.util.http-status :as status]
            [compojure.api.middleware :as mw]
            [ring.swagger.middleware :as rsm]
            [compojure.api.validator :as validator]
            [compojure.api.routes :as routes]
            [muuntaja.core :as muuntaja]
            [muuntaja.core :as formats]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [muuntaja.core :as muuntaja]
            [muuntaja.core :as m])
  (:import (java.sql SQLException SQLWarning)
           (java.io File)))

;;
;; Data
;;

(s/defschema User {:id s/Int
                   :name s/Str})

(def pertti {:id 1 :name "Pertti"})

(def invalid-user {:id 1 :name "Jorma" :age 50})

; Headers contain extra keys, so make the schema open
(s/defschema UserHeaders
  (assoc User
    s/Keyword s/Any))

;;
;; Middleware setup
;;

(def mw* "mw")

(defn middleware*
  "This middleware appends given value or 1 to a header in request and response."
  ([handler] (middleware* handler 1))
  ([handler value]
   (fn [request]
     (let [append #(str % value)
           request (update-in request [:headers mw*] append)
           response (handler request)]
       (update-in response [:headers mw*] append)))))

(defn constant-middleware
  "This middleware rewrites all responses with a constant response."
  [_ res]
  (constantly res))

(defn reply-mw*
  "Handler which replies with response where a header contains copy
   of the headers value from request and 7"
  [request]
  (-> (ok "true")
      (header mw* (str (get-in request [:headers mw*]) "/"))))

(defn middleware-x
  "If request has query-param x, presume it's a integer and multiply it by two
   before passing request to next handler."
  [handler]
  (fn [req]
    (handler (update-in req [:query-params "x"] #(* (Integer. %) 2)))))

(defn custom-validation-error-handler [ex data request]
  (let [error-body {:custom-error (:uri request)}]
    (case (:type data)
      ::ex/response-validation (not-implemented error-body)
      (bad-request error-body))))

(defn custom-exception-handler [key]
  (fn [^Exception ex data request]
    (ok {key (str ex)})))

(defn custom-error-handler [ex data request]
  (ok {:custom-error (:data data)}))

;;
;; Facts
;;

(facts "core routes"

  (fact "keyword options"
    (let [route (GET "/ping" []
                  :return String
                  (ok "kikka"))]
      (route {:request-method :get :uri "/ping"}) => (contains {:body "kikka"})))

  (fact "map options"
    (let [route (GET "/ping" []
                  {:return String}
                  (ok "kikka"))]
      (route {:request-method :get :uri "/ping"}) => (contains {:body "kikka"})))

  (fact "map return"
    (let [route (GET "/ping" []
                  {:body "kikka"})]
      (route {:request-method :get :uri "/ping"}) => (contains {:body "kikka"}))))

(facts "middleware ordering"
  (let [app (api
              {:middleware [[middleware* 0]]}
              (middleware [middleware* [middleware* 2]]
                (context "/middlewares" []
                  :middleware [(fn [handler] (middleware* handler 3)) [middleware* 4]]
                  (GET "/simple" req (reply-mw* req))
                  (middleware [#(middleware* % 5) [middleware* 6]]
                    (GET "/nested" req (reply-mw* req))
                    (GET "/nested-declared" req
                      :middleware [(fn [handler] (middleware* handler 7)) [middleware* 8]]
                      (reply-mw* req))))))]

    (fact "are applied left-to-right"
      (let [[status _ headers] (get* app "/middlewares/simple" {})]
        status => 200
        (get headers mw*) => "01234/43210"))

    (fact "are applied left-to-right closest one first"
      (let [[status _ headers] (get* app "/middlewares/nested" {})]
        status => 200
        (get headers mw*) => "0123456/6543210"))

    (fact "are applied left-to-right for both nested & declared closest one first"
      (let [[status _ headers] (get* app "/middlewares/nested-declared" {})]
        status => 200
        (get headers mw*) => "012345678/876543210"))))

(facts "middleware - multiple routes"
  (let [app (api
              (GET "/first" []
                (ok {:value "first"}))
              (GET "/second" []
                :middleware [[constant-middleware (ok {:value "foo"})]]
                (ok {:value "second"}))
              (GET "/third" []
                (ok {:value "third"})))]
    (fact "first returns first"
      (let [[status body] (get* app "/first" {})]
        status => 200
        body => {:value "first"}))
    (fact "second returns foo"
      (let [[status body] (get* app "/second" {})]
        status => 200
        body => {:value "foo"}))
    (fact "third returns third"
      (let [[status body] (get* app "/third" {})]
        status => 200
        body => {:value "third"}))))

(facts "middleware - editing request"
  (let [app (api
              (GET "/first" []
                :query-params [x :- Long]
                :middleware [middleware-x]
                (ok {:value x})))]
    (fact "middleware edits the parameter before route body"
      (let [[status body] (get* app "/first?x=5" {})]
        status => 200
        body => {:value 10}))))

(fact ":body, :query, :headers and :return"
  (let [app (api
              (context "/models" []
                (GET "/pertti" []
                  :return User
                  (ok pertti))
                (GET "/user" []
                  :return User
                  :query [user User]
                  (ok user))
                (GET "/invalid-user" []
                  :return User
                  (ok invalid-user))
                (GET "/not-validated" []
                  (ok invalid-user))
                (POST "/user" []
                  :return User
                  :body [user User]
                  (ok user))
                (POST "/user_list" []
                  :return [User]
                  :body [users [User]]
                  (ok users))
                (POST "/user_set" []
                  :return #{User}
                  :body [users #{User}]
                  (ok users))
                (POST "/user_headers" []
                  :return User
                  :headers [user UserHeaders]
                  (ok (select-keys user [:id :name])))
                (POST "/user_legacy" {user :body-params}
                  :return User
                  (ok user))))]

    (fact "GET"
      (let [[status body] (get* app "/models/pertti")]
        status => 200
        body => pertti))

    (fact "GET with smart destructuring"
      (let [[status body] (get* app "/models/user" pertti)]
        status => 200
        body => pertti))

    (fact "POST with smart destructuring"
      (let [[status body] (post* app "/models/user" (json pertti))]
        status => 200
        body => pertti))

    (fact "POST with smart destructuring - lists"
      (let [[status body] (post* app "/models/user_list" (json [pertti]))]
        status => 200
        body => [pertti]))

    (fact "POST with smart destructuring - sets"
      (let [[status body] (post* app "/models/user_set" (json #{pertti}))]
        status => 200
        body => [pertti]))

    (fact "POST with compojure destructuring"
      (let [[status body] (post* app "/models/user_legacy" (json pertti))]
        status => 200
        body => pertti))

    (fact "POST with smart destructuring - headers"
      (let [[status body] (headers-post* app "/models/user_headers" pertti)]
        status => 200
        body => pertti))

    (fact "Validation of returned data"
      (let [[status] (get* app "/models/invalid-user")]
        status => 500))

    (fact "Routes without a :return parameter aren't validated"
      (let [[status body] (get* app "/models/not-validated")]
        status => 200
        body => invalid-user))

    (fact "Invalid json in body causes 400 with error message in json"
      (let [[status body] (post* app "/models/user" "{INVALID}")]
        status => 400
        body => (contains
                  {:type "compojure.api.exception/request-parsing"
                   :message (contains "Malformed application/json")
                   :original (contains "Unexpected character")})))))

(fact ":responses"
  (fact "normal cases"
    (let [app (api
                (swagger-routes)
                (GET "/lotto/:x" []
                  :path-params [x :- Long]
                  :responses {403 {:schema [String]}
                              440 {:schema [String]}}
                  :return [Long]
                  (case x
                    1 (ok [1])
                    2 (ok ["two"])
                    3 (forbidden ["error"])
                    4 (forbidden [1])
                    (not-found {:message "not-found"}))))]

      (fact "return case"
        (let [[status body] (get* app "/lotto/1")]
          status => 200
          body => [1]))

      (fact "return case, non-matching model"
        (let [[status body] (get* app "/lotto/2")]
          status => 500
          body => (contains {:errors vector?})))

      (fact "error case"
        (let [[status body] (get* app "/lotto/3")]
          status => 403
          body => ["error"]))

      (fact "error case, non-matching model"
        (let [[status body] (get* app "/lotto/4")]
          status => 500
          body => (contains {:errors vector?})))

      (fact "returning non-predefined http-status code works"
        (let [[status body] (get* app "/lotto/5")]
          body => {:message "not-found"}
          status => 404))

      (fact "swagger-docs for multiple returns"
        (-> app get-spec :paths vals first :get :responses keys set))))

  (fact ":responses 200 and :return"
    (let [app (api
                (GET "/lotto/:x" []
                  :path-params [x :- Long]
                  :return {:return String}
                  :responses {200 {:schema {:value String}}}
                  (case x
                    1 (ok {:return "ok"})
                    2 (ok {:value "ok"}))))]

      (fact "return case"
        (let [[status body] (get* app "/lotto/1")]
          status => 500
          body => (contains {:errors {:return "disallowed-key"
                                      :value "missing-required-key"}})))

      (fact "return case"
        (let [[status body] (get* app "/lotto/2")]
          status => 200
          body => {:value "ok"}))))

  (fact ":responses 200 and :return - other way around"
    (let [app (api
                (GET "/lotto/:x" []
                  :path-params [x :- Long]
                  :responses {200 {:schema {:value String}}}
                  :return {:return String}
                  (case x
                    1 (ok {:return "ok"})
                    2 (ok {:value "ok"}))))]

      (fact "return case"
        (let [[status body] (get* app "/lotto/1")]
          status => 200
          body => {:return "ok"}))

      (fact "return case"
        (let [[status body] (get* app "/lotto/2")]
          status => 500
          body => (contains {:errors {:return "missing-required-key"
                                      :value "disallowed-key"}}))))))

(fact ":query-params, :path-params, :header-params , :body-params and :form-params"
  (let [app (api
              (context "/smart" []
                (GET "/plus" []
                  :query-params [x :- Long y :- Long]
                  (ok {:total (+ x y)}))
                (GET "/multiply/:x/:y" []
                  :path-params [x :- Long y :- Long]
                  (ok {:total (* x y)}))
                (GET "/power" []
                  :header-params [x :- Long y :- Long]
                  (ok {:total (long (Math/pow x y))}))
                (POST "/minus" []
                  :body-params [x :- Long {y :- Long 1}]
                  (ok {:total (- x y)}))
                (POST "/divide" []
                  :form-params [x :- Long y :- Long]
                  (ok {:total (/ x y)}))))]

    (fact "query-parameters"
      (let [[status body] (get* app "/smart/plus" {:x 2 :y 3})]
        status => 200
        body => {:total 5}))

    (fact "path-parameters"
      (let [[status body] (get* app "/smart/multiply/2/3")]
        status => 200
        body => {:total 6}))

    (fact "header-parameters"
      (let [[status body] (get* app "/smart/power" {} {:x 2 :y 3})]
        status => 200
        body => {:total 8}))

    (fact "form-parameters"
      (let [[status body] (form-post* app "/smart/divide" {:x 6 :y 3})]
        status => 200
        body => {:total 2}))

    (fact "body-parameters"
      (let [[status body] (post* app "/smart/minus" (json {:x 2 :y 3}))]
        status => 200
        body => {:total -1}))

    (fact "default parameters"
      (let [[status body] (post* app "/smart/minus" (json {:x 2}))]
        status => 200
        body => {:total 1}))))

(fact "primitive support"
  (let [app (api
              {:swagger {:spec "/swagger.json"}}
              (context "/primitives" []
                (GET "/return-long" []
                  :return Long
                  (ok 1))
                (GET "/long" []
                  (ok 1))
                (GET "/return-string" []
                  :return String
                  (ok "kikka"))
                (POST "/arrays" []
                  :return [Long]
                  :body [longs [Long]]
                  (ok longs))))]

    (fact "when :return is set, longs can be returned"
      (let [[status body] (raw-get* app "/primitives/return-long")]
        status => 200
        body => "1"))

    (fact "when :return is not set, longs won't be encoded"
      (let [[status body] (raw-get* app "/primitives/long")]
        status => 200
        body => number?))

    (fact "when :return is set, raw strings can be returned"
      (let [[status body] (raw-get* app "/primitives/return-string")]
        status => 200
        body => "\"kikka\""))

    (fact "primitive arrays work"
      (let [[status body] (raw-post* app "/primitives/arrays" (json/generate-string [1 2 3]))]
        status => 200
        body => "[1,2,3]"))

    (fact "swagger-spec is valid"
      (validator/validate app))

    (fact "primitive array swagger-docs are good"

      (-> app get-spec :paths (get "/primitives/arrays") :post :parameters)
      => [{:description ""
           :in "body"
           :name ""
           :required true
           :schema {:items {:format "int64"
                            :type "integer"}
                    :type "array"}}]

      (-> app get-spec :paths (get "/primitives/arrays") :post :responses :200 :schema)
      => {:items {:format "int64",
                  :type "integer"},
          :type "array"})))

(fact "compojure destructuring support"
  (let [app (api
              (context "/destructuring" []
                (GET "/regular" {{:keys [a]} :params}
                  (ok {:a a
                       :b (-> +compojure-api-request+ :params :b)}))
                (GET "/regular2" {:as req}
                  (ok {:a (-> req :params :a)
                       :b (-> +compojure-api-request+ :params :b)}))
                (GET "/vector" [a]
                  (ok {:a a
                       :b (-> +compojure-api-request+ :params :b)}))
                (GET "/vector2" [:as req]
                  (ok {:a (-> req :params :a)
                       :b (-> +compojure-api-request+ :params :b)}))
                (GET "/symbol" req
                  (ok {:a (-> req :params :a)
                       :b (-> +compojure-api-request+ :params :b)}))
                (GET "/integrated" [a] :query-params [b]
                  (ok {:a a
                       :b b}))))]

    (doseq [uri ["regular" "regular2" "vector" "vector2" "symbol" "integrated"]]
      (fact {:midje/description uri}
        (let [[status body] (get* app (str "/destructuring/" uri) {:a "a" :b "b"})]
          status => 200
          body => {:a "a" :b "b"})))))

(fact "counting execution times, issue #19"
  (let [execution-times (atom 0)
        app (api
              (GET "/user" []
                :return User
                :query [user User]
                (swap! execution-times inc)
                (ok user)))]

    (fact "body is executed one"
      @execution-times => 0
      (let [[status body] (get* app "/user" pertti)]
        status => 200
        body => pertti)
      @execution-times => 1)))

(fact "swagger-docs"
  (let [app (api
              {:formats (muuntaja/select-formats
                          muuntaja/default-options
                          ["application/json" "application/edn"])}
              (swagger-routes)
              (GET "/user" []
                (continue)))]

    (fact "api-listing shows produces & consumes for known types"
      (get-spec app) => {:swagger "2.0"
                         :info {:title "Swagger API"
                                :version "0.0.1"}
                         :basePath "/"
                         :consumes ["application/json" "application/edn"]
                         :produces ["application/json" "application/edn"]
                         :definitions {}
                         :paths {"/user" {:get {:responses {:default {:description ""}}}}}}))

  (fact "swagger-routes"

    (fact "with defaults"
      (let [app (api (swagger-routes))]

        (fact "api-docs are mounted to /"
          (let [[status body] (raw-get* app "/")]
            status => 200
            body => #"<title>Swagger UI</title>"))

        (fact "spec is mounted to /swagger.json"
          (let [[status body] (get* app "/swagger.json")]
            status => 200
            body => (contains {:swagger "2.0"})))))

    (fact "with partial overridden values"
      (let [app (api (swagger-routes {:ui "/api-docs"
                                      :data {:info {:title "Kikka"}
                                             :paths {"/ping" {:get {}}}}}))]

        (fact "api-docs are mounted"
          (let [[status body] (raw-get* app "/api-docs")]
            status => 200
            body => #"<title>Swagger UI</title>"))

        (fact "spec is mounted to /swagger.json"
          (let [[status body] (get* app "/swagger.json")]
            status => 200
            body => (contains
                      {:swagger "2.0"
                       :info (contains
                               {:title "Kikka"})
                       :paths (contains
                                {(keyword "/ping") anything})}))))))

  (fact "swagger via api-options"

    (fact "with defaults"
      (let [app (api)]

        (fact "api-docs are not mounted"
          (let [[status body] (raw-get* app "/")]
            status => nil))

        (fact "spec is not mounted"
          (let [[status body] (get* app "/swagger.json")]
            status => nil))))

    (fact "with spec"
      (let [app (api {:swagger {:spec "/swagger.json"}})]

        (fact "api-docs are not mounted"
          (let [[status body] (raw-get* app "/")]
            status => nil))

        (fact "spec is mounted to /swagger.json"
          (let [[status body] (get* app "/swagger.json")]
            status => 200
            body => (contains {:swagger "2.0"}))))))

  (fact "with ui"
    (let [app (api {:swagger {:ui "/api-docs"}})]

      (fact "api-docs are mounted"
        (let [[status body] (raw-get* app "/api-docs")]
          status => 200
          body => #"<title>Swagger UI</title>"))

      (fact "spec is not mounted"
        (let [[status body] (get* app "/swagger.json")]
          status => nil))))

  (fact "with ui and spec"
    (let [app (api {:swagger {:spec "/swagger.json", :ui "/api-docs"}})]

      (fact "api-docs are mounted"
        (let [[status body] (raw-get* app "/api-docs")]
          status => 200
          body => #"<title>Swagger UI</title>"))

      (fact "spec is mounted to /swagger.json"
        (let [[status body] (get* app "/swagger.json")]
          status => 200
          body => (contains {:swagger "2.0"}))))))

(facts "swagger-docs with anonymous Return and Body models"
  (let [app (api
              (swagger-routes)
              (POST "/echo" []
                :return (s/either {:a String})
                :body [_ (s/maybe {:a String})]
                identity))]

    (fact "api-docs"
      (let [spec (get-spec app)]

        (let [operation (some-> spec :paths vals first :post)
              body-ref (some-> operation :parameters first :schema :$ref)
              return-ref (get-in operation [:responses :200 :schema :$ref])]

          (fact "generated body-param is found in Definitions"
            (find-definition spec body-ref) => truthy)

          (fact "generated return-param is found in Definitions"
            return-ref => truthy
            (find-definition spec body-ref) => truthy))))))

(def Boundary
  {:type (s/enum "MultiPolygon" "Polygon" "MultiPoint" "Point")
   :coordinates [s/Any]})

(def ReturnValue
  {:boundary (s/maybe Boundary)})

(facts "https://github.com/metosin/compojure-api/issues/53"
  (let [app (api
              (swagger-routes)
              (POST "/" []
                :return ReturnValue
                :body [_ Boundary]
                identity))]

    (fact "api-docs"
      (let [spec (get-spec app)]

        (let [operation (some-> spec :paths vals first :post)
              body-ref (some-> operation :parameters first :schema :$ref)
              return-ref (get-in operation [:responses :200 :schema :$ref])]

          (fact "generated body-param is found in Definitions"
            (find-definition spec body-ref) => truthy)

          (fact "generated return-param is found in Definitions"
            return-ref => truthy
            (find-definition spec body-ref) => truthy))))))

(s/defschema Urho {:kaleva {:kekkonen {s/Keyword s/Any}}})
(s/defschema Olipa {:kerran {:avaruus {s/Keyword s/Any}}})

; https://github.com/metosin/compojure-api/issues/94
(facts "preserves deeply nested schema names"
  (let [app (api
              (swagger-routes)
              (POST "/" []
                :return Urho
                :body [_ Olipa]
                identity))]

    (fact "api-docs"
      (let [spec (get-spec app)]

        (fact "nested models are discovered correctly"
          (-> spec :definitions keys set)

          => #{:Urho :UrhoKaleva :UrhoKalevaKekkonen
               :Olipa :OlipaKerran :OlipaKerranAvaruus})))))

(fact "swagger-docs works with the :middleware"
  (let [app (api
              (swagger-routes)
              (GET "/middleware" []
                :query-params [x :- String]
                :middleware [[constant-middleware (ok 1)]]
                (ok 2)))]

    (fact "api-docs"
      (-> app get-spec :paths vals first)
      => {:get {:parameters [{:description ""
                              :in "query"
                              :name "x"
                              :required true
                              :type "string"}]
                :responses {:default {:description ""}}}})))

(fact "sub-context paths"
  (let [response {:ping "pong"}
        ok (ok response)
        ok? (fn [[status body]]
              (and (= status 200)
                   (= body response)))
        not-ok? (comp not ok?)
        app (api
              (swagger-routes {:ui nil})
              (GET "/" [] ok)
              (GET "/a" [] ok)
              (context "/b" []
                (context "/b1" []
                  (GET "/" [] ok))
                (context "/" []
                  (GET "/" [] ok)
                  (GET "/b2" [] ok))))]

    (fact "valid routes"
      (get* app "/") => ok?
      (get* app "/a") => ok?
      (get* app "/b/b1") => ok?
      (get* app "/b") => ok?
      (get* app "/b/b2") => ok?)

    (fact "undocumented compojure easter eggs"
      (get* app "/b/b1/") => ok?
      (get* app "/b/") => ok?
      (fact "this is fixed in compojure 1.5.1"
        (get* app "/b//") =not=> ok?))

    (fact "swagger-docs have trailing slashes removed"
      (->> app get-spec :paths keys)
      => ["/" "/a" "/b/b1" "/b" "/b/b2"])))

(fact "formats supported by ring-middleware-format"
  (let [app (api
              (POST "/echo" []
                :body-params [foo :- String]
                (ok {:foo foo})))]

    (tabular
      (facts
        (fact {:midje/description (str ?content-type " to json")}
          (let [[status body]
                (raw-post* app "/echo" ?body ?content-type {:accept "application/json"})]
            status => 200
            body => "{\"foo\":\"bar\"}"))
        (fact {:midje/description (str "json to " ?content-type)}
          (let [[status body]
                (raw-post* app "/echo" "{\"foo\":\"bar\"}" "application/json" {:accept ?content-type})]
            status => 200
            body => ?body)))

      ?content-type ?body
      "application/json" "{\"foo\":\"bar\"}"
      "application/x-yaml" "{foo: bar}\n"
      "application/edn" "{:foo \"bar\"}"
      "application/transit+json" "[\"^ \",\"~:foo\",\"bar\"]")))

(fact "multiple routes in context"
  (let [app (api
              (context "/foo" []
                (GET "/bar" [] (ok ["bar"]))
                (GET "/baz" [] (ok ["baz"]))))]

    (fact "first route works"
      (let [[status body] (get* app "/foo/bar")]
        status => 200
        body => ["bar"]))
    (fact "second route works"
      (let [[status body] (get* app "/foo/baz")]
        status => 200
        body => ["baz"]))))

(require '[compojure.api.test-domain :refer [Pizza burger-routes]])

(fact "external deep schemas"
  (let [app (api
              (swagger-routes)
              burger-routes
              (POST "/pizza" []
                :return Pizza
                :body [body Pizza]
                (ok body)))]

    (fact "direct route with nested named schema works when called"
      (let [pizza {:toppings [{:name "cheese"}]}
            [status body] (post* app "/pizza" (json pizza))]
        status => 200
        body => pizza))

    (fact "defroute*'d route with nested named schema works when called"
      (let [burger {:ingredients [{:name "beef"}, {:name "egg"}]}
            [status body] (post* app "/burger" (json burger))]
        status => 200
        body => burger))

    (fact "generates correct swagger-spec"
      (-> app get-spec :definitions keys set) => #{:Topping :Pizza :Burger :Beef})))

(fact "multiple routes with same path & method in same file"
  (let [app (api
              (swagger-routes)
              (GET "/ping" []
                :summary "active-ping"
                (ok {:ping "active"}))
              (GET "/ping" []
                :summary "passive-ping"
                (ok {:ping "passive"})))]

    (fact "first route matches with Compojure"
      (let [[status body] (get* app "/ping" {})]
        status => 200
        body => {:ping "active"}))

    (fact "generates correct swagger-spec"
      (-> app get-spec :paths vals first :get :summary) => "active-ping")))

(fact "multiple routes with same path & method over context"
  (let [app (api
              (swagger-routes)
              (context "/api" []
                (context "/ipa" []
                  (GET "/ping" []
                    :summary "active-ping"
                    (ok {:ping "active"}))))
              (context "/api" []
                (context "/ipa" []
                  (GET "/ping" []
                    :summary "passive-ping"
                    (ok {:ping "passive"})))))]

    (fact "first route matches with Compojure"
      (let [[status body] (get* app "/api/ipa/ping" {})]
        status => 200
        body => {:ping "active"}))

    (fact "generates correct swagger-spec"
      (-> app get-spec :paths vals first :get :summary) => "active-ping")))

(fact "multiple routes with same overall path (with different path sniplets & method over context"
  (let [app (api
              (swagger-routes)
              (context "/api/ipa" []
                (GET "/ping" []
                  :summary "active-ping"
                  (ok {:ping "active"})))
              (context "/api" []
                (context "/ipa" []
                  (GET "/ping" []
                    :summary "passive-ping"
                    (ok {:ping "passive"})))))]

    (fact "first route matches with Compojure"
      (let [[status body] (get* app "/api/ipa/ping" {})]
        status => 200
        body => {:ping "active"}))

    (fact "generates correct swagger-spec"
      (-> app get-spec :paths vals first :get :summary) => "active-ping")))

; https://github.com/metosin/compojure-api/issues/98
; https://github.com/metosin/compojure-api/issues/134
(fact "basePath"
  (let [app (api (swagger-routes))]

    (fact "no context"
      (-> app get-spec :basePath) => "/")

    (fact "app-servers with given context"
      (against-background (rsc/context anything) => "/v2")
      (-> app get-spec :basePath) => "/v2"))

  (let [app (api (swagger-routes {:data {:basePath "/serve/from/here"}}))]
    (fact "override it"
      (-> app get-spec :basePath) => "/serve/from/here"))

  (let [app (api (swagger-routes {:data {:basePath "/"}}))]
    (fact "can set it to the default"
      (-> app get-spec :basePath) => "/")))

(fact "multiple different models with same name"

  (fact "schemas with same regexps are not equal"
    {:d #"\D"} =not=> {:d #"\D"})

  (fact "api-spec with 2 schemas with non-equal contents"
    (let [app (api
                (swagger-routes)
                (GET "/" []
                  :responses {200 {:schema (s/schema-with-name {:a {:d #"\D"}} "Kikka")}
                              201 {:schema (s/schema-with-name {:a {:d #"\D"}} "Kikka")}}
                  identity))]
      (fact "api spec doesn't fail (#102)"
        (get-spec app) => anything))))

(def over-the-hills-and-far-away
  (POST "/" []
    :body-params [a :- s/Str]
    identity))

(fact "anonymous body models over defined routes"
  (let [app (api
              (swagger-routes)
              over-the-hills-and-far-away)]
    (fact "generated model doesn't have namespaced keys"
      (-> app get-spec :definitions vals first :properties keys first) => :a)))

(def foo
  (GET "/foo" []
    (let [foo {:foo "bar"}]
      (ok foo))))

(fact "defroutes with local symbol usage with same name (#123)"
  (let [app (api
              foo)]
    (let [[status body] (get* app "/foo")]
      status => 200
      body => {:foo "bar"})))

(def response-descriptions-routes
  (GET "/x" []
    :responses {500 {:schema {:code String}
                     :description "Horror"}}
    identity))

(fact "response descriptions"
  (let [app (api
              (swagger-routes)
              response-descriptions-routes)]
    (-> app get-spec :paths vals first :get :responses :500 :description) => "Horror"))

(fact "exceptions options with custom validation error handler"
  (let [app (api
              {:exceptions {:handlers {::ex/request-validation custom-validation-error-handler
                                       ::ex/request-parsing custom-validation-error-handler
                                       ::ex/response-validation custom-validation-error-handler}}}
              (swagger-routes)
              (POST "/get-long" []
                :body [body {:x Long}]
                :return Long
                (case (:x body)
                  1 (ok 1)
                  (ok "not a number"))))]

    (fact "return case, valid request & valid model"
      (let [[status body] (post* app "/get-long" "{\"x\": 1}")]
        status => 200
        body => 1))

    (fact "return case, not schema valid request"
      (let [[status body] (post* app "/get-long" "{\"x\": \"1\"}")]
        status => 400
        body => (contains {:custom-error "/get-long"})))

    (fact "return case, invalid json request"
      (let [[status body] (post* app "/get-long" "{x: 1}")]
        status => 400
        body => (contains {:custom-error "/get-long"})))

    (fact "return case, valid request & invalid model"
      (let [[status body] (post* app "/get-long" "{\"x\": 2}")]
        status => 501
        body => (contains {:custom-error "/get-long"})))))

(fact "exceptions options with custom exception and error handler"
  (let [app (api
              {:exceptions {:handlers {::ex/default (custom-exception-handler :custom-exception)
                                       SQLException (custom-exception-handler :sql-exception)
                                       ::custom-error custom-error-handler}}}
              (swagger-routes)
              (GET "/some-exception" []
                (throw (RuntimeException.)))
              (GET "/some-error" []
                (throw (ex-info "some ex info" {:data "some error" :type ::some-error})))
              (GET "/specific-error" []
                (throw (ex-info "my ex info" {:data "my error" :type ::custom-error})))
              (GET "/class" []
                (throw (SQLException.)))
              (GET "/sub-class" []
                (throw (SQLWarning.))))]

    (fact "uses default exception handler for unknown exceptions"
      (let [[status body] (get* app "/some-exception")]
        status => 200
        body => {:custom-exception "java.lang.RuntimeException"}))

    (fact "uses default exception handler for unknown errors"
      (let [[status body] (get* app "/some-error")]
        status => 200
        (:custom-exception body) => (contains ":data \"some error\"")))

    (fact "uses specific error handler for ::custom-errors"
      (let [[_ body] (get* app "/specific-error")]
        body => {:custom-error "my error"}))

    (fact "direct class"
      (let [[_ body] (get* app "/class")]
        body => (contains {:sql-exception "java.sql.SQLException"})))

    (fact "sub-class"
      (let [[_ body] (get* app "/sub-class")]
        body => (contains {:sql-exception "java.sql.SQLWarning"})))))

(fact "exception handling can be disabled"
  (let [app (api
              {:exceptions nil}
              (GET "/throw" []
                (throw (new RuntimeException))))]
    (get* app "/throw") => throws))

(s/defn schema-error [a :- s/Int]
  {:bar a})

(fact "handling schema.core/error"
  (let [app (api
              {:exceptions {:handlers {:schema.core/error ex/schema-error-handler}}}
              (GET "/:a" []
                :path-params [a :- s/Str]
                (ok (s/with-fn-validation (schema-error a)))))]
    (let [[status body] (get* app "/foo")]
      status => 400
      body => (contains {:errors vector?}))))

(fact "ring-swagger options"
  (let [app (api
              {:ring-swagger {:default-response-description-fn status/get-description}}
              (swagger-routes)
              (GET "/ping" []
                :responses {500 nil}
                identity))]
    (-> app get-spec :paths vals first :get :responses :500 :description)
    => "There was an internal server error."))

(fact "path-for"
  (fact "simple case"
    (let [app (api
                (GET "/api/pong" []
                  :name :pong
                  (ok {:pong "pong"}))
                (GET "/api/ping" []
                  (moved-permanently (path-for :pong))))]
      (fact "path-for works"
        (let [[status body] (get* app "/api/ping" {})]
          status => 200
          body => {:pong "pong"}))))

  (fact "with path parameters"
    (let [app (api
                (GET "/lost-in/:country/:zip" []
                  :name :lost
                  :path-params [country :- (s/enum :FI :EN), zip :- s/Int]
                  (ok {:country country
                       :zip zip}))
                (GET "/api/ping" []
                  (moved-permanently
                    (path-for :lost {:country :FI, :zip 33200}))))]
      (fact "path-for resolution"
        (let [[status body] (get* app "/api/ping" {})]
          status => 200
          body => {:country "FI"
                   :zip 33200}))))

  (fact "https://github.com/metosin/compojure-api/issues/150"
    (let [app (api
                (GET "/companies/:company-id/refresh" []
                  :path-params [company-id :- s/Int]
                  :name :refresh-company
                  :return String
                  (ok (path-for :refresh-company {:company-id company-id}))))]
      (fact "path-for resolution"
        (let [[status body] (get* app "/companies/4/refresh")]
          status => 200
          body => "/companies/4/refresh"))))

  (fact "multiple routes with same name fail at compile-time"
    (let [app' `(api
                  (GET "/api/pong" []
                    :name :pong
                    identity)
                  (GET "/api/ping" []
                    :name :pong
                    identity))]
      (eval app') => (throws RuntimeException))))


(fact "swagger-spec-path"
  (fact "defaults to /swagger.json"
    (let [app (api (swagger-routes))]
      (swagger/swagger-spec-path app) => "/swagger.json"))
  (fact "follows defined path"
    (let [app (api (swagger-routes {:spec "/api/api-docs/swagger.json"}))]
      (swagger/swagger-spec-path app) => "/api/api-docs/swagger.json")))

(defrecord NonSwaggerRecord [data])

(fact "api validation"

  (fact "a swagger api with valid swagger records"
    (let [app (api
                (swagger-routes)
                (GET "/ping" []
                  :return {:data s/Str}
                  (ok {:data "ping"})))]

      (fact "works"
        (let [[status body] (get* app "/ping")]
          status => 200
          body => {:data "ping"}))

      (fact "the api is valid"
        (validator/validate app) => app)))

  (fact "a swagger api with invalid swagger records"
    (let [app (api
                (swagger-routes)
                (GET "/ping" []
                  :return NonSwaggerRecord
                  (ok (->NonSwaggerRecord "ping"))))]

      (fact "works"
        (let [[status body] (get* app "/ping")]
          status => 200
          body => {:data "ping"}))

      (fact "the api is invalid"
        (validator/validate app)
        => (throws
             IllegalArgumentException
             (str
               "don't know how to convert class compojure.api.integration_test.NonSwaggerRecord "
               "into a Swagger Schema. Check out ring-swagger docs for details.")))))

  (fact "a non-swagger api with invalid swagger records"
    (let [app (api
                (GET "/ping" []
                  :return NonSwaggerRecord
                  (ok (->NonSwaggerRecord "ping"))))]

      (fact "works"
        (let [[status body] (get* app "/ping")]
          status => 200
          body => {:data "ping"}))

      (fact "the api is valid"
        (validator/validate app) => app))))

(fact "component integration"
  (let [system {:magic 42}]
    (fact "via options"
      (let [app (api
                  {:components system}
                  (GET "/magic" []
                    :components [magic]
                    (ok {:magic magic})))]
        (let [[status body] (get* app "/magic")]
          status => 200
          body => {:magic 42})))

    (fact "via middleware"
      (let [handler (api
                      (GET "/magic" []
                        :components [magic]
                        (ok {:magic magic})))
            app (mw/wrap-components handler system)]
        (let [[status body] (get* app "/magic")]
          status => 200
          body => {:magic 42})))))

(fact "sequential string parameters"
  (let [app (api
              (GET "/ints" []
                :query-params [i :- [s/Int]]
                (ok {:i i})))]
    (fact "multiple values"
      (let [[status body] (get* app "/ints?i=1&i=2&i=3")]
        status => 200
        body => {:i [1, 2, 3]}))
    (fact "single value"
      (let [[status body] (get* app "/ints?i=42")]
        status => 200
        body => {:i [42]}))))

(fact ":swagger params just for ducumentation"
  (fact "compile-time values"
    (let [app (api
                (swagger-routes)
                (GET "/route" [q]
                  :swagger {:x-name :boolean
                            :operationId "echoBoolean"
                            :description "Ehcoes a boolean"
                            :parameters {:query {:q s/Bool}}}
                  (ok {:q q})))]

      (fact "there is no coercion"
        (let [[status body] (get* app "/route" {:q "kikka"})]
          status => 200
          body => {:q "kikka"}))

      (fact "swagger-docs are generated"
        (-> app get-spec :paths vals first :get)
        => (contains
             {:x-name "boolean"
              :operationId "echoBoolean"
              :description "Ehcoes a boolean"
              :parameters [{:description ""
                            :in "query"
                            :name "q"
                            :required true
                            :type "boolean"}]}))))
  (fact "run-time values"
    (let [runtime-data {:x-name :boolean
                        :operationId "echoBoolean"
                        :description "Ehcoes a boolean"
                        :parameters {:query {:q s/Bool}}}
          app (api
                (swagger-routes)
                (GET "/route" [q]
                  :swagger runtime-data
                  (ok {:q q})))]

      (fact "there is no coercion"
        (let [[status body] (get* app "/route" {:q "kikka"})]
          status => 200
          body => {:q "kikka"}))

      (fact "swagger-docs are generated"
        (-> app get-spec :paths vals first :get)
        => (contains
             {:x-name "boolean"
              :operationId "echoBoolean"
              :description "Ehcoes a boolean"
              :parameters [{:description ""
                            :in "query"
                            :name "q"
                            :required true
                            :type "boolean"}]})))))

(fact "swagger-docs via api options, #218"
  (let [routes (routes
                 (context "/api" []
                   (GET "/ping" []
                     :summary "ping"
                     (ok {:message "pong"}))
                   (POST "/pong" []
                     :summary "pong"
                     (ok {:message "ping"})))
                 (ANY "*" []
                   (ok {:message "404"})))
        api1 (api {:swagger {:spec "/swagger.json", :ui "/"}} routes)
        api2 (api (swagger-routes) routes)]

    (fact "both generate same swagger-spec"
      (get-spec api1) => (get-spec api2))

    (fact "not-found handler works"
      (second (get* api1 "/missed")) => {:message "404"}
      (second (get* api2 "/missed")) => {:message "404"})))

(fact "more swagger-data can be (deep-)merged in - either via swagger-docs at runtime via mws, fixes #170"
  (let [app (api
              (middleware [[rsm/wrap-swagger-data {:paths {"/runtime" {:get {}}}}]]
                (swagger-routes
                  {:data
                   {:info {:version "2.0.0"}
                    :paths {"/extra" {:get {}}}}})
                (GET "/normal" [] (ok))))]
    (get-spec app) => (contains
                        {:paths (just
                                  {"/normal" irrelevant
                                   "/extra" irrelevant
                                   "/runtime" irrelevant})})))


(s/defschema Foo {:a [s/Keyword]})

(defapi with-defapi
  (swagger-routes)
  (GET "/foo" []
    :return Foo
    (ok {:a "foo"})))

(defn with-api []
  (api
    (swagger-routes)
    (GET "/foo" []
      :return Foo
      (ok {:a "foo"}))))

(fact "defapi & api define same results, #159"
  (get-spec with-defapi) => (get-spec (with-api)))

(fact "handling invalid routes with api"
  (let [invalid-routes (routes (constantly nil))]

    (fact "by default, logs the exception"
      (api invalid-routes) => truthy
      (provided
        (compojure.api.impl.logging/log! :warn irrelevant) => irrelevant :times 1))

    (fact "ignoring invalid routes doesn't log"
      (api {:api {:invalid-routes-fn nil}} invalid-routes) => truthy
      (provided
        (compojure.api.impl.logging/log! :warn irrelevant) => irrelevant :times 0))

    (fact "throwing exceptions"
      (api {:api {:invalid-routes-fn routes/fail-on-invalid-child-routes}} invalid-routes)) => throws))

(defmethod compojure.api.meta/restructure-param ::deprecated-middlewares-test [_ _ acc]
  (assoc acc :middlewares [(constantly nil)]))

(defmethod compojure.api.meta/restructure-param ::deprecated-parameters-test [_ _ acc]
  (assoc-in acc [:parameters :parameters :query] {:a String}))

(fact "old middlewares restructuring"

  (fact ":middlewares"
    (eval '(GET "/foo" []
             ::deprecated-middlewares-test true
             (ok)))
    => (throws AssertionError #":middlewares is deprecated with 1.0.0, use :middleware instead."))

  (fact ":parameters"
    (eval '(GET "/foo" []
             ::deprecated-parameters-test true
             (ok)))
    => (throws AssertionError #":parameters is deprecated with 1.0.0, use :swagger instead.")))

(fact "using local symbols for restructuring params"
  (let [responses {400 {:schema {:fail s/Str}}}
        app (api
              {:swagger {:spec "/swagger.json"
                         :data {:info {:version "2.0.0"}}}}
              (GET "/a" []
                :responses responses
                :return {:ok s/Str}
                (ok))
              (GET "/b" []
                :responses (assoc responses 500 {:schema {:m s/Str}})
                :return {:ok s/Str}
                (ok)))
        paths (:paths (get-spec app))]

    (get-in paths ["/a" :get :responses])
    => (just {:400 (just {:schema anything :description ""})
              :200 (just {:schema anything :description ""})})

    (get-in paths ["/b" :get :responses])
    => (just {:400 (just {:schema anything :description ""})
              :200 (just {:schema anything :description ""})
              :500 (just {:schema anything :description ""})})))

(fact "when functions are returned"
  (let [wrap-mw-params (fn [handler value]
                         (fn [request]
                           (handler
                             (update request ::mw #(str % value)))))]
    (fact "from endpoint"
      (let [app (GET "/ping" []
                  :middleware [[wrap-mw-params "1"]]
                  :query-params [{a :- s/Str "a"}]
                  (fn [req] (str (::mw req) a)))]

        (app {:request-method :get, :uri "/ping", :query-params {}}) => (contains {:body "1a"})
        (app {:request-method :get, :uri "/ping", :query-params {:a "A"}}) => (contains {:body "1A"})))

    (fact "from endpoint under context"
      (let [app (context "/api" []
                  :middleware [[wrap-mw-params "1"]]
                  :query-params [{a :- s/Str "a"}]
                  (GET "/ping" []
                    :middleware [[wrap-mw-params "2"]]
                    :query-params [{b :- s/Str "b"}]
                    (fn [req] (str (::mw req) a b))))]

        (app {:request-method :get, :uri "/api/ping", :query-params {}}) => (contains {:body "12ab"})
        (app {:request-method :get, :uri "/api/ping", :query-params {:a "A"}}) => (contains {:body "12Ab"})
        (app {:request-method :get, :uri "/api/ping", :query-params {:a "A", :b "B"}}) => (contains {:body "12AB"})))))

(defn check-for-response-handler
  "This response-validation handler checks for the existence of :response in its input. If it's there, it
  returns status 200, including the value that was origingally returned. Otherwise it returns 404."
  [^Exception e data request]
  (if (:response data)
    (ok {:message "Found :response in data!" :attempted-body (get-in data [:response :body])})
    (not-found "No :response key present in data!")))

(fact "response-validation handler has access to response value that failed coercion"
  (let [incorrect-return-value {:incorrect "response"}
        app (api
              {:exceptions {:handlers {::ex/response-validation check-for-response-handler}}}
              (swagger-routes)
              (GET "/test-response" []
                :return {:correct s/Str}
                ; This should fail and trigger our error handler
                (ok incorrect-return-value)))]

    (fact "return case, valid request & valid model"
      (let [[status body] (get* app "/test-response")]
        status => 200
        (:attempted-body body) => incorrect-return-value))))

(fact "correct swagger parameter order with small number or parameters, #224"
  (let [app (api
              (swagger-routes)
              (GET "/ping" []
                :query-params [a b c d e]
                (ok {:a a, :b b, :c c, :d d, :e e})))]
    (fact "api works"
      (let [[status body] (get* app "/ping" {:a "A" :b "B" :c "C" :d "D" :e "E"})]
        status => 200
        body => {:a "A" :b "B" :c "C" :d "D" :e "E"}))
    (fact "swagger parameters are in correct order"
      (-> app get-spec :paths (get "/ping") :get :parameters (->> (map (comp keyword :name)))) => [:a :b :c :d :e])))

(fact "empty top-level route, #https://github.com/metosin/ring-swagger/issues/92"
  (let [app (api
              {:swagger {:spec "/swagger.json"}}
              (GET "/" [] (ok {:kikka "kukka"})))]
    (fact "api works"
      (let [[status body] (get* app "/")]
        status => 200
        body => {:kikka "kukka"}))
    (fact "swagger docs"
      (-> app get-spec :paths keys) => ["/"])))

(fact "describe works on anonymous bodys, #168"
  (let [app (api
              (swagger-routes)
              (POST "/" []
                :body [body (describe {:kikka [{:kukka String}]} "kikkas")]
                (ok body)))]
    (fact "description is in place"
      (-> app get-spec :paths (get "/") :post :parameters first)
      => (contains {:description "kikkas"}))))

(facts "swagger responses headers are mapped correctly, #232"
  (let [app (api
              (swagger-routes)
              (context "/resource" []
                (resource
                  {:get {:responses {200 {:schema {:size s/Str}
                                          :description "size"
                                          :headers {"X-men" (describe s/Str "mutant")}}}}})))]
    (-> app get-spec :paths vals first :get :responses :200 :headers)
    => {:X-men {:description "mutant", :type "string"}}))

(facts "api-middleware can be disabled"
  (let [app (api
              {:api {:disable-api-middleware? true}}
              (swagger-routes)
              (GET "/params" [x] (ok {:x x}))
              (GET "/throw" [] (throw (RuntimeException. "kosh"))))]

    (fact "json-parsing & wrap-params is off"
      (let [[status body] (raw-get* app "/params" {:x 1})]
        status => 200
        body => {:x nil}))

    (fact "exceptions are not caught"
      (raw-get* app "/throw") => throws)))

(facts "custom formats contribute to Swagger :consumes & :produces"
  (let [custom-type "application/vnd.vendor.v1+json"
        custom-format {:decoder [muuntaja.format.json/make-json-decoder {:key-fn true}]
                       :encoder [muuntaja.format.json/make-json-encoder]}
        app (api
              {:swagger {:spec "/swagger.json"}
               :formats (-> muuntaja/default-options
                            (update :formats assoc custom-type custom-format)
                            (muuntaja/select-formats ["application/json" custom-type]))}
              (POST "/echo" []
                :body [data {:kikka s/Str}]
                (ok data)))]

    (fact "it works"
      (let [response (app {:uri "/echo"
                           :request-method :post
                           :body (json-stream {:kikka "kukka"})
                           :headers {"content-type" "application/vnd.vendor.v1+json"
                                     "accept" "application/vnd.vendor.v1+json"}})]

        (-> response :body slurp) => (json {:kikka "kukka"})
        (-> response :headers) => (contains {"Content-Type" "application/vnd.vendor.v1+json; charset=utf-8"})))

    (fact "spec is correct"
      (get-spec app)
      => (contains
           {:produces (just ["application/vnd.vendor.v1+json" "application/json"] :in-any-order)
            :consumes (just ["application/vnd.vendor.v1+json" "application/json"] :in-any-order)}))))

(facts ":body doesn't keywordize keys"
  (let [m (muuntaja/create)
        data {:items {"kikka" 42}}
        body* (atom nil)
        app (api
              (swagger-routes)
              (POST "/echo" []
                :body-params [items :- {:kikka Long}]
                (reset! body* {:items items})
                (ok))
              (POST "/echo2" []
                :body [body {:items {(s/required-key "kikka") Long}}]
                (reset! body* body)
                (ok)))]

    (facts ":body-params keywordizes params"
      (app {:uri "/echo"
            :request-method :post
            :body (muuntaja/encode m "application/transit+json" data)
            :headers {"content-type" "application/transit+json"
                      "accept" "application/transit+json"}}) => http/ok?
      @body* => {:items {:kikka 42}})

    (facts ":body does not keywordizes params"
      (app {:uri "/echo2"
            :request-method :post
            :body (muuntaja/encode m "application/transit+json" data)
            :headers {"content-type" "application/transit+json"
                      "accept" "application/transit+json"}}) => http/ok?
      @body* => {:items {"kikka" 42}})

    (facts "swagger spec is generated both ways"
      (let [spec (get-spec app)
            echo-schema-name (-> (get-in spec [:paths "/echo" :post :parameters 0 :name])
                                 name (str "Items") keyword)
            echo2-schema-name (-> (get-in spec [:paths "/echo2" :post :parameters 0 :name])
                                  name (str "Items") keyword)
            echo-schema (get-in spec [:definitions echo-schema-name :properties])
            echo2-schema (get-in spec [:definitions echo2-schema-name :properties])]
        echo-schema => {:kikka {:type "integer", :format "int64"}}
        echo2-schema => {:kikka {:type "integer", :format "int64"}}))))


(def ^:dynamic *response* nil)

(facts "format-based body & response coercion"
  (let [m (muuntaja/create)]

    (facts "application/transit & application/edn validate request & response (no coercion)"
      (let [valid-data {:items {"kikka" :kukka}}
            invalid-data {"items" {"kikka" :kukka}}
            Schema {:items {(s/required-key "kikka") s/Keyword}}
            app (api
                  (POST "/echo" []
                    :body [_ Schema]
                    :return Schema
                    (ok *response*)))]

        (doseq [format ["application/transit+json" "application/edn"]]
          (fact {:midje/description format}

            (fact "fails with invalid body"
              (app (ring-request m format invalid-data)) => http/bad-request?)

            (fact "fails with invalid response"
              (binding [*response* invalid-data]
                (app (ring-request m format valid-data)) => http/internal-server-error?))

            (fact "succeeds with valid body & response"
              (binding [*response* valid-data]
                (let [response (app (ring-request m format valid-data))]
                  response => http/ok?
                  (m/decode m format (:body response)) => valid-data)))))))

    (facts "application/json & application/x-yaml - coerce request, validate response"
      (let [valid-data {:int 1, :keyword "kikka"}
            valid-response-data {:int 1, :keyword :kikka}
            invalid-data {:int "1", :keyword "kikka"}
            Schema {:int s/Int, :keyword s/Keyword}
            app (api
                  (POST "/echo" []
                    :body [_ Schema]
                    :return Schema
                    (ok *response*)))]

        (doseq [format ["application/json" "application/x-yaml"]]
          (fact {:midje/description format}

            (fact "fails with invalid body"
              (app (ring-request m format invalid-data)) => http/bad-request?)

            (fact "fails with invalid response"
              (binding [*response* invalid-data]
                (app (ring-request m format valid-data)) => http/internal-server-error?))

            (fact "does not coerce response"
              (binding [*response* valid-data]
                (app (ring-request m format valid-data)) => http/internal-server-error?))

            (fact "succeeds with valid body & response"
              (binding [*response* valid-response-data]
                (let [response (app (ring-request m format valid-data))]
                  response => http/ok?
                  (m/decode m format (:body response)) => valid-data)))))))

    (facts "msgpack"
      ;; TODO: implement
      )))

(fact "static contexts just work"
  (let [app (context "/:a" [a]
              (GET "/:b" [b]
                (ok [a b])))]
    (app {:request-method :get, :uri "/a/b"}) => (contains {:body ["a" "b"]})
    (app {:request-method :get, :uri "/b/c"}) => (contains {:body ["b" "c"]})))

(facts "file responses don't get coerced"
  (let [message "aja hiljaa sillalla"
        app (api
              (swagger-routes)
              (GET "/file" []
                :return File
                (ok message)))]

    (fact "spec is not mounted"
      (let [[status body] (raw-get* app "/file")]
        status => 200
        body => message))))
