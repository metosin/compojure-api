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
  (let [{{:keys [status body]} :response}
        (-> (p/session app)
          (p/request uri
            :request-method :get
            :params (or params {})))]
    [status (cheshire/parse-string body true)]))

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
;; Facts
;;

(facts "for a compojure-api app"
  (background
    (after :contents (swap! swagger/swagger dissoc app-name)))

  (defapi api
    (swaggered app-name
      (context "/api" []
        (GET* "/pertti" []
          :return User
          (ok pertti))
        (GET* "/user" []
          :return User
          :query  [user User]
          (ok user))
        (GET* "/user_list" []
          :return [User]
          :query  [user [User]]
          (ok user))
        (GET* "/user_set" []
          :return #{User}
          :query  [user #{User}]
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
    (let [[status body] (get* api "/api/pertti")]
      status => 200
      body => pertti))

  (fact "GET* with smart destructuring"
    (let [[status body] (get* api "/api/user" pertti)]
      status => 200
      body => pertti))

  (fact "POST* with smart destructuring"
    (let [[status body] (post* api "/api/user" (json pertti))]
      status => 200
      body => pertti))

  (fact "POST* with smart destructuring - lists"
    (let [[status body] (post* api "/api/user_list" (json [pertti]))]
      status => 200
      body => [pertti]))

  (fact "POST* with smart destructuring - sets"
    (let [[status body] (post* api "/api/user_set" (json #{pertti}))]
      status => 200
      body => [pertti]))

  (fact "POST* with compojure destructuring"
    (let [[status body] (post* api "/api/user_legacy" (json pertti))]
      status => 200
      body => pertti))

  (fact "Validation of returned data"
    (let [[status body] (get* api "/api/invalid-user")]
      status => 400))

  (fact "Routes without a :return parameter aren't validated"
    (let [[status body] (get* api "/api/not-validated")]
      status => 200
      body => invalid-user))

  (fact "Invalid json in body causes 400 with error message in json"
    (let [[status body] (post* api "/api/user" "{INVALID}")]
      status => 400
      (:type body) => "json-parse-exception"
      (:message body) => truthy)))
