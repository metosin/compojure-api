(ns compojure.api.integration-test
  (:require [compojure.api.sweet :refer :all]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [compojure.api.test-domain :refer [Pizza burger-routes]]
            [compojure.api.test-utils :refer :all]
            [compojure.api.exception :as ex]
            [compojure.api.swagger :as swagger]
            [ring.util.http-response :refer :all]
            [ring.util.http-predicates :as http]
            [schema.core :as s]
            [ring.swagger.core :as rsc]
            [ring.util.http-status :as status]
            [compojure.api.middleware :as mw]
            [ring.swagger.middleware :as rsm]
            [compojure.api.validator :as validator]
            [compojure.api.request :as request]
            [compojure.api.routes :as routes]
            [muuntaja.core :as m]
            [compojure.api.core :as c]
            [clojure.java.io :as io]
            [muuntaja.format.msgpack]
            [muuntaja.format.yaml])
  (:import (java.sql SQLException SQLWarning)
           (muuntaja.protocols StreamableResponse)
           (java.io File ByteArrayInputStream)))

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

(defn is-200-status [status]
  (is (= 200 status)))

(defn middleware*
  "This middleware appends given value or 1 to a header in request and response."
  ([handler] (middleware* handler 1))
  ([handler value]
   (fn
     ([request]
      (let [append #(str % value)
            request (update-in request [:headers mw*] append)
            response (handler request)]
        (update-in response [:headers mw*] append)))
     ([request respond raise]
      (let [append #(str % value)
            request (update-in request [:headers mw*] append)]
        (handler request #(respond (update-in % [:headers mw*] append)) raise))))))

(defn constant-middleware
  "This middleware rewrites all responses with a constant response."
  [_ res]
  (fn
    ([_] res)
    ([_ respond _] (respond res))))

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
  (fn
    ([req]
     (handler (update-in req [:query-params "x"] #(* (Integer. %) 2))))
    ([req respond raise]
     (handler (update-in req [:query-params "x"] #(* (Integer. %) 2)) respond raise))))

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

(deftest core-routes-test

  (testing "keyword options"
    (let [route (GET "/ping" []
                  :return String
                  (ok "kikka"))]
      (is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))))

  (testing "map options"
    (let [route (GET "/ping" []
                  {:return String}
                  (ok "kikka"))]
      (is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))))

  (testing "map return"
    (let [route (GET "/ping" []
                  {:body "kikka"})]
      (is (= "kikka" (:body (route {:request-method :get :uri "/ping"})))))))

(deftest middleware-ordering-test
  (let [app (api
              {:formatter :muuntaja
               :middleware [[middleware* 0]]}
              (route-middleware [[middleware* "a"] [middleware* "b"]]
                (context "/middlewares" []
                  :middleware [(fn [handler] (middleware* handler 1)) [middleware* 2]]
                  (GET "/simple" req (reply-mw* req))
                  (route-middleware [#(middleware* % "c") [middleware* "d"]]
                    (GET "/nested" req (reply-mw* req))
                    (GET "/nested-declared" req
                      :middleware [(fn [handler] (middleware* handler "e")) [middleware* "f"]]
                      (reply-mw* req))))))]

    (testing "are applied left-to-right"
      (let [[status _ headers] (get* app "/middlewares/simple" {})]
        (is (= 200 status))
        (is (= "012ab/ba210" (get headers mw*)))))

    (testing "are applied left-to-right closest one first"
      (let [[status _ headers] (get* app "/middlewares/nested" {})]
        (is (= 200 status))
        (is (= "012abcd/dcba210" (get headers mw*)))))

    (testing "are applied left-to-right for both nested & declared closest one first"
      (let [[status _ headers] (get* app "/middlewares/nested-declared" {})]
        (is (= 200 status))
        (is (= "012abcdef/fedcba210" (get headers mw*)))))))

(deftest context-middleware-test
  (let [app (api
              {:formatter :muuntaja}
              (context "/middlewares" []
                :middleware [(fn [h] (fn mw
                                       ([r] (ok {:middleware "hello"}))
                                       ([r respond _] (respond (mw r)))))]
                (GET "/simple" req (reply-mw* req))))]

    (testing "is applied even if route is not matched"
      (let [[status body] (get* app "/middlewares/non-existing" {})]
        (is (= 200 status))
        (is (= {:middleware "hello"} body))))))

(deftest middleware-multiple-routes-test
  (let [app (api
              {:formatter :muuntaja}
              (GET "/first" []
                (ok {:value "first"}))
              (GET "/second" []
                :middleware [[constant-middleware (ok {:value "foo"})]]
                (ok {:value "second"}))
              (GET "/third" []
                (ok {:value "third"})))]
    (testing "first returns first"
      (let [[status body] (get* app "/first" {})]
        (is (= 200 status))
        (is (= {:value "first"} body))))
    (testing "second returns foo"
      (let [[status body] (get* app "/second" {})]
        (is-200-status status)
        (is (= {:value "foo"} body))))
    (testing "third returns third"
      (let [[status body] (get* app "/third" {})]
        (is-200-status status)
        (is (= {:value "third"} body))))))

(deftest middleware-editing-request-test
  (let [app (api
              {:formatter :muuntaja}
              (GET "/first" []
                :query-params [x :- Long]
                :middleware [middleware-x]
                (ok {:value x})))]
    (testing "middleware edits the parameter before route body"
      (let [[status body] (get* app "/first?x=5" {})]
        (is-200-status status)
        (is (= {:value 10} body))))))

(deftest body-query-headers-and-return-test
  (let [app (api
              {:formatter :muuntaja}
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

    (testing "GET"
      (let [[status body] (get* app "/models/pertti")]
        (is-200-status status)
        (is (= pertti body))))

    (testing "GET with smart destructuring"
      (let [[status body] (get* app "/models/user" pertti)]
        (is-200-status status)
        (is (= pertti body))))

    (testing "POST with smart destructuring"
      (let [[status body] (post* app "/models/user" (json-string pertti))]
        (is-200-status status)
        (is (= pertti body))))

    (testing "POST with smart destructuring - lists"
      (let [[status body] (post* app "/models/user_list" (json-string [pertti]))]
        (is-200-status status)
        (is (= [pertti] body))))

    (testing "POST with smart destructuring - sets"
      (let [[status body] (post* app "/models/user_set" (json-string #{pertti}))]
        (is-200-status status)
        (is (= [pertti] body))))

    (testing "POST with compojure destructuring"
      (let [[status body] (post* app "/models/user_legacy" (json-string pertti))]
        (is-200-status status)
        (is (= pertti body))))

    (testing "POST with smart destructuring - headers"
      (let [[status body] (headers-post* app "/models/user_headers" pertti)]
        (is-200-status status)
        (is (= pertti body))))

    (testing "Validation of returned data"
      (let [[status] (get* app "/models/invalid-user")]
        (is (= 500 status))))

    (testing "Routes without a :return parameter aren't validated"
      (let [[status body] (get* app "/models/not-validated")]
        (is-200-status status)
        (is (= invalid-user body))))

    (testing "Invalid json in body causes 400 with error message in json"
      (let [[status body] (post* app "/models/user" "{INVALID}")]
        (is (= 400 status))
        (is (= "compojure.api.exception/request-parsing" (:type body)))
        (is (str/starts-with? (:message body) "Malformed application/json"))
        (is (str/starts-with? (:original body) "Unexpected character"))))))

(deftest responses-test
  (testing "normal cases"
    (let [app (api
                {:formatter :muuntaja}
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

      (testing "return case"
        (let [[status body] (get* app "/lotto/1")]
          (is-200-status status)
          (is (= [1] body))))

      (testing "return case, non-matching model"
        (let [[status body] (get* app "/lotto/2")]
          (is (= 500 status))
          (is (vector? (:errors body)))))

      (testing "error case"
        (let [[status body] (get* app "/lotto/3")]
          (is (= 403 status))
          (is (= ["error"] body))))

      (testing "error case, non-matching model"
        (let [[status body] (get* app "/lotto/4")]
          (is (= 500 status))
          (is (-> body :errors vector?))))

      (testing "returning non-predefined http-status code works"
        (let [[status body] (get* app "/lotto/5")]
          (is (= {:message "not-found"} body))
          (is (= 404 status))))

      (testing "swagger-docs for multiple returns"
        (-> app get-spec :paths vals first :get :responses keys set))))

  (testing ":responses 200 and :return"
    (let [app (api
                {:formatter :muuntaja}
                (GET "/lotto/:x" []
                  :path-params [x :- Long]
                  :return {:return String}
                  :responses {200 {:schema {:value String}}}
                  (case x
                    1 (ok {:return "ok"})
                    2 (ok {:value "ok"}))))]

      (testing "return case"
        (let [[status body] (get* app "/lotto/1")]
          (is (= 500 status))
          (is (= {:return "disallowed-key"
                  :value "missing-required-key"}
                 (:errors body)))))

      (testing "return case"
        (let [[status body] (get* app "/lotto/2")]
          (is-200-status status)
          (is (= {:value "ok"} body))))))

  (testing ":responses 200 and :return - other way around"
    (let [app (api
                {:formatter :muuntaja}
                (GET "/lotto/:x" []
                  :path-params [x :- Long]
                  :responses {200 {:schema {:value String}}}
                  :return {:return String}
                  (case x
                    1 (ok {:return "ok"})
                    2 (ok {:value "ok"}))))]

      (testing "return case"
        (let [[status body] (get* app "/lotto/1")]
          (is-200-status status)
          (is (= {:return "ok"} body))))

      (testing "return case"
        (let [[status body] (get* app "/lotto/2")]
          (is (= 500 status))
          (is (= {:return "missing-required-key"
                  :value "disallowed-key"}
                 (:errors body))))))))

(deftest query-params-path-params-header-params-body-params-and-form-params-test
  (let [app (api
              {:formatter :muuntaja}
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

    (testing "query-parameters"
      (let [[status body] (get* app "/smart/plus" {:x 2 :y 3})]
        (is-200-status status)
        (is (= {:total 5} body))))

    (testing "path-parameters"
      (let [[status body] (get* app "/smart/multiply/2/3")]
        (is-200-status status)
        (is (= {:total 6} body))))

    (testing "header-parameters"
      (let [[status body] (get* app "/smart/power" {} {:x 2 :y 3})]
        (is-200-status status)
        (is (= {:total 8} body))))

    (testing "form-parameters"
      (let [[status body] (form-post* app "/smart/divide" {:x 6 :y 3})]
        (is-200-status status)
        (is (= {:total 2} body))))

    (testing "body-parameters"
      (let [[status body] (post* app "/smart/minus" (json-string {:x 2 :y 3}))]
        (is-200-status status)
        (is (= {:total -1} body))))

    (testing "default parameters"
      (let [[status body] (post* app "/smart/minus" (json-string {:x 2}))]
        (is-200-status status)
        (is (= {:total 1} body))))))

(deftest primitive-support-test
  (let [app (api
              {:formatter :muuntaja
               :swagger {:spec "/swagger.json"}}
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

    (testing "when :return is set, longs can be returned"
      (let [[status body] (raw-get* app "/primitives/return-long")]
        (is-200-status status)
        (is (= "1" body))))

    (testing "when :return is not set, longs won't be encoded"
      (let [[status body] (raw-get* app "/primitives/long")]
        (is-200-status status)
        (is (number? body))))

    (testing "when :return is set, raw strings can be returned"
      (let [[status body] (raw-get* app "/primitives/return-string")]
        (is-200-status status)
        (is (= "\"kikka\"" body))))

    (testing "primitive arrays work"
      (let [[status body] (raw-post* app "/primitives/arrays" (json-string [1 2 3]))]
        (is-200-status status)
        (is (= "[1,2,3]" body))))

    (testing "swagger-spec is valid"
      (validator/validate app))

    (testing "primitive array swagger-docs are good"

      (is (= [{:description ""
               :in "body"
               :name ""
               :required true
               :schema {:items {:format "int64"
                                :type "integer"}
                        :type "array"}}]
             (-> app get-spec :paths (get "/primitives/arrays") :post :parameters)))

      (is (= {:items {:format "int64",
                      :type "integer"},
              :type "array"}
             (-> app get-spec :paths (get "/primitives/arrays") :post :responses :200 :schema))))))

(deftest compojure-destructuring-support-test
  (let [app (api
              {:formatter :muuntaja}
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
                (GET "/integrated" [a]
                  :query-params [b]
                  (ok {:a a
                       :b b}))))]

    (doseq [uri ["regular" "regular2" "vector" "vector2" "symbol" "integrated"]]
      (testing uri
        (let [[status body] (get* app (str "/destructuring/" uri) {:a "a" :b "b"})]
          (is-200-status status)
          (is (= {:a "a" :b "b"} body)))))))

(deftest counting-execution-times-issue-19-test
  (let [execution-times (atom 0)
        app (api
              {:formatter :muuntaja}
              (GET "/user" []
                :return User
                :query [user User]
                (swap! execution-times inc)
                (ok user)))]

    (testing "body is executed one"
      (is (zero? @execution-times))
      (let [[status body] (get* app "/user" pertti)]
        (is-200-status status)
        (is (= pertti body)))
      (is (= 1 @execution-times)))))

(deftest swagger-docs-test
  (let [app (api
              {:formats (m/select-formats
                          m/default-options
                          ["application/json" "application/edn"])}
              (swagger-routes)
              (GET "/user" []
                (continue)))]

    (testing "api-listing shows produces & consumes for known types"
      (is (= {:swagger "2.0"
              :info {:title "Swagger API"
                     :version "0.0.1"}
              :basePath "/"
              :consumes ["application/json" "application/edn"]
              :produces ["application/json" "application/edn"]
              :definitions {}
              :paths {"/user" {:get {:responses {:default {:description ""}}}}}}
             (get-spec app)))))

  (testing "swagger-routes"

    (testing "with defaults"
      (let [app (api {:formatter :muuntaja} (swagger-routes))]

        (testing "api-docs are mounted to /"
          (let [[status body] (raw-get* app "/")]
            (is-200-status status)
            (is (str/includes? body "<title>Swagger UI</title>"))))

        (testing "spec is mounted to /swagger.json"
          (let [[status body] (get* app "/swagger.json")]
            (is-200-status status)
            (is (= "2.0" (:swagger body)))))))

    (testing "with partial overridden values"
      (let [app (api
                  {:formatter :muuntaja}
                  (swagger-routes {:ui "/api-docs"
                                   :data {:info {:title "Kikka"}
                                          :paths {"/ping" {:get {}}}}}))]

        (testing "api-docs are mounted"
          (let [[status body] (raw-get* app "/api-docs")]
            (is-200-status status)
            (is (str/includes? body "<title>Swagger UI</title>"))))

        (testing "spec is mounted to /swagger.json"
          (let [[status body] (get* app "/swagger.json")]
            (is-200-status status)
            (is (= "2.0" (:swagger body)))
            (is (= "Kikka" (-> body :info :title)))
            (is (some? (-> body :paths (get (keyword "/ping"))))))))))

  (testing "swagger via api-options"

    (testing "with defaults"
      (let [app (api {:formatter :muuntaja})]

        (testing "api-docs are not mounted"
          (let [[status body] (raw-get* app "/")]
            (is (nil? status))))

        (testing "spec is not mounted"
          (let [[status body] (get* app "/swagger.json")]
            (is (= nil status))))))

    (testing "with spec"
      (let [app (api {:formatter :muuntaja
                      :swagger {:spec "/swagger.json"}})]

        (testing "api-docs are not mounted"
          (let [[status body] (raw-get* app "/")]
            (is (= nil status))))

        (testing "spec is mounted to /swagger.json"
          (let [[status body] (get* app "/swagger.json")]
            (is-200-status status)
            (is (= "2.0" (:swagger body))))))))

  (testing "with ui"
    (let [app (api {:formatter :muuntaja
                    :swagger {:ui "/api-docs"}})]

      (testing "api-docs are mounted"
        (let [[status body] (raw-get* app "/api-docs")]
          (is-200-status status)
          (is (str/includes? body "<title>Swagger UI</title>"))))

      (testing "spec is not mounted"
        (let [[status body] (get* app "/swagger.json")]
          (is (= nil status))))))

  (testing "with ui and spec"
    (let [app (api {:formatter :muuntaja
                    :swagger {:spec "/swagger.json", :ui "/api-docs"}})]

      (testing "api-docs are mounted"
        (let [[status body] (raw-get* app "/api-docs")]
          (is-200-status status)
          (str/includes? body "<title>Swagger UI</title>")))

      (testing "spec is mounted to /swagger.json"
        (let [[status body] (get* app "/swagger.json")]
          (is-200-status status)
          (is (= "2.0" (:swagger body))))))))

(deftest swagger-docs-with-anonymous-Return-and-Body-models-test
  (let [app (api
              {:formatter :muuntaja}
              (swagger-routes)
              (POST "/echo" []
                :return (s/either {:a String})
                :body [_ (s/maybe {:a String})]
                identity))]

    (testing "api-docs"
      (let [spec (get-spec app)]

        (let [operation (some-> spec :paths vals first :post)
              body-ref (some-> operation :parameters first :schema :$ref)
              return-ref (get-in operation [:responses :200 :schema :$ref])]

          (testing "generated body-param is found in Definitions"
            (is (find-definition spec body-ref)))

          (testing "generated return-param is found in Definitions"
            (is return-ref)
            (is (find-definition spec body-ref))))))))

(def Boundary
  {:type (s/enum "MultiPolygon" "Polygon" "MultiPoint" "Point")
   :coordinates [s/Any]})

(def ReturnValue
  {:boundary (s/maybe Boundary)})

;; "https://github.com/metosin/compojure-api/issues/53"
(deftest issue-53-test
  (let [app (api
              {:formatter :muuntaja}
              (swagger-routes)
              (POST "/" []
                :return ReturnValue
                :body [_ Boundary]
                identity))]

    (testing "api-docs"
      (let [spec (get-spec app)]

        (let [operation (some-> spec :paths vals first :post)
              body-ref (some-> operation :parameters first :schema :$ref)
              return-ref (get-in operation [:responses :200 :schema :$ref])]

          (testing "generated body-param is found in Definitions"
            (is (find-definition spec body-ref)))

          (testing "generated return-param is found in Definitions"
            (is return-ref)
            (is (find-definition spec body-ref))))))))

(s/defschema Urho {:kaleva {:kekkonen {s/Keyword s/Any}}})
(s/defschema Olipa {:kerran {:avaruus {s/Keyword s/Any}}})

; https://github.com/metosin/compojure-api/issues/94
(deftest preserves-deeply-nested-schema-names-test
  (let [app (api
              {:formatter :muuntaja}
              (swagger-routes)
              (POST "/" []
                :return Urho
                :body [_ Olipa]
                identity))]

    (testing "api-docs"
      (let [spec (get-spec app)]

        (testing "nested models are discovered correctly"
          (is (= #{:Urho :UrhoKaleva :UrhoKalevaKekkonen
                   :Olipa :OlipaKerran :OlipaKerranAvaruus}
                 (-> spec :definitions keys set))))))))

(deftest swagger-docs-works-with-the-middleware-test
  (let [app (api
              {:formatter :muuntaja}
              (swagger-routes)
              (GET "/middleware" []
                :query-params [x :- String]
                :middleware [[constant-middleware (ok 1)]]
                (ok 2)))]

    (testing "api-docs"
      (is (= {:get {:parameters [{:description ""
                                  :in "query"
                                  :name "x"
                                  :required true
                                  :type "string"}]
                    :responses {:default {:description ""}}}}
             (-> app get-spec :paths vals first))))))

(deftest sub-context-paths-test
  (let [response {:ping "pong"}
        ok (ok response)
        ok? (fn [[status body]]
              (and (= status 200)
                   (= body response)))
        app (api
              {:formatter :muuntaja}
              (swagger-routes {:ui nil})
              (GET "/" [] ok)
              (GET "/a" [] ok)
              (context "/b" []
                (context "/b1" []
                  (GET "/" [] ok))
                (context "/" []
                  (GET "/" [] ok)
                  (GET "/b2" [] ok))))]

    (testing "valid routes"
      (is (ok? (get* app "/")))
      (is (ok? (get* app "/a")))
      (is (ok? (get* app "/b/b1")))
      (is (ok? (get* app "/b")))
      (is (ok? (get* app "/b/b2"))))

    (testing "undocumented compojure easter eggs"
      (is (ok? (get* app "/b/b1/")))
      (is (ok? (get* app "/b/")))
      (testing "this is fixed in compojure 1.5.1"
        (is (not (ok? (get* app "/b//"))))))

    (testing "swagger-docs have trailing slashes removed"
      (is (= (sort ["/" "/a" "/b/b1" "/b" "/b/b2"])
             (-> app get-spec :paths keys sort))))))

(deftest formats-supported-by-ring-middleware-format-test
  (let [app (api
              {:formatter :muuntaja}
              (POST "/echo" []
                :body-params [foo :- String]
                (ok {:foo foo})))]

    (doseq [[?content-type ?body] [["application/json" "{\"foo\":\"bar\"}"]
                                   ["application/edn" "{:foo \"bar\"}"]
                                   ["application/transit+json" "[\"^ \",\"~:foo\",\"bar\"]"]]]
      (testing (pr-str [?content-type ?body])
        (testing (str ?content-type " to json")
          (let [[status body]
                (raw-post* app "/echo" ?body ?content-type {:accept "application/json"})]
            (is-200-status status)
            (is (= "{\"foo\":\"bar\"}" body))))
        (testing (str "json to " ?content-type)
          (let [[status body]
                (raw-post* app "/echo" "{\"foo\":\"bar\"}" "application/json" {:accept ?content-type})]
            (is-200-status status)
            (is (= ?body body))))))))

(deftest multiple-routes-in-context-test
  (let [app (api
              {:formatter :muuntaja}
              (context "/foo" []
                (GET "/bar" [] (ok ["bar"]))
                (GET "/baz" [] (ok ["baz"]))))]

    (testing "first route works"
      (let [[status body] (get* app "/foo/bar")]
        (is-200-status status)
        (is (= ["bar"] body))))
    (testing "second route works"
      (let [[status body] (get* app "/foo/baz")]
        (is-200-status status)
        (is (= ["baz"] body))))))

(deftest external-deep-schemas-test
  (let [app (api
              {:formatter :muuntaja}
              (swagger-routes)
              burger-routes
              (POST "/pizza" []
                :return Pizza
                :body [body Pizza]
                (ok body)))]

    (testing "direct route with nested named schema works when called"
      (let [pizza {:toppings [{:name "cheese"}]}
            [status body] (post* app "/pizza" (json-string pizza))]
        (is-200-status status)
        (is (= pizza body))))

    (testing "defroute*'d route with nested named schema works when called"
      (let [burger {:ingredients [{:name "beef"}, {:name "egg"}]}
            [status body] (post* app "/burger" (json-string burger))]
        (is-200-status status)
        (is (= burger body))))

    (testing "generates correct swagger-spec"
      (is (= #{:Topping :Pizza :Burger :Beef}
             (-> app get-spec :definitions keys set))))))

(deftest multiple-routes-with-same-path-and-method-in-same-file-test
  (let [app (api
              {:formatter :muuntaja}
              (swagger-routes)
              (GET "/ping" []
                :summary "active-ping"
                (ok {:ping "active"}))
              (GET "/ping" []
                :summary "passive-ping"
                (ok {:ping "passive"})))]

    (testing "first route matches with Compojure"
      (let [[status body] (get* app "/ping" {})]
        (is-200-status status)
        (is (= {:ping "active"} body))))

    (testing "generates correct swagger-spec"
      (is (= "active-ping" (-> app get-spec :paths vals first :get :summary))))))

(deftest multiple-routes-with-same-path-and-method-over-context-test
  (let [app (api
              {:formatter :muuntaja}
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

    (testing "first route matches with Compojure"
      (let [[status body] (get* app "/api/ipa/ping" {})]
        (is-200-status status)
        (is (= {:ping "active"} body))))

    (testing "generates correct swagger-spec"
      (is (= "active-ping" (-> app get-spec :paths vals first :get :summary))))))

;; multiple routes with same overall path (with different path sniplets & method over context)
(deftest multiple-routes-with-same-overall-path-test
  (let [app (api
              {:formatter :muuntaja}
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

    (testing "first route matches with Compojure"
      (let [[status body] (get* app "/api/ipa/ping" {})]
        (is-200-status status)
        (is (= {:ping "active"} body))))

    (testing "generates correct swagger-spec"
      (is (= "active-ping" (-> app get-spec :paths vals first :get :summary))))))

; https://github.com/metosin/compojure-api/issues/98
; https://github.com/metosin/compojure-api/issues/134
(deftest basePath-test
  (let [app (api
              {:formatter :muuntaja}
              (swagger-routes))]

    (testing "no context"
      (is (= "/" (-> app get-spec :basePath))))

    (testing "app-servers with given context"
      (with-redefs [rsc/context (fn [& args] "/v2")]
        (is (= "/v2" (-> app get-spec :basePath))))))

  (let [app (api
              {:formatter :muuntaja}
              (swagger-routes {:data {:basePath "/serve/from/here"}}))]
    (testing "override it"
      (is (= "/serve/from/here" (-> app get-spec :basePath)))))

  (let [app (api
              {:formatter :muuntaja}
              (swagger-routes {:data {:basePath "/"}}))]
    (testing "can set it to the default"
      (is (= "/" (-> app get-spec :basePath))))))

(deftest multiple-different-models-with-same-name-test

  (testing "schemas with same regexps are not equal"
    (is (not= {:d #"\D"} {:d #"\D"})))

  (testing "api-spec with 2 schemas with non-equal contents"
    (let [app (api
                {:formatter :muuntaja}
                (swagger-routes)
                (GET "/" []
                  :responses {200 {:schema (s/schema-with-name {:a {:d #"\D"}} "Kikka")}
                              201 {:schema (s/schema-with-name {:a {:d #"\D"}} "Kikka")}}
                  identity))]
      (testing "api spec doesn't fail (#102)"
        (is (get-spec app))))))

(def over-the-hills-and-far-away
  (POST "/" []
    :body-params [a :- s/Str]
    identity))

(deftest anonymous-body-models-over-defined-routes-test
  (let [app (api
              {:formatter :muuntaja}
              (swagger-routes)
              over-the-hills-and-far-away)]
    (testing "generated model doesn't have namespaced keys"
      (is (= :a (-> app get-spec :definitions vals first :properties keys first))))))

(def foo
  (GET "/foo" []
    (let [foo {:foo "bar"}]
      (ok foo))))

;;defroutes with local symbol usage with same name (#123)
(deftest defroutes-with-local-symbol-usage-with-same-name-test
  (let [app (api
              {:formatter :muuntaja}
              foo)]
    (let [[status body] (get* app "/foo")]
      (is-200-status status)
      (is (= {:foo "bar"} body)))))

(def response-descriptions-routes
  (GET "/x" []
    :responses {500 {:schema {:code String}
                     :description "Horror"}}
    identity))

(deftest response-descriptions-test
  (let [app (api
              {:formatter :muuntaja}
              (swagger-routes)
              response-descriptions-routes)]
    (is (= "Horror" (-> app get-spec :paths vals first :get :responses :500 :description)))))

(deftest exceptions-options-with-custom-validation-error-handler-test
  (let [app (api
              {:formatter :muuntaja
               :exceptions {:handlers {::ex/request-validation custom-validation-error-handler
                                       ::ex/request-parsing custom-validation-error-handler
                                       ::ex/response-validation custom-validation-error-handler}}}
              (swagger-routes)
              (POST "/get-long" []
                :body [body {:x Long}]
                :return Long
                (case (:x body)
                  1 (ok 1)
                  (ok "not a number"))))]

    (testing "return case, valid request & valid model"
      (let [[status body] (post* app "/get-long" "{\"x\": 1}")]
        (is-200-status status)
        (is (= 1 body))))

    (testing "return case, not schema valid request"
      (let [[status body] (post* app "/get-long" "{\"x\": \"1\"}")]
        (is (= 400 status))
        (is (= "/get-long" (:custom-error body)))))

    (testing "return case, invalid json request"
      (let [[status body] (post* app "/get-long" "{x: 1}")]
        (is (= 400 status))
        (is (= "/get-long" (:custom-error body)))))

    (testing "return case, valid request & invalid model"
      (let [[status body] (post* app "/get-long" "{\"x\": 2}")]
        (is (= 501 status))
        (is (= "/get-long" (:custom-error body)))))))

(deftest exceptions-options-with-custom-exception-and-error-handler-test
  (let [app (api
              {:formatter :muuntaja
               :exceptions {:handlers {::ex/default (custom-exception-handler :custom-exception)
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

    (testing "uses default exception handler for unknown exceptions"
      (let [[status body] (get* app "/some-exception")]
        (is-200-status status)
        (is (= {:custom-exception "java.lang.RuntimeException"} body))))

    (testing "uses default exception handler for unknown errors"
      (let [[status body] (get* app "/some-error")]
        (is-200-status status)
        (is (str/includes? (:custom-exception body) ":data \"some error\"" ))))

    (testing "uses specific error handler for ::custom-errors"
      (let [[_ body] (get* app "/specific-error")]
        (is (= {:custom-error "my error"} body))))

    (testing "direct class"
      (let [[_ body] (get* app "/class")]
        (is (= "java.sql.SQLException" (:sql-exception body)))))

    (testing "sub-class"
      (let [[_ body] (get* app "/sub-class")]
        (is (= "java.sql.SQLWarning" (:sql-exception body)))))))

(deftest exception-handling-can-be-disabled-test
  (let [app (api
              {:formatter :muuntaja
               :exceptions nil}
              (GET "/throw" []
                (throw (new RuntimeException))))]
    (is (thrown? RuntimeException (get* app "/throw")))))

(s/defn schema-error [a :- s/Int]
  {:bar a})

;; handling schema.core/error
(deftest handling-schema-core-error-test
  (let [app (api
              {:formatter :muuntaja
               :exceptions {:handlers {:schema.core/error ex/schema-error-handler}}}
              (GET "/:a" []
                :path-params [a :- s/Str]
                (ok (s/with-fn-validation (schema-error a)))))]
    (let [[status body] (get* app "/foo")]
      (is (= 400 status))
      (is (-> body :errors vector?)))))

(deftest ring-swagger-options-test
  (let [app (api
              {:formatter :muuntaja
               :ring-swagger {:default-response-description-fn status/get-description}}
              (swagger-routes)
              (GET "/ping" []
                :responses {500 nil}
                identity))]
    (is (= "There was an internal server error." (-> app get-spec :paths vals first :get :responses :500 :description)))))

(deftest path-for-test
  (testing "simple case"
    (let [app (api
                {:formatter :muuntaja}
                (GET "/api/pong" []
                  :name :pong
                  (ok {:pong "pong"}))
                (GET "/api/ping" []
                  (moved-permanently (path-for :pong))))]
      (testing "path-for works"
        (let [[status body] (get* app "/api/ping" {})]
          (is-200-status status)
          (is (= {:pong "pong"} body))))))

  (testing "with path parameters"
    (let [app (api
                {:formatter :muuntaja}
                (GET "/lost-in/:country/:zip" []
                  :name :lost
                  :path-params [country :- (s/enum :FI :EN), zip :- s/Int]
                  (ok {:country country
                       :zip zip}))
                (GET "/api/ping" []
                  (moved-permanently
                    (path-for :lost {:country :FI, :zip 33200}))))]
      (testing "path-for resolution"
        (let [[status body] (get* app "/api/ping" {})]
          (is-200-status status)
          (is (= {:country "FI" :zip 33200} body))))))

  (testing "https://github.com/metosin/compojure-api/issues/150"
    (let [app (api
                {:formatter :muuntaja}
                (GET "/companies/:company-id/refresh" []
                  :path-params [company-id :- s/Int]
                  :name :refresh-company
                  :return String
                  (ok (path-for :refresh-company {:company-id company-id}))))]
      (testing "path-for resolution"
        (let [[status body] (get* app "/companies/4/refresh")]
          (is-200-status status)
          (is (= "/companies/4/refresh" body))))))

  (testing "multiple routes with same name fail at compile-time"
    (let [app' `(api
                  {:formatter :muuntaja}
                  (GET "/api/pong" []
                    :name :pong
                    identity)
                  (GET "/api/ping" []
                    :name :pong
                    identity))]
      (is (thrown? RuntimeException (eval app')))))

  (testing "bindings with wrong syntax should fail nicely"
    (let [app' `(api
                  {:formatter :muuntaja}
                  (GET "/api/:id/pong" []
                    :path-params [id ::id]
                    :name :pong
                    identity))]
      (is (thrown? RuntimeException (eval app'))))))

(deftest swagger-spec-path-test
  (testing "defaults to /swagger.json"
    (let [app (api
                {:formatter :muuntaja}
                (swagger-routes))]
      (is (= "/swagger.json" (swagger/swagger-spec-path app)))))
  (testing "follows defined path"
    (let [app (api
                {:formatter :muuntaja}
                (swagger-routes {:spec "/api/api-docs/swagger.json"}))]
      (is (= "/api/api-docs/swagger.json" (swagger/swagger-spec-path app))))))

(defrecord NonSwaggerRecord [data])

(deftest api-validation-test

  (testing "a swagger api with valid swagger records"
    (let [app (api
                {:formatter :muuntaja}
                (swagger-routes)
                (GET "/ping" []
                  :return {:data s/Str}
                  (ok {:data "ping"})))]

      (testing "works"
        (let [[status body] (get* app "/ping")]
          (is-200-status status)
          (is (= {:data "ping"} body))))

      (testing "the api is valid"
        (is (= app (validator/validate app))))))

  (testing "a swagger api with invalid swagger records"
    (let [app (api
                {:formatter :muuntaja}
                (swagger-routes)
                (GET "/ping" []
                  :return NonSwaggerRecord
                  (ok (->NonSwaggerRecord "ping"))))]

      (testing "works"
        (let [[status body] (get* app "/ping")]
          (is-200-status status)
          (is (= {:data "ping"} body))))

      (testing "the api is invalid"
        (is (thrown-with-msg?
              IllegalArgumentException
              #"don't know how to convert class compojure.api.integration_test.NonSwaggerRecord into a Swagger Schema. Check out ring-swagger docs for details."
              (validator/validate app))))))

  (testing "a non-swagger api with invalid swagger records"
    (let [app (api
                {:formatter :muuntaja}
                (GET "/ping" []
                  :return NonSwaggerRecord
                  (ok (->NonSwaggerRecord "ping"))))]

      (testing "works"
        (let [[status body] (get* app "/ping")]
          (is-200-status status)
          (is (= {:data "ping"} body))))

      (testing "the api is valid"
        (is (= app (validator/validate app)))))))

(deftest component-integration-test
  (let [system {:magic 42}]
    (testing "via options"
      (let [app (api
                  {:formatter :muuntaja
                   :components system}
                  (GET "/magic" []
                    :components [magic]
                    (ok {:magic magic})))]
        (let [[status body] (get* app "/magic")]
          (is-200-status status)
          (is (= {:magic 42} body)))))

    (testing "via middleware"
      (let [handler (api
                      {:formatter :muuntaja}
                      (GET "/magic" []
                        :components [magic]
                        (ok {:magic magic})))
            app (mw/wrap-components handler system)]
        (let [[status body] (get* app "/magic")]
          (is-200-status status)
          (is (= {:magic 42} body)))))))

(deftest sequential-string-parameters-test
  (let [app (api
              {:formatter :muuntaja}
              (GET "/ints" []
                :query-params [i :- [s/Int]]
                (ok {:i i})))]
    (testing "multiple values"
      (let [[status body] (get* app "/ints?i=1&i=2&i=3")]
        (is-200-status status)
        (is (= {:i [1, 2, 3]} body))))
    (testing "single value"
      (let [[status body] (get* app "/ints?i=42")]
        (is-200-status status)
        (is (= {:i [42]} body))))))

(deftest swagger-params-just-for-documentation-test
  (testing "compile-time values"
    (let [app (api
                {:formatter :muuntaja}
                (swagger-routes)
                (GET "/route" [q]
                  :swagger {:x-name :boolean
                            :operationId "echoBoolean"
                            :description "Echoes a boolean"
                            :parameters {:query {:q s/Bool}}}
                  (ok {:q q})))]

      (testing "there is no coercion"
        (let [[status body] (get* app "/route" {:q "kikka"})]
          (is-200-status status)
          (is (= {:q "kikka"} body))))

      (testing "swagger-docs are generated"
        (is (= {:x-name "boolean"
                :operationId "echoBoolean"
                :description "Echoes a boolean"
                :parameters [{:description ""
                              :in "query"
                              :name "q"
                              :required true
                              :type "boolean"}]}
               (-> app get-spec :paths vals first :get
                   (select-keys [:x-name :operationId :description :parameters])))))))
  (testing "run-time values"
    (let [runtime-data {:x-name :boolean
                        :operationId "echoBoolean"
                        :description "Echoes a boolean"
                        :parameters {:query {:q s/Bool}}}
          app (api
                {:formatter :muuntaja}
                (swagger-routes)
                (GET "/route" [q]
                  :swagger runtime-data
                  (ok {:q q})))]

      (testing "there is no coercion"
        (let [[status body] (get* app "/route" {:q "kikka"})]
          (is-200-status status)
          (is (= {:q "kikka"} body))))

      (testing "swagger-docs are generated"
        (is (= {:x-name "boolean"
                :operationId "echoBoolean"
                :description "Echoes a boolean"
                :parameters [{:description ""
                              :in "query"
                              :name "q"
                              :required true
                              :type "boolean"}]}
               (-> app get-spec :paths vals first :get
                   (select-keys [:x-name :operationId :description :parameters]))))))))

;; swagger-docs via api options, #218
(deftest swagger-docs-via-api-options
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
        api1 (api {:formatter :muuntaja
                   :swagger {:spec "/swagger.json", :ui "/"}} routes)
        api2 (api {:formatter :muuntaja} (swagger-routes) routes)]

    (testing "both generate same swagger-spec"
      (is (= (get-spec api1) (get-spec api2))))

    (testing "not-found handler works"
      (is (= {:message "404"} (second (get* api1 "/missed"))))
      (is (= {:message "404"} (second (get* api2 "/missed")))))))

;; more swagger-data can be (deep-)merged in - either via swagger-docs at runtime via mws, fixes #170
(deftest issue-170-test
  (let [app (api
              {:formatter :muuntaja}
              (route-middleware [[rsm/wrap-swagger-data {:paths {"/runtime" {:get {}}}}]]
                (swagger-routes
                  {:data
                   {:info {:version "2.0.0"}
                    :paths {"/extra" {:get {}}}}})
                (GET "/normal" [] (ok))))]
    (is (= #{"/normal" "/extra" "/runtime"}
           (-> (get-spec app) :paths keys set)))))


(deftest handling-invalid-routes-with-api-test
  (let [invalid-routes (routes (constantly nil))]

    (testing "by default, logs the exception"
      (let [a (atom [])]
        (with-redefs [compojure.api.impl.logging/log! (fn [& args] (swap! a conj args))]
          (is (api {:formatter :muuntaja} invalid-routes)))
        (is (= [:warn] (map first @a)))))

    (testing "ignoring invalid routes doesn't log"
      (let [a (atom [])]
        (with-redefs [compojure.api.impl.logging/log! (fn [& args] (swap! a conj args))]
          (is (api {:formatter :muuntaja, :api {:invalid-routes-fn nil}} invalid-routes)))
        (is (empty? @a))))

    (testing "throwing exceptions"
      (is (thrown? Exception (api {:formatter :muuntaja
                                   :api {:invalid-routes-fn routes/fail-on-invalid-child-routes}}
                                  invalid-routes))))))

(deftest using-local-symbols-for-restructuring-params-test
  (let [responses {400 {:schema {:fail s/Str}}}
        app (api
              {:formatter :muuntaja
               :swagger {:spec "/swagger.json"
                         :data {:info {:version "2.0.0"}}}}
              (GET "/a" []
                :responses responses
                :return {:ok s/Str}
                (ok))
              (GET "/b" []
                :responses (assoc responses 500 {:schema {:m s/Str}})
                :return {:ok s/Str}
                (ok)))
        paths (:paths (get-spec app))
        a-resp (get-in paths ["/a" :get :responses])
        b-resp (get-in paths ["/b" :get :responses])]

    (is (= #{:200 :400} (-> a-resp keys set)))
    (is (= #{:schema :description} (-> a-resp :400 keys set)))
    (is (= #{:schema :description} (-> a-resp :200 keys set)))
    (is (= "" (-> a-resp :400 :description)))
    (is (= "" (-> a-resp :200 :description)))

    (is (= #{:200 :400 :500} (-> b-resp keys set)))
    (is (= #{:schema :description} (-> b-resp :500 keys set)))
    (is (= #{:schema :description} (-> b-resp :400 keys set)))
    (is (= #{:schema :description} (-> b-resp :200 keys set)))
    (is (= "" (-> b-resp :500 :description)))
    (is (= "" (-> b-resp :400 :description)))
    (is (= "" (-> b-resp :200 :description)))))

(deftest when-functions-are-returned-test
  (let [wrap-mw-params (fn [handler value]
                         (fn [request]
                           (handler
                             (update request ::mw #(str % value)))))]
    (testing "from endpoint"
      (let [app (GET "/ping" []
                  :middleware [[wrap-mw-params "1"]]
                  :query-params [{a :- s/Str "a"}]
                  (fn [req] (str (::mw req) a)))]

        (is (= "1a" (:body (app {:request-method :get, :uri "/ping", :query-params {}}))))
        (is (= "1A" (:body (app {:request-method :get, :uri "/ping", :query-params {:a "A"}}))))))

    (testing "from endpoint under context"
      (let [app (context "/api" []
                  :middleware [[wrap-mw-params "1"]]
                  :query-params [{a :- s/Str "a"}]
                  (GET "/ping" []
                    :middleware [[wrap-mw-params "2"]]
                    :query-params [{b :- s/Str "b"}]
                    (fn [req] (str (::mw req) a b))))]

        (is (= "12ab" (:body (app {:request-method :get, :uri "/api/ping", :query-params {}}))))
        (is (= "12Ab" (:body (app {:request-method :get, :uri "/api/ping", :query-params {:a "A"}}))))
        (is (= "12AB" (:body (app {:request-method :get, :uri "/api/ping", :query-params {:a "A", :b "B"}}))))))))

(defn check-for-response-handler
  "This response-validation handler checks for the existence of :response in its input. If it's there, it
  returns status 200, including the value that was origingally returned. Otherwise it returns 404."
  [^Exception e data request]
  (if (:response data)
    (ok {:message "Found :response in data!" :attempted-body (get-in data [:response :body])})
    (not-found "No :response key present in data!")))

(deftest response-validation-handler-has-access-to-response-value-that-failed-coercion-test
  (let [incorrect-return-value {:incorrect "response"}
        app (api
              {:formatter :muuntaja
               :exceptions {:handlers {::ex/response-validation check-for-response-handler}}}
              (swagger-routes)
              (GET "/test-response" []
                :return {:correct s/Str}
                ; This should fail and trigger our error handler
                (ok incorrect-return-value)))]

    (testing "return case, valid request & valid model"
      (let [[status body] (get* app "/test-response")]
        (is-200-status status)
        (is (= incorrect-return-value (:attempted-body body)))))))

;; "correct swagger parameter order with small number or parameters, #224"
(deftest issue-224-test
  (let [app (api
              {:formatter :muuntaja}
              (swagger-routes)
              (GET "/ping" []
                :query-params [a b c d e]
                (ok {:a a, :b b, :c c, :d d, :e e})))]
    (testing "api works"
      (let [[status body] (get* app "/ping" {:a "A" :b "B" :c "C" :d "D" :e "E"})]
        (is-200-status status)
        (is (= {:a "A" :b "B" :c "C" :d "D" :e "E"} body))))
    (testing "swagger parameters are in correct order"
      (is (= [:a :b :c :d :e]
             (-> app get-spec :paths (get "/ping") :get :parameters (->> (map (comp keyword :name)))))))))

;; empty top-level route, #https://github.com/metosin/ring-swagger/issues/92
(deftest issue-92-test 
  (let [app (api
              {:formatter :muuntaja
               :swagger {:spec "/swagger.json"}}
              (GET "/" [] (ok {:kikka "kukka"})))]
    (testing "api works"
      (let [[status body] (get* app "/")]
        (is-200-status status)
        (is (= {:kikka "kukka"} body))))
    (testing "swagger docs"
      (is (= ["/"] (-> app get-spec :paths keys))))))

;; describe works on anonymous bodys, #168
(deftest issue-168-test
  (let [app (api
              {:formatter :muuntaja}
              (swagger-routes)
              (POST "/" []
                :body [body (describe {:kikka [{:kukka String}]} "kikkas")]
                (ok body)))]
    (testing "description is in place"
      (is (= "kikkas" (-> app get-spec :paths (get "/") :post :parameters first :description))))))

;; swagger responses headers are mapped correctly, #232
(deftest issue-232-test
  (let [app (api
              {:formatter :muuntaja}
              (swagger-routes)
              (context "/resource" []
                (resource
                  {:get {:responses {200 {:schema {:size s/Str}
                                          :description "size"
                                          :headers {"X-men" (describe s/Str "mutant")}}}}})))]
    (is (= {:X-men {:description "mutant", :type "string"}}
           (-> app get-spec :paths vals first :get :responses :200 :headers)))))

(deftest api-middleware-can-be-disabled-test
  (let [app (api
              {:formatter :muuntaja
               :api {:disable-api-middleware? true}}
              (swagger-routes)
              (GET "/params" [x] (ok {:x x}))
              (GET "/throw" [] (throw (RuntimeException. "kosh"))))]

    (testing "json-parsing & wrap-params is off"
      (let [[status body] (raw-get* app "/params" {:x 1})]
        (is-200-status status)
        (is (= {:x nil} body))))

    (testing "exceptions are not caught"
      (is (thrown? Exception (raw-get* app "/throw"))))))

;"custom formats contribute to Swagger :consumes & :produces"
(deftest custom-formats-contribute-to-Swagger-consumes-produces-test
  (let [custom-type "application/vnd.vendor.v1+json"
        app (api
              {:swagger {:spec "/swagger.json"}
               :formats (-> m/default-options
                            (m/install muuntaja.format.json/format custom-type)
                            (m/select-formats ["application/json" custom-type]))}
              (POST "/echo" []
                :body [data {:kikka s/Str}]
                (ok data)))]

    (testing "it works"
      (let [response (app {:uri "/echo"
                           :request-method :post
                           :body (json-stream {:kikka "kukka"})
                           :headers {"content-type" "application/vnd.vendor.v1+json"
                                     "accept" "application/vnd.vendor.v1+json"}})]

        (is (= (json-string {:kikka "kukka"}) (-> response :body slurp)))
        (is (= "application/vnd.vendor.v1+json; charset=utf-8"
               (-> response :headers (get "Content-Type"))))))

    (testing "spec is correct"
      (let [res (get-spec app)]
        (is (= (sort ["application/vnd.vendor.v1+json" "application/json"])
               (-> res :produces sort)))
        (is (= (sort ["application/vnd.vendor.v1+json" "application/json"])
               (-> res :consumes sort)))))))

(deftest muuntaja-is-bound-in-request-test
  (let [app (api
              {:formatter :muuntaja}
              (GET "/ping" {:keys [::request/muuntaja]}
                (ok {:pong (slurp (m/encode muuntaja "application/json" {:is "json"}))})))]

    (let [[status body] (get* app "/ping")]
      (is-200-status status)
      (is (= {:pong "{\"is\":\"json\"}"} body)))))

(deftest body-doesnt-keywordize-keys-test
  (let [m (m/create)
        data {:items {"kikka" 42}}
        body* (atom nil)
        app (api
              {:formatter :muuntaja}
              (swagger-routes)
              (POST "/echo" []
                :body-params [items :- {:kikka Long}]
                (reset! body* {:items items})
                (ok))
              (POST "/echo2" []
                :body [body {:items {(s/required-key "kikka") Long}}]
                (reset! body* body)
                (ok)))]

    (testing ":body-params keywordizes params"
      (is (http/ok? (app {:uri "/echo"
                          :request-method :post
                          :body (m/encode m "application/transit+json" data)
                          :headers {"content-type" "application/transit+json"
                                    "accept" "application/transit+json"}})))
       (is (= {:items {:kikka 42}} @body*)))

    (testing ":body does not keywordizes params"
      (is (http/ok? (app {:uri "/echo2"
                          :request-method :post
                          :body (m/encode m "application/transit+json" data)
                          :headers {"content-type" "application/transit+json"
                                    "accept" "application/transit+json"}})))
      (is (= {:items {"kikka" 42}} @body*)))

    (testing "swagger spec is generated both ways"
      (let [spec (get-spec app)
            echo-schema-name (-> (get-in spec [:paths "/echo" :post :parameters 0 :name])
                                 name (str "Items") keyword)
            echo2-schema-name (-> (get-in spec [:paths "/echo2" :post :parameters 0 :name])
                                  name (str "Items") keyword)
            echo-schema (get-in spec [:definitions echo-schema-name :properties])
            echo2-schema (get-in spec [:definitions echo2-schema-name :properties])]
        (is (= {:kikka {:type "integer", :format "int64"}} echo-schema))
        (is (= {:kikka {:type "integer", :format "int64"}} echo2-schema))))))


(def ^:dynamic *response* nil)

(deftest format-based-body-and-response-coercion-test
  (let [m (mw/create-muuntaja)]

    (testing "application/transit & application/edn validate request & response (no coercion)"
      (let [valid-data {:items {"kikka" :kukka}}
            invalid-data {"items" {"kikka" :kukka}}
            Schema {:items {(s/required-key "kikka") s/Keyword}}
            app (api
                  {:formatter :muuntaja}
                  (POST "/echo" []
                    :body [_ Schema]
                    :return Schema
                    (ok *response*)))]

        (doseq [format ["application/transit+json" "application/edn"]]
          (testing format

            (testing "fails with invalid body"
              (is (http/bad-request? (app (ring-request m format invalid-data)))))

            (testing "fails with invalid response"
              (binding [*response* invalid-data]
                (is (http/internal-server-error? (app (ring-request m format valid-data))))))

            (testing "succeeds with valid body & response"
              (binding [*response* valid-data]
                (let [response (app (ring-request m format valid-data))]
                  (is (http/ok? response))
                  (is (= valid-data (m/decode m format (:body response)))))))))))

    (testing "application/json - coerce request, validate response"
      (let [valid-data {:int 1, :keyword "kikka"}
            valid-response-data {:int 1, :keyword :kikka}
            invalid-data {:int "1", :keyword "kikka"}
            Schema {:int s/Int, :keyword s/Keyword}
            app (api
                  {:formatter :muuntaja}
                  (POST "/echo" []
                    :body [_ Schema]
                    :return Schema
                    (ok *response*)))]

        (doseq [format ["application/json"]]
          (testing format

            (testing "fails with invalid body"
              (is (http/bad-request? (app (ring-request m format invalid-data)))))

            (testing "fails with invalid response"
              (binding [*response* invalid-data]
                (is (http/internal-server-error? (app (ring-request m format valid-data))))))

            (testing "does not coerce response"
              (binding [*response* valid-data]
                (is (http/internal-server-error? (app (ring-request m format valid-data))))))

            (testing "succeeds with valid body & response"
              (binding [*response* valid-response-data]
                (let [response (app (ring-request m format valid-data))]
                  (is (http/ok? response))
                  (is (= valid-data (m/decode m format (:body response)))))))))))))

(deftest static-contexts-just-work-test
  (let [app (context "/:a" [a]
              (GET "/:b" [b]
                (ok [a b])))]
    (is (= ["a" "b"] (:body (app {:request-method :get, :uri "/a/b"}))))
    (is (= ["b" "c"] (:body (app {:request-method :get, :uri "/b/c"}))))))

(deftest file-responses-dont-get-coerced-test
  (let [app (api
              {:formatter :muuntaja}
              (swagger-routes)
              (GET "/file" []
                :return File
                (ok (io/file "project.clj"))))]
    (let [{:keys [status body]} (app {:uri "/file", :request-method :get})]
      (is-200-status status)
      (is (instance? File body)))))

(deftest nil-routes-are-ignored-test
  (let [create-app (fn [{:keys [dev?]}]
                     (context "/api" []
                       (GET "/ping" [] (ok))
                       (context "/db" []
                         (if dev?
                           (GET "/drop" [] (ok))))
                       (if dev?
                         (context "/dev" []
                           (GET "/tools" [] (ok))))))]

    (testing "with routes"
      (let [app (create-app {:dev? true})]
        (is (http/ok? (app {:request-method :get, :uri "/api/ping"})))
        (is (http/ok? (app {:request-method :get, :uri "/api/db/drop"})))
        (is (http/ok? (app {:request-method :get, :uri "/api/dev/tools"})))))

    (testing "without routes"
      (let [app (create-app {:dev? false})]
        (is (http/ok? (app {:request-method :get, :uri "/api/ping"})))
        (is (nil? (app {:request-method :get, :uri "/api/db/drop"})))
        (is (nil? (app {:request-method :get, :uri "/api/dev/tools"})))))))

(deftest wrap-routes-test
  (testing "simple middleware"
    (let [called? (atom false)
          app (api
                {:formatter :muuntaja}
                (route-middleware
                  [(fn [handler]
                     (fn [req]
                       (reset! called? true)
                       (handler req)))]
                  (GET "/a" []
                    (ok {:ok true})))
                (GET "/b" []
                  (ok {:ok true})))
          response (app {:uri "/a"
                         :request-method :get})]
      (is (= (json-string {:ok true}) (-> response :body slurp)))
      (testing "middleware is called"
        (is @called?))

      (reset! called? false)
      (let [response (app {:uri "/b"
                           :request-method :get})]
        (is (= (json-string {:ok true}) (-> response :body slurp)))
        (is (not @called?)))))

  (testing "middleware with args"
    (let [mw-value (atom nil)
          app (api
                {:formatter :muuntaja}
                (route-middleware
                  [[(fn [handler value]
                      (fn [req]
                        (reset! mw-value value)
                        (handler req)))
                    :foo-bar]]
                  (GET "/a" []
                    (ok {:ok true})))
                (GET "/b" []
                  (ok {:ok true})))
          response (app {:uri "/a"
                         :request-method :get})]
      (is (= (json-string {:ok true}) (-> response :body slurp)))
      (testing "middleware is called"
        (is (= :foo-bar @mw-value)))

      (reset! mw-value nil)
      (let [response (app {:uri "/b"
                           :request-method :get})]
        (is (= (json-string {:ok true}) (-> response :body slurp)))
        (is (nil? @mw-value))))))

(deftest ring-handler-test
  (let [app (api
              {:formatter :muuntaja}
              (GET "/ping" [] (ok)))
        ring-app (c/ring-handler app)]
    (testing "both work"
      (is (some #{200} (get* app "/ping")))
      (is (some #{200} (get* ring-app "/ping"))))
    (testing "ring-app is also a Fn"
      (is (not (fn? app)))
      (is (fn? ring-app)))))

(deftest body-params-are-set-to-params-test
  (let [app (api
              {:formatter :muuntaja}
              (POST "/echo" [x] (ok {:x x})))
        [status body] (post* app "/echo" (json-string {:x 1}))]
    (is-200-status status)
    (is (= {:x 1} body))))

;; #306 & #313"
(deftest body-in-error-handling-test
  (let [app (api
              {:formatter :muuntaja
               :exceptions
               {:handlers
                {:compojure.api.exception/default
                 (fn [_ _ request]
                   (internal-server-error (:body-params request)))}}}
              (POST "/error" []
                (throw (RuntimeException. "error"))))
        [status body] (post* app "/error" (json-string {:kikka 6}))]
    (is (= 500 status))
    (is (= {:kikka 6} body))))

(deftest sequential-routes-test

  (testing "context"
    (let [app (api
                {:formatter :muuntaja}
                (context "/api" []
                  (for [path ["/ping" "/pong"]]
                    (GET path [] (ok {:path path})))))]

      (testing "all routes can be invoked"
        (let [[status body] (get* app "/api/ping")]
          (is-200-status status)
          (is (= {:path "/ping"} body)))

        (let [[status body] (get* app "/api/pong")]
          (is-200-status status)
          (is (= {:path "/pong"} body))))))

  (testing "routes"
    (let [app (api
                {:formatter :muuntaja}
                (routes
                  (for [path ["/ping" "/pong"]]
                    (GET path [] (ok {:path path})))))]

      (testing "all routes can be invoked"
        (let [[status body] (get* app "/ping")]
          (is-200-status status)
          (is (= {:path "/ping"} body)))

        (let [[status body] (get* app "/pong")]
          (is-200-status status)
          (is (= {:path "/pong"} body)))))))

(deftest wrap-format-issue-374-test
  (let [data {:war "hammer"}]

    (testing "first api consumes the body"
      (let [app (routes
                  (api
                    {:formatter :muuntaja}
                    (POST "/echo1" []
                      :body [body s/Any]
                      (ok body)))
                  (api
                    {:formatter :muuntaja}
                    (POST "/echo2" []
                      :body [body s/Any]
                      (ok body))))]

        (testing "first api sees the body"
          (let [[status body] (post* app "/echo1" (json-string data))]
            (is-200-status status)
            (is (= data body))))

        (testing "second api fails"
          (let [[status] (post* app "/echo2" (json-string data))]
            (is (= 400 status)))))

      (testing "wrap-format with defaults"
        (let [app (-> (routes
                        (api
                          {:formatter :muuntaja}
                          (POST "/echo1" []
                            :body [body s/Any]
                            (ok body)))
                        (api
                          {:formatter :muuntaja}
                          (POST "/echo2" []
                            :body [body s/Any]
                            (ok body))))
                      (mw/wrap-format))]

          (testing "first api sees the body"
            (let [[status body] (post* app "/echo1" (json-string data))]
              (is-200-status status)
              (is (= data body))))

          (testing "second api sees it too!"
            (let [[status body] (post* app "/echo2" (json-string data))]
              (is-200-status status)
              (is (= data body))))))

      (testing "wrap-format with configuration"
        (let [muuntaja (m/create
                         (m/select-formats
                           m/default-options
                           ["application/json"]))
              app (-> (routes
                        (api
                          {:formats nil
                           :swagger {:spec "/swagger1.json"}}
                          (POST "/echo1" []
                            :body [body s/Any]
                            (ok body)))
                        (api
                          {:formats nil
                           :swagger {:spec "/swagger2.json"}}
                          (POST "/echo2" []
                            :body [body s/Any]
                            (ok body))))
                      (mw/wrap-format
                        {:formats muuntaja}))]

          (testing "first api sees the body"
            (let [[status body] (post* app "/echo1" (json-string data))]
              (is-200-status status)
              (is (= data body))))

          (testing "second api sees it too!"
            (let [[status body] (post* app "/echo2" (json-string data))]
              (is-200-status status)
              (is (= data body))))

          (testing "top-level muuntaja effect both"
            (let [[status body] (get* app "/swagger1.json")]
              (is-200-status status)
              (is (= {:produces ["application/json"]
                      :consumes ["application/json"]}
                     (select-keys body [:produces :consumes]))))
            (let [[status body] (get* app "/swagger2.json")]
              (is-200-status status)
              (is (= {:produces ["application/json"]
                      :consumes ["application/json"]}
                     (select-keys body [:produces :consumes]))))))))))

;;"2.* will fail fast with :format"
(deftest compojure-2x-will-fail-fast-with-format-test 
  (let [app' `(api {:format (m/create)})]
    (is (thrown? AssertionError (eval app')))))

(deftest Muuntaja-0-6-0-options-test
  (testing "new formats"
    (let [muuntaja (m/create
                     (-> m/default-options
                         (m/install muuntaja.format.msgpack/format)
                         (m/install muuntaja.format.yaml/format)))
          data {:it "works!"}
          app (api
                {:formats muuntaja}
                (POST "/echo" []
                  :body [body s/Any]
                  (ok body)))]
      (doseq [format ["application/json"
                      "application/edn"
                      "application/msgpack"
                      "application/x-yaml"
                      "application/transit+json"
                      "application/transit+msgpack"]]
        (testing (str "format " (pr-str format))
          (let [{:keys [status body]} (app (ring-request muuntaja format data))]
            (is-200-status status)
            (is (= data (m/decode muuntaja format body))))))))

  (testing "return types"
    (doseq [[return type] {:input-stream ByteArrayInputStream
                           :bytes (class (make-array Byte/TYPE 0))
                           :output-stream StreamableResponse}]
      (let [app (api
                  {:formats (assoc m/default-options :return return)}
                  (GET "/" []
                    (ok {:kikka "kukka"})))]
        (testing (str "return " (pr-str return))
          (let [{:keys [status body]} (app {:uri "/", :request-method :get})]
            (is-200-status status)
            (is (instance? type body))))))))
