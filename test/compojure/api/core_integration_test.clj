(ns compojure.api.core-integration-test
  (:require [midje.sweet :refer :all]
            [schema.core :as s]
            [ring.swagger.schema :refer :all]
            [compojure.api.swagger :as swagger]
            [ring.util.http-response :refer :all]
            [peridot.core :as p]
            [cheshire.core :as cheshire]
            [compojure.core :as compojure]
            [clojure.java.io :as io]
            [compojure.api.sweet :refer :all])
  (:import [java.io ByteArrayInputStream]))

;;
;; common
;;

(defn get* [app uri & [params]]
  (let [{{:keys [status body headers]} :response}
        (-> (p/session app)
          (p/request uri
            :request-method :get
            :params (or params {})))]
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

(defmodel User {:id   Long
                :name String})

(def pertti {:id 1 :name "Pertti"})

(def invalid-user {:id 1 :name "Jorma" :age 50})

(def app-name (str (gensym)))

;;
;; Middleware setup
;;

(def mw* "mw")

(defn middleware* [value]
  (fn [handler]
    (fn [request]
      (let [response (handler request)]
        (update-in response [:headers mw*]
                   (fn [x] (str x value)))))))

(def reply-mw* (header (ok "true") mw* "1"))

;;
;; Facts
;;

(facts "for a compojure-api app"
   (background
     (after :contents (swap! swagger/swagger dissoc app-name)))

   (defapi api
     (with-middleware [(middleware* 6) (middleware* 7)]
       (swaggered app-name

         (context "/middlewares" []
           (GET* "/simple" [] reply-mw*)
           (with-middleware [(middleware* 4) (middleware* 5)]
             (GET* "/nested" [] reply-mw*)
             (GET* "/nested-declared" []
               :middlewares [(middleware* 2) (middleware* 3)]
               reply-mw*)))

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
             (ok user)))

         (context "/smart" []
           (GET* "/plus" []
             :query-params [x :- Long y :- Long]
             (ok {:total (+ x y)}))
           (GET* "/multiply/:x/:y" []
             :path-params [x :- Long y :- Long]
             (ok {:total (* x y)}))
           (POST* "/minus" []
             :body-params [x :- Long {y :- Long 1}]
             (ok {:total (- x y)})))

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
                  :b b}))))))

   (facts "with-middleware"

     (fact "applies middlewares left-to-right"
       (let [[status body headers] (get* api "/middlewares/simple" {})]
         status => 200
         (get headers mw*) => "167"))

     (fact "applies nested middlewares left-to-right closest one first"
       (let [[status body headers] (get* api "/middlewares/nested" {})]
         status => 200
         (get headers mw*) => "14567"))

     (fact "applies nested & declared middlewares left-to-right closest one first"
       (let [[status body headers] (get* api "/middlewares/nested-declared" {})]
         status => 200
         (get headers mw*) => "1234567"))

     )

   (facts "compojure destucturing"
     (doseq [uri ["regular" "regular2" "vector" "vector2" "symbol" "integrated"]]
       (fact {:midje/description uri}
         (let [[status body] (get* api (str "/destructuring/" uri) {:a "a" :b "b"})]
           status => 200
           body => {:a "a" :b "b"}))))

   (facts "query, path and body parameter smart destucturing"

     (fact "query-parameters"
       (let [[status body] (get* api "/smart/plus" {:x 2 :y 3})]
         status => 200
         body => {:total 5}))

     (fact "query-parameters"
       (let [[status body] (get* api "/smart/multiply/2/3")]
         status => 200
         body => {:total 6}))

     (fact "body-parameters"
       (let [[status body] (post* api "/smart/minus" (json {:x 2 :y 3}))]
         status => 200
         body => {:total -1}))

     (fact "default parameters"
       (let [[status body] (post* api "/smart/minus" (json {:x 2}))]
         status => 200
         body => {:total 1})))

   (facts "models"

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
         status => 400))

     (fact "Routes without a :return parameter aren't validated"
       (let [[status body] (get* api "/models/not-validated")]
         status => 200
         body => invalid-user))

     (fact "Invalid json in body causes 400 with error message in json"
       (let [[status body] (post* api "/models/user" "{INVALID}")]
         status => 400
         (:type body) => "json-parse-exception"
         (:message body) => truthy))))
