(ns compojure.api.core-test
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [compojure.api.core :refer :all])
  (:import [java.io ByteArrayInputStream]))

(defn stream [s]
  (ByteArrayInputStream. (.getBytes s "utf-8")))

(defn json-request [s]
  {:content-type "application/json"
   :body (stream s)})

(def handler (api-middleware identity))

(fact "json-request?"
  (json-request? {:content-type "application/xml"}) => false
  (json-request? {:content-type "application/json"}) => true
  (json-request? {:content-type "application/vnd.myapp+json"}) => true)

(fact "api-middleware end2end"

  (fact "json-list"
    (let [request (handler (json-request "[1,2,3]"))]
      (:body request)         => "[1,2,3]"
      (:body-params request)  => [1 2 3]
      (:json-params request)  => nil
      (:params request)       => {}))

  (fact "json-map"
    (let [request (handler (json-request "{\"a\":1,\"b\":\"value\"}" ))]
      (:body request)         => "{\"a\":1,\"b\":\"value\"}"
      (:body-params request)  => {:a 1, :b "value"}
      (:json-params request)  => {:a 1, :b "value"}
      (:params request)       => {:a 1, :b "value"})))
