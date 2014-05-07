(ns compojure.api.json-test
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [compojure.api.json :refer :all])
  (:import [java.io ByteArrayInputStream]))

(defn stream [s]
  (ByteArrayInputStream. (.getBytes s "utf-8")))

(defn json-request [s]
  {:content-type "application/json"
   :body (stream s)})

(def with-json-support (json-support identity))

(fact "json-request?"
  (json-request? {:content-type "application/xml"}) => false
  (json-request? {:content-type "application/json"}) => true
  (json-request? {:content-type "application/vnd.myapp+json"}) => true)

(fact "json-support"

  (fact "json-request-support"

    (fact "json-list"
      (let [request (with-json-support (json-request "[1,2,3]"))]
        (:body request)         => "[1,2,3]"
        (:body-params request)  => [1 2 3]
        (:json-params request)  => nil
        (:params request)       => nil))

    (fact "json-map"
      (let [request (with-json-support (json-request "{\"a\":1,\"b\":\"value\"}" ))]
        (:body request)         => "{\"a\":1,\"b\":\"value\"}"
        (:body-params request)  => {:a 1, :b "value"}
        (:json-params request)  => {:a 1, :b "value"}
        (:params request)       => {:a 1, :b "value"}))

    (fact "json-primitive"
      (let [request (with-json-support (json-request "true" ))]
        (:body request)         => (checker [x] (instance? ByteArrayInputStream x))
        (:body-params request)  => nil
        (:json-params request)  => nil
        (:params request)       => nil))

    (fact "json capability"
      (let [request (with-json-support (json-request "true" ))]
        (-> request :meta :consumes) => ["application/json"]
        (-> request :meta :produces) => ["application/json"])))

  (fact "json-response-support"))
