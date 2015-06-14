(ns compojure.api.integration-test
  (:require [compojure.api.sweet :refer :all]
            [compojure.api.test-utils :refer :all]
            [midje.sweet :refer :all]
            [flatland.ordered.map :as om]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [ring.swagger.core :as rsc]
            [compojure.api.swagger :as caw]
            [ring.util.http-status :as status]
            [compojure.api.middleware :as mw]))

;;
;; Data
;;

(s/defschema User {:id Long
                   :name String})

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
  [handler & [value]]
  (fn [request]
    (let [append #(str % (or value 1))
          request (update-in request [:headers mw*] append)
          response (handler request)]
      (update-in response [:headers mw*] append))))

(defn constant-middleware
  "This middleware rewrites all responses with a constant response."
  [_ & [res]] (constantly res))

(defn reply-mw*
  "Handler which replies with response where a header contains copy
   of the headers value from request and 7"
  [request]
  (-> (ok "true")
      (header mw* (str (get-in request [:headers mw*]) 7))))

(defn middleware-x
  "If request has query-param x, presume it's a integer and multiply it by two
   before passing request to next handler."
  [handler]
  (fn [req]
    (handler (update-in req [:query-params "x"] #(* (Integer. %) 2)))))

;;
;; Facts
;;

(facts "middlewares"
  (let [app (api
              (middlewares [middleware* (middleware* 2)]
                (context "/middlewares" []
                  (GET* "/simple" req (reply-mw* req))
                  (middlewares [(middleware* 3) (middleware* 4)]
                    (GET* "/nested" req (reply-mw* req))
                    (GET* "/nested-declared" req
                      :middlewares [(middleware* 5) (middleware* 6)]
                      (reply-mw* req))))))]

    (fact "are applied left-to-right"
      (let [[status _ headers] (get* app "/middlewares/simple" {})]
        status => 200
        (get headers mw*) => "12721"))

    (fact "are applied left-to-right closest one first"
      (let [[status _ headers] (get* app "/middlewares/nested" {})]
        status => 200
        (get headers mw*) => "123474321"))

    (fact "are applied left-to-right for both nested & declared cloest one first"
      (let [[status _ headers] (get* app "/middlewares/nested-declared" {})]
        status => 200
        (get headers mw*) => "1234567654321"))))

(facts "middlewares - multiple routes"
  (let [app (api
              (GET* "/first" []
                (ok {:value "first"}))
              (GET* "/second" []
                :middlewares [(constant-middleware (ok {:value "foo"}))]
                (ok {:value "second"}))
              (GET* "/third" []
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

(facts "middlewares - editing request"
  (let [app (api
              (GET* "/first" []
                :query-params [x :- Long]
                :middlewares [middleware-x]
                (ok {:value x})))]
    (fact "middleware edits the parameter before route body"
      (let [[status body] (get* app "/first?x=5" {})]
        status => 200
        body => {:value 10}))))

(fact ":body, :query, :headers and :return"
  (let [app (api
              (context "/models" []
                (GET* "/pertti" []
                  :return User
                  (ok pertti))
                (GET* "/user" []
                  :return User
                  :query [user User]
                  (ok user))
                (GET* "/invalid-user" []
                  :return User
                  (ok invalid-user))
                (GET* "/not-validated" []
                  (ok invalid-user))
                (POST* "/user" []
                  :return User
                  :body [user User]
                  (ok user))
                (POST* "/user_list" []
                  :return [User]
                  :body [users [User]]
                  (ok users))
                (POST* "/user_set" []
                  :return #{User}
                  :body [users #{User}]
                  (ok users))
                (POST* "/user_headers" []
                  :return User
                  :headers [user UserHeaders]
                  (ok (select-keys user [:id :name])))
                (POST* "/user_legacy" {user :body-params}
                  :return User
                  (ok user))))]

    (fact "GET*"
      (let [[status body] (get* app "/models/pertti")]
        status => 200
        body => pertti))

    (fact "GET* with smart destructuring"
      (let [[status body] (get* app "/models/user" pertti)]
        status => 200
        body => pertti))

    (fact "POST* with smart destructuring"
      (let [[status body] (post* app "/models/user" (json pertti))]
        status => 200
        body => pertti))

    (fact "POST* with smart destructuring - lists"
      (let [[status body] (post* app "/models/user_list" (json [pertti]))]
        status => 200
        body => [pertti]))

    (fact "POST* with smart destructuring - sets"
      (let [[status body] (post* app "/models/user_set" (json #{pertti}))]
        status => 200
        body => [pertti]))

    (fact "POST* with compojure destructuring"
      (let [[status body] (post* app "/models/user_legacy" (json pertti))]
        status => 200
        body => pertti))

    (fact "POST* with smart destructuring - headers"
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
        (:type body) => "json-parse-exception"
        (:message body) => truthy))))

(fact ":responses"
  (fact "normal cases"
    (let [app (api
                (swagger-docs)
                (GET* "/lotto/:x" []
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
        (let [[status spec] (get* app "/swagger.json" {})]
          status => 200
          (-> spec :paths vals first :get :responses keys set)
          => #{:200 :440 :403}))))

  (fact ":responses 200 and :return"
    (let [app (api
                (GET* "/lotto/:x" []
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
                (GET* "/lotto/:x" []
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
                (GET* "/plus" []
                  :query-params [x :- Long y :- Long]
                  (ok {:total (+ x y)}))
                (GET* "/multiply/:x/:y" []
                  :path-params [x :- Long y :- Long]
                  (ok {:total (* x y)}))
                (GET* "/power" []
                  :header-params [x :- Long y :- Long]
                  (ok {:total (long (Math/pow x y))}))
                (POST* "/minus" []
                  :body-params [x :- Long {y :- Long 1}]
                  (ok {:total (- x y)}))
                (POST* "/divide" []
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
  (let [api (api
              (context "/primitives" []
                (GET* "/return-long" []
                  :return Long
                  (ok 1))
                (GET* "/long" []
                  (ok 1))
                (GET* "/return-string" []
                  :return String
                  (ok "kikka"))))]

    (fact "when :return is set, longs can be returned"
      (let [[status body] (raw-get* api "/primitives/return-long")]
        status => 200
        body => "1"))

    (fact "when :return is not set, longs won't be encoded"
      (let [[status body] (raw-get* api "/primitives/long")]
        status => 200
        body => number?))

    (fact "when :return is set, raw strings can be returned"
      (let [[status body] (raw-get* api "/primitives/return-string")]
        status => 200
        body => "\"kikka\""))))

(fact "compojure destructuring support"
  (let [app (api
              (context "/destructuring" []
                (GET* "/regular" {{:keys [a]} :params}
                  (ok {:a a
                       :b (-> +compojure-api-request+ :params :b)}))
                (GET* "/regular2" {:as req}
                  (ok {:a (-> req :params :a)
                       :b (-> +compojure-api-request+ :params :b)}))
                (GET* "/vector" [a]
                  (ok {:a a
                       :b (-> +compojure-api-request+ :params :b)}))
                (GET* "/vector2" [:as req]
                  (ok {:a (-> req :params :a)
                       :b (-> +compojure-api-request+ :params :b)}))
                (GET* "/symbol" req
                  (ok {:a (-> req :params :a)
                       :b (-> +compojure-api-request+ :params :b)}))
                (GET* "/integrated" [a] :query-params [b]
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
              (GET* "/user" []
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
              {:format {:formats [:json-kw :edn]}}
              (swagger-docs)
              (GET* "/user" []
                (continue)))]

    (fact "api-listing"
      (let [[status spec] (get* app "/swagger.json")]
        status => 200
        spec => {:swagger "2.0"
                 :info {:title "Swagger API"
                        :version "0.0.1"}
                 :basePath "/"
                 :consumes ["application/json" "application/edn"]
                 :produces ["application/json" "application/edn"]
                 :definitions {}
                 :paths {(keyword "/user") {:get {:responses {:default {:description ""}}}}}}))))

(facts "swagger-docs with anonymous Return and Body models"
  (let [app (api
              (swagger-docs)
              (POST* "/echo" []
                :return (s/either {:a String})
                :body [_ (s/maybe {:a String})]
                identity))]

    (fact "api-docs"
      (let [[status spec] (get* app "/swagger.json")]

        (fact "are found"
          status => 200)

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
              (swagger-docs)
              (POST* "/" []
                :return ReturnValue
                :body [_ Boundary]
                identity))]

    (fact "api-docs"
      (let [[status spec] (get* app "/swagger.json")]

        (fact "are found"
          status => 200)

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
              (swagger-docs)
              (POST* "/" []
                :return Urho
                :body [_ Olipa]
                identity))]

    (fact "api-docs"
      (let [[status spec] (get* app "/swagger.json")]

        (fact "are found"
          status => 200)

        (fact "nested models are discovered correctly"
          (-> spec :definitions keys set)

          => #{:Urho :UrhoKaleva :UrhoKalevaKekkonen
               :Olipa :OlipaKerran :OlipaKerranAvaruus})))))

(fact "swagger-docs works with the :middlewares"
  (let [app (api
              (swagger-docs)
              (GET* "/middleware" []
                :query-params [x :- String]
                :middlewares [(constant-middleware (ok 1))]
                (ok 2)))]

    (fact "api-docs"
      (let [[status spec] (get* app "/swagger.json")]
        status => 200
        (-> spec :paths vals first) =>
        {:get {:parameters [{:description ""
                             :in "query"
                             :name "x"
                             :required true
                             :type "string"}]
               :responses {:default {:description ""}}}}))))

(fact "sub-context paths"
  (let [response {:ping "pong"}
        ok (ok response)
        ok? (fn [[status body]]
              (and (= status 200)
                   (= body response)))
        not-ok? (comp not ok?)
        app (api (swagger-docs)
                 (GET* "/" [] ok)
                 (GET* "/a" [] ok)
                 (context "/b" []
                   (context "/b1" []
                     (GET* "/" [] ok))
                   (context "/" []
                     (GET* "/" [] ok)
                     (GET* "/b2" [] ok))))]

    (fact "valid routes"
      (get* app "/") => ok?
      (get* app "/a") => ok?
      (get* app "/b/b1") => ok?
      (get* app "/b/b1/") => ok?
      (get* app "/b") => ok?
      (get* app "/b/") => ok?
      (get* app "/b//") => ok?
      (get* app "/b//b2") => ok?)

    (fact "invalid routes"
      (get* app "/b/b2") => not-ok?)

    ;; TODO: order!
    #_(fact "swagger-docs have trailing slashes removed"
      (let [[status spec] (get* api "/swagger.json")]
        status => 200
        (->> spec
             :paths
             keys) => (map keyword ["/" "/a" "/b/b1" "/b" "/b//b2"])))))

(fact "formats supported by ring-middleware-format"
  (let [app (api
              (POST* "/echo" []
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

(fact "accumulation in context*"
  (let [metas (atom nil)
        app (api
              (context* "/:id" []
                :path-params [id :- String]
                :tags [:api]
                :summary "jeah"
                (GET* "/:di/ping" []
                  :tags [:ipa]
                  :path-params [di :- String]
                  :query-params [foo :- s/Int]
                  (reset! metas +compojure-api-meta+)
                  (ok [id di foo]))))]

    (fact "all but lists & sequences get accumulated"
      (let [[status body] (get* app "/kikka/kukka/ping" {:foo 123})]
        status => 200
        body => ["kikka" "kukka" 123]
        @metas => {:parameters {:path {:id String
                                       :di String
                                       s/Keyword s/Any}
                                :query {:foo s/Int
                                        s/Keyword s/Any}}
                   :summary "jeah"
                   :tags #{:ipa}}))))

(fact "multiple routes in context*"
  (let [app (api
              (context* "/foo" []
                (GET* "/bar" [] (ok ["bar"]))
                (GET* "/baz" [] (ok ["baz"]))))]

    (fact "first route works"
      (let [[status body] (get* app "/foo/bar")]
        status => 200
        body => ["bar"]))
    (fact "second route works"
      (let [[status body] (get* app "/foo/baz")]
        status => 200
        body => ["baz"]))))

(fact "(deprecated) swaggered-macro still works"
  (let [app (api
              (swagger-docs)
              (swaggered "a"
                (GET* "/api/a" []
                  (ok "a")))
              (swaggered "b"
                (GET* "/api/b" []
                  (ok "b"))))]

    (fact "swaggered routes work"
      (let [[_ body] (raw-get* app "/api/a")]
        body => "a"))

    (fact "swaggered routes work"
      (let [[_ body] (raw-get* app "/api/b")]
        body => "b"))

    (fact "swaggered pushes tag to endpoints"
      (let [[status spec] (get* app "/swagger.json")]
        status => 200
        (:paths spec) => {:/api/a {:get {:responses {:default {:description ""}}
                                         :tags ["a"]}}
                          :/api/b {:get {:responses {:default {:description ""}}
                                         :tags ["b"]}}}))))

(require '[compojure.api.test-domain :refer [Pizza burger-routes]])

(fact "external deep schemas"
  (let [app (api
              (swagger-docs)
              burger-routes
              (POST* "/pizza" []
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
      (let [[status spec] (get* app "/swagger.json")]
        status => 200
        (-> spec :definitions keys set) => #{:Topping :Pizza :Burger :Beef}))))

(fact "multiple routes with same path & method in same file"
  (let [app (api
              (swagger-docs)
              (GET* "/ping" []
                :summary "active-ping"
                (ok {:ping "active"}))
              (GET* "/ping" []
                :summary "passive-ping"
                (ok {:ping "passive"})))]

    (fact "first route matches with Compojure"
      (let [[status body] (get* app "/ping" {})]
        status => 200
        body => {:ping "active"}))

    (fact "generates correct swagger-spec"
      (let [[status spec] (get* app "/swagger.json")]
        status => 200
        (-> spec :paths vals first :get :summary) => "active-ping"))))

(fact "multiple routes with same path & method over context*"
  (let [app (api
              (swagger-docs)
              (context* "/api" []
                (context* "/ipa" []
                  (GET* "/ping" []
                    :summary "active-ping"
                    (ok {:ping "active"}))))
              (context* "/api" []
                (context* "/ipa" []
                  (GET* "/ping" []
                    :summary "passive-ping"
                    (ok {:ping "passive"})))))]

    (fact "first route matches with Compojure"
      (let [[status body] (get* app "/api/ipa/ping" {})]
        status => 200
        body => {:ping "active"}))

    (fact "generates correct swagger-spec"
      (let [[status spec] (get* app "/swagger.json")]
        status => 200
        (-> spec :paths vals first :get :summary) => "active-ping"))))

(fact "multiple routes with same overall path (with different path sniplets & method over context*"
  (let [app (api
              (swagger-docs)
              (context* "/api/ipa" []
                (GET* "/ping" []
                  :summary "active-ping"
                  (ok {:ping "active"})))
              (context* "/api" []
                (context* "/ipa" []
                  (GET* "/ping" []
                    :summary "passive-ping"
                    (ok {:ping "passive"})))))]

    (fact "first route matches with Compojure"
      (let [[status body] (get* app "/api/ipa/ping" {})]
        status => 200
        body => {:ping "active"}))

    (fact "generates correct swagger-spec"
      (let [[status spec] (get* app "/swagger.json")]
        status => 200
        (-> spec :paths vals first :get :summary) => "active-ping"))))

(comment
  "https://github.com/Prismatic/schema/pull/212"

  (s/defschema Kikka
    (om/ordered-map
      :a s/Str, :b s/Str, :c s/Str, :d s/Str, :e s/Str, :f s/Str, :g s/Str, :h s/Str))

  (def data (om/ordered-map :a "a", :b "b", :c "c", :d "d", :e "e", :f "f", :g "g", :h "h"))

  (defapi api
    (swagger-docs)
    (GET* "/ping" []
      :return Kikka
      (ok data)))

  (fact "ordered schema test"

    (fact "first route matches with Compojure"
      (let [[status body] (get* api "/ping" {})]
        status => 200
        body => data))

    (fact "generates correct swagger-spec"
      (let [[status spec] (get* api "/swagger.json")]
        status => 200
        (-> spec :definitions :Kikka :properties keys) => (keys Kikka)))))

; https://github.com/metosin/compojure-api/issues/98
(fact "basePath"
  (let [app (api (swagger-docs))]

    (fact "no context"
      (let [[status spec] (get* app "/swagger.json")]
        status => 200
        (:basePath spec) => "/"))

    (fact "app-servers with given context"
      (against-background (rsc/context anything) => "/v2")
      (let [[status spec] (get* app "/swagger.json")]
        status => 200
        (:basePath spec) => "/v2"))))

(fact "multiple different models with same name"

  (fact "schemas with same regexps are not equal"
    {:d #"\D"} =not=> {:d #"\D"})

  (fact "api-spec with 2 schemas with non-equal contents"
    (let [app (api
                (swagger-docs)
                (GET* "/" []
                  :responses {200 {:schema (s/schema-with-name {:a {:d #"\D"}} "Kikka")}
                              201 {:schema (s/schema-with-name {:a {:d #"\D"}} "Kikka")}}
                  identity))]
      (fact "api spec doesn't fail (#102)"
        (let [[status spec] (get* app "/swagger.json")]
          status => 200
          spec => anything)))))

(defroutes* over-the-hills-and-far-away
  (POST* "/" []
    :body-params [a :- s/Str]
    identity))

(fact "anonymous body models over defroutes*"
  (let [app (api
              (swagger-docs)
              over-the-hills-and-far-away)]
    (fact "generated model doesn't have namespaced keys"
      (let [[status spec] (get* app "/swagger.json")]
        status => 200
        (-> spec :definitions vals first :properties keys first) => :a))))

(defroutes* response-descriptions-routes
  (GET* "/x" []
    :responses {500 {:schema {:code String}
                     :description "Horror"}}
    identity))

(fact "response descriptions"
  (let [app (api
              (swagger-docs)
              response-descriptions-routes)
        [status spec] (get* app "/swagger.json")]
    status => 200
    (-> spec :paths vals first :get :responses :500 :description)
    => "Horror"))

(fact "ring-swagger options"
  (let [app (api
              {:ring-swagger {:default-response-description-fn status/get-description}}
              (swagger-docs)
              (GET* "/ping" []
                :responses {500 nil}
                identity))
        [status spec] (get* app "/swagger.json")]
    status => 200
    (-> spec :paths vals first :get :responses :500 :description)
    => "There was an internal server error."))

(fact "path-for"
  (fact "simple case"
    (let [app (api
                (GET* "/api/pong" []
                  :name :pong
                  (ok {:pong "pong"}))
                (GET* "/api/ping" []
                  (moved-permanently (path-for :pong))))]
      (fact "path-for works"
        (let [[status body] (get* app "/api/ping" {})]
          status => 200
          body => {:pong "pong"}))))

  (fact "with path parameters"
    (let [app (api
                (GET* "/lost-in/:country/:zip" []
                  :name :lost
                  :path-params [country :- (s/enum :FI :EN), zip :- s/Int]
                  (ok {:country country
                       :zip zip}))
                (GET* "/api/ping" []
                  (moved-permanently
                    (path-for :lost {:country :FI, :zip 33200}))))]
      (fact "path-for resolution"
        (let [[status body] (get* app "/api/ping" {})]
          status => 200
          body => {:country "FI"
                   :zip 33200}))))

  (fact "multiple routes with same name fail at compile-time"
    (let [app' `(api
                  (GET* "/api/pong" []
                    :name :pong
                    identity)
                  (GET* "/api/ping" []
                    :name :pong
                    identity))]
      (eval app') => (throws RuntimeException))))

(fact "swagger-api?"
  (fact "false, when no swagger-docs is mounted"
    (let [app (api
                (GET* "/ping" [] identity))]
      (caw/swagger-api? app) => false))
  (fact "true, when swagger-docs is mounted"
    (let [app (api
                (swagger-docs)
                (GET* "/ping" [] identity))]
      (caw/swagger-api? app) => true)))

(fact "swagger-spec-path"
  (fact "defaults to swagger.json"
    (let [app (api (swagger-docs))]
      (caw/swagger-spec-path app) => "/swagger.json"))
  (fact "follows defined path"
    (let [app (api (swagger-docs "/api/api-docs/swagger.json"))]
      (caw/swagger-spec-path app) => "/api/api-docs/swagger.json")))

(defrecord NonSwaggerRecord [data])

(fact "api validation"

  (fact "a swagger api with valid swagger records"
    (let [app (api
                (swagger-docs)
                (GET* "/ping" []
                  :return {:data s/Str}
                  (ok {:data "ping"})))]

      (fact "works"
        (let [[status body] (get* app "/ping")]
          status => 200
          body => {:data "ping"}))

      (fact "a swagger-api"
        (caw/swagger-api? app) => true)

      (fact "the api is valid"
        (caw/validate app) => truthy)))

  (fact "a pre-validated swagger api with valid swagger records"
    (let [app (caw/validate
                (api
                  (swagger-docs)
                  (GET* "/ping" []
                    :return {:data s/Str}
                    (ok {:data "ping"}))))]

      (fact "works"
        (let [[status body] (get* app "/ping")]
          status => 200
          body => {:data "ping"}))))

  (fact "a swagger api with invalid swagger records"
    (let [app (api
                (swagger-docs)
                (GET* "/ping" []
                  :return NonSwaggerRecord
                  (ok (->NonSwaggerRecord "ping"))))]

      (fact "works"
        (let [[status body] (get* app "/ping")]
          status => 200
          body => {:data "ping"}))

      (fact "it's a swagger-api"
        (caw/swagger-api? app) => true)

      (fact "the api is invalid"
        (caw/validate app)
        => (throws
             IllegalArgumentException
             "don't know how to create json-type of: class compojure.api.integration_test.NonSwaggerRecord"))))

  (fact "a pre-validated swagger api with invalid swagger records"
    (let [app' `(caw/validate
                  (api
                    (swagger-docs)
                    (GET* "/ping" []
                      :return NonSwaggerRecord
                      (ok (->NonSwaggerRecord "ping")))))]

      (fact "fails at compile-time"
        (eval app')
        => (throws
             IllegalArgumentException
             "don't know how to create json-type of: class compojure.api.integration_test.NonSwaggerRecord"))))

  (fact "a non-swagger api with invalid swagger records"
    (let [app (api
                (GET* "/ping" []
                  :return NonSwaggerRecord
                  (ok (->NonSwaggerRecord "ping"))))]

      (fact "works"
        (let [[status body] (get* app "/ping")]
          status => 200
          body => {:data "ping"}))

      (fact "not a swagger-api"
        (caw/swagger-api? app) => false)

      (fact "the api is valid"
        (caw/validate app) => truthy))))

(fact "component integration"
  (fact "via options"
    (let [system {:magic 42}
          app (api
                {:components system}
                (GET* "/magic" []
                  :components [magic]
                  (ok {:magic magic})))]
      (let [[status body] (get* app "/magic")]
        status => 200
        body => {:magic 42}))))
