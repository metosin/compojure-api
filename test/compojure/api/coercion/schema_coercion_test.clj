(ns compojure.api.coercion.schema-coercion-test
  (:require [midje.sweet :refer :all]
            [schema.core :as s]
            [compojure.api.middleware :as mw]
            [compojure.api.coercion :as coercion]
            [compojure.api.coercion.schema :as cs]))

(s/defschema Schema {:kikka s/Keyword})

(def valid-value {:kikka :kukka})
(def invalid-value {:kikka "kukka"})

(fact "request-coercion"
  (let [c! #(coercion/coerce-request! Schema :body-params :body false %)]
    (fact "default coercion"
      (c! {:body-params valid-value}) => valid-value
      (c! {:body-params invalid-value}) => throws
      (try
        (c! {:body-params invalid-value})
        (catch Exception e
          (ex-data e) => (just {:type :compojure.api.exception/request-validation
                                :coercion :schema
                                :in [:request :body-params]
                                :schema Schema
                                :value invalid-value
                                :errors anything
                                :request {:body-params {:kikka "kukka"}}}))))
    (fact ":schema coercion"
      (c! {:body-params valid-value
           ::mw/options {:coercion :schema}}) => valid-value
      (c! {:body-params invalid-value
           ::mw/options {:coercion :schema}}) => throws)
    (fact "format-based coercion"
      (c! {:body-params valid-value
           :muuntaja/request {:format "application/json"}}) => valid-value
      (c! {:body-params invalid-value
           :muuntaja/request {:format "application/json"}}) => valid-value)
    (fact "no coercion"
      (c! {:body-params valid-value
           ::mw/options {:coercion nil}
           :muuntaja/request {:format "application/json"}}) => valid-value
      (c! {:body-params invalid-value
           ::mw/options {:coercion nil}
           :muuntaja/request {:format "application/json"}}) => invalid-value)))

(future-fact "response-coercion")

