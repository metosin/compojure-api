(ns compojure.api.core-integration-test
  (:require [midje.sweet :refer :all]
            [schema.core :as s]
            [compojure.api.swagger :as swagger]
            [ring.util.http-response :refer :all]
            [peridot.core :as p]
            [cheshire.core :as cheshire]
            [compojure.core :as compojure]
            [clojure.java.io :as io]
            [compojure.api.sweet :refer :all]
            [compojure.api.test-domain :as domain])
  (:import [java.io ByteArrayInputStream]))

;;
;; common
;;

(defn get* [app uri & [params headers]]
  (let [{{:keys [status body headers]} :response}
        (-> (p/session app)
            (p/request uri
                       :request-method :get
                       :params (or params {})
                       :headers (or headers {})))]
    [status (cheshire/parse-string body true) headers]))

(defn json [x] (cheshire/generate-string x))

(defn post* [app uri & [data]]
  (let [{{:keys [status body]} :response}
        (-> (p/session app)
            (p/request uri
                       :request-method :post
                       :content-type "application/json"
                       :body (.getBytes data)))]
    [status (cheshire/parse-string body true)]))

;;
;; Data
;;

(s/defschema User {:id   Long
                   :name String})

(def pertti {:id 1 :name "Pertti"})

(def invalid-user {:id 1 :name "Jorma" :age 50})

(def +name+ (str (gensym)))

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
  [handler & [res]]
  (fn [request]
    res))

(defn reply-mw*
  "Handler which replies with response where a header contains copy
   of the headers value from request and 7"
  [request]
  (-> (ok "true")
      (header mw* (str (get-in request [:headers mw*]) 7))))

;;
;; Facts
;;

(facts "middlewares"
  (defapi api
    (middlewares [middleware* (middleware* 2)]
      (swaggered +name+
        (context "/middlewares" []
          (GET* "/simple" req (reply-mw* req))
          (middlewares [(middleware* 3) (middleware* 4)]
            (GET* "/nested" req (reply-mw* req))
            (GET* "/nested-declared" req
              :middlewares [(middleware* 5) (middleware* 6)]
              (reply-mw* req)))))))

  (fact "are applied left-to-right"
    (let [[status body headers] (get* api "/middlewares/simple" {})]
      status => 200
      (get headers mw*) => "12721"))

  (fact "are applied left-to-right closest one first"
    (let [[status body headers] (get* api "/middlewares/nested" {})]
      status => 200
      (get headers mw*) => "123474321"))

  (fact "are applied left-to-right for both nested & declared cloest one first"
    (let [[status body headers] (get* api "/middlewares/nested-declared" {})]
      status => 200
      (get headers mw*) => "1234567654321")))

(facts "middlewares - multiple routes"
  (defapi api
    (swaggered +name+
      (GET* "/first" []
        (ok {:value "first"}))
      (GET* "/second" []
        :middlewares [(constant-middleware (ok {:value"foo"}))]
        (ok {:value "second"}))
      (GET* "/third" []
        (ok {:value "third"}))))
  (fact "first returns first"
    (let [[status body headers] (get* api "/first" {})]
      status => 200
      body => {:value "first"}))
  (fact "second returns foo"
    (let [[status body headers] (get* api "/second" {})]
      status => 200
      body => {:value "foo"}))
  (fact "third returns third"
    (let [[status body headers] (get* api "/third" {})]
      status => 200
      body => {:value "third"})))

(fact ":body, :query and :return"
  (defapi api
    (swaggered +name+
      (context "/models" []
        (GET* "/pertti" []
          :return User
          (ok pertti))
        (GET* "/user" []
          :return User
          :query  [user User]
          (ok user))
        (GET* "/invalid-user" []
          :return User
          (ok invalid-user))
        (GET* "/not-validated" []
          (ok invalid-user))
        (POST* "/user" []
          :return User
          :body   [user User]
          (ok user))
        (POST* "/user_list" []
          :return [User]
          :body   [users [User]]
          (ok users))
        (POST* "/user_set" []
          :return #{User}
          :body   [users #{User}]
          (ok users))
        (POST* "/user_legacy" {user :body-params}
          :return User
          (ok user)))))

  (fact "GET*"
    (let [[status body] (get* api "/models/pertti")]
      status => 200
      body => pertti))

  (fact "GET* with smart destructuring"
    (let [[status body] (get* api "/models/user" pertti)]
      status => 200
      body => pertti))

  (fact "POST* with smart destructuring"
    (let [[status body] (post* api "/models/user" (json pertti))]
      status => 200
      body => pertti))

  (fact "POST* with smart destructuring - lists"
    (let [[status body] (post* api "/models/user_list" (json [pertti]))]
      status => 200
      body => [pertti]))

  (fact "POST* with smart destructuring - sets"
    (let [[status body] (post* api "/models/user_set" (json #{pertti}))]
      status => 200
      body => [pertti]))

  (fact "POST* with compojure destructuring"
    (let [[status body] (post* api "/models/user_legacy" (json pertti))]
      status => 200
      body => pertti))

  (fact "Validation of returned data"
    (let [[status body] (get* api "/models/invalid-user")]
      status => 500))

  (fact "Routes without a :return parameter aren't validated"
    (let [[status body] (get* api "/models/not-validated")]
      status => 200
      body => invalid-user))

  (fact "Invalid json in body causes 400 with error message in json"
    (let [[status body] (post* api "/models/user" "{INVALID}")]
      status => 400
      (:type body) => "json-parse-exception"
      (:message body) => truthy)))

(fact ":responses"

  (fact "normal cases"
    (defapi api
      (swaggered +name+
        (GET* "/lotto/:x" []
          :return [Long]
          :path-params [x :- Long]
          :responses {403 [String]
                      440 [String]}
          (case x
            1 (ok [1])
            2 (ok ["two"])
            3 (forbidden ["error"])
            4 (forbidden [1])
            (not-found {:message "not-found"})))))

    (fact "return case"
      (let [[status body] (get* api "/lotto/1")]
        status => 200
        body => [1]))

    (fact "return case, non-matching model"
      (let [[status body] (get* api "/lotto/2")]
        status => 500
        body => (contains {:errors vector?})))

    (fact "error case"
      (let [[status body] (get* api "/lotto/3")]
        status => 403
        body => ["error"]))

    (fact "error case, non-matching model"
      (let [[status body] (get* api "/lotto/4")]
        status => 500
        body => (contains {:errors vector?})))

    (fact "returning non-predefined http-status code works"
      (let [[status body] (get* api "/lotto/5")]
        body => {:message "not-found"}
        status => 404)))

  (fact ":responses 200 and :return"
    (defapi api
      (swaggered +name+
        (GET* "/lotto/:x" []
          :path-params [x :- Long]
          :return {:return String}
          :responses {200 {:value String}}
          (case x
            1 (ok {:return "ok"})
            2 (ok {:value "ok"})))))

    (fact "return case"
      (let [[status body] (get* api "/lotto/1")]
        status => 500
        body => (contains {:errors {:return "disallowed-key"
                                    :value "missing-required-key"}})))

    (fact "return case"
      (let [[status body] (get* api "/lotto/2")]
        status => 200
        body => {:value "ok"})))

  (fact ":responses 200 and :return - other way around"
    (defapi api
      (swaggered +name+
        (GET* "/lotto/:x" []
          :path-params [x :- Long]
          :responses {200 {:value String}}
          :return {:return String}
          (case x
            1 (ok {:return "ok"})
            2 (ok {:value "ok"})))))

    (fact "return case"
      (let [[status body] (get* api "/lotto/1")]
        status => 200
        body => {:return "ok"}))

    (fact "return case"
      (let [[status body] (get* api "/lotto/2")]
        status => 500
        body => (contains {:errors {:return "missing-required-key"
                                    :value "disallowed-key"}})))))

(fact ":query-params, :path-params, :header-params & :body-params"
  (defapi api
    (swaggered +name+
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
          (ok {:total (- x y)})))))

  (fact "query-parameters"
    (let [[status body] (get* api "/smart/plus" {:x 2 :y 3})]
      status => 200
      body => {:total 5}))

  (fact "query-parameters"
    (let [[status body] (get* api "/smart/multiply/2/3")]
      status => 200
      body => {:total 6}))

  (fact "header-parameters"
    (let [[status body] (get* api "/smart/power" {} {:x 2 :y 3})]
      status => 200
      body => {:total 8}))

  (fact "body-parameters"
    (let [[status body] (post* api "/smart/minus" (json {:x 2 :y 3}))]
      status => 200
      body => {:total -1}))

  (fact "default parameters"
    (let [[status body] (post* api "/smart/minus" (json {:x 2}))]
      status => 200
      body => {:total 1})))

(fact "primitive support"
  (defapi api
    (swaggered +name+
      (context "/primitives" []
        (GET* "/return-long" []
          :return Long
          (ok 1))
        (GET* "/long" []
          (ok 1))
        (GET* "/return-string" []
          :return String
          (ok "kikka")))))

  (fact "when :return is set, longs can be returned"
    (let [[status body] (get* api "/primitives/return-long")]
      status => 200
      body => 1))

  (fact "when :return is not set, longs can't be returned"
    (get* api "/primitives/long") => (throws Exception))

  (fact "when :return is set, raw strings can be returned"
    (let [[status body] (get* api "/primitives/return-string")]
      status => 200
      body => "kikka")))

(fact "compojure destructuring support"
  (defapi api
    (swaggered +name+
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
               :b b})))))

  (doseq [uri ["regular" "regular2" "vector" "vector2" "symbol" "integrated"]]
    (fact {:midje/description uri}
      (let [[status body] (get* api (str "/destructuring/" uri) {:a "a" :b "b"})]
        status => 200
        body => {:a "a" :b "b"}))))



(fact "counting execution times, issue #19"
  (let [execution-times (atom 0)]
    (defapi api
      (swaggered +name+
        (GET* "/user" []
          :return User
          :query  [user User]
          (swap! execution-times inc)
          (ok user))))

    (fact "body is executed one"
      @execution-times => 0
      (let [[status body] (get* api "/user" pertti)]
        status => 200
        body => pertti)
      @execution-times => 1)))



(fact "swagger-docs"
  (defapi api
    (swagger-docs)
    (swaggered +name+
      (GET* "/user" []
        (continue))))

  (fact "api-listing"
    (let [[status body] (get* api "/api/api-docs" {})]
      status => 200
      body => {:swaggerVersion "1.2"
               :apiVersion "0.0.1"
               :info {}
               :apis [{:description ""
                       :path (str "/" +name+)}]}))

  (fact "api-docs"
    (let [[status body] (get* api (str "/api/api-docs/" +name+) {})]
      status => 200
      body => {:swaggerVersion "1.2"
               :apiVersion "0.0.1"
               :resourcePath "/"
               :models {}
               :basePath "http://localhost"
               :consumes ["application/json"]
               :produces ["application/json"]
               :apis [{:operations [{:method "GET"
                                     :nickname "getUser"
                                     :notes ""
                                     :parameters []
                                     :responseMessages []
                                     :summary ""
                                     :type "void"}]
                       :path "/user"}]})))

(fact "sub-context paths"
  (let [response {:ping "pong"}
        ok (ok response)
        ok? (fn [[status body]]
              (and (= status 200)
                   (= body response)))
        not-ok? (comp not ok?)]
    (defapi api
      (swagger-docs)
      (swaggered +name+
        (GET* "/" [] ok)
        (GET* "/a" [] ok)
        (context "/b" []
          (context "/b1" []
            (GET* "/" [] ok))
          (context "/" []
            ;; anonymous schema names
            (GET* "/" []
              :query-params [{x :- Long 1}]
              ok)
            ;; external schema names
            (GET* "/b2" []
              :query [q domain/Entity]
              ok)))))

  (fact "valid routes"
    (get* api "/") => ok?
    (get* api "/a") => ok?
    (get* api "/b/b1") => ok?
    (get* api "/b/b1/") => ok?
    (get* api "/b") => ok?
    (get* api "/b/") => ok?
    (get* api "/b//") => ok?
    (get* api "/b//b2") => ok?)

  (fact "invalid routes"
    (get* api "/b/b2") => not-ok?)

  (fact "swagger-docs have trailing slashes removed"
    (let [[status body] (get* api (str "/api/api-docs/" +name+) {})]
      status => 200
      (->> body
           :apis
           (map :path)) => ["/" "/a" "/b/b1" "/b" "/b//b2"]))))
