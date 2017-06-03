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

(defn ok [body]
  {:status 200, :body body})

(defn ok? [body]
  (contains (ok body)))

(def responses {200 {:schema Schema}})

(def custom-coercion
  (cs/->SchemaCoercion
    :custom
    (-> cs/default-options
        (assoc-in [:response :formats "application/json"] cs/json-coercion-matcher))))

(fact "response-coercion"
  (let [c! coercion/coerce-response!]

    (fact "default coercion"
      (c! {} (ok valid-value) responses) => (ok? valid-value)
      (c! {} (ok invalid-value) responses) => throws
      (try
        (c! {} (ok invalid-value) responses)
        (catch Exception e
          (ex-data e) => (contains {:type :compojure.api.exception/response-validation
                                    :coercion :schema
                                    :in [:response :body]
                                    :schema Schema
                                    :value invalid-value
                                    :errors anything
                                    :request {}}))))

    (fact ":schema coercion"
      (fact "default coercion"
        (c! {::mw/options {:coercion :schema}}
            (ok valid-value)
            responses) => (ok? valid-value)
        (c! {::mw/options {:coercion :schema}}
            (ok invalid-value)
            responses) => throws))

    (fact "format-based custom coercion"
      (fact "request-negotiated response format"
        (c! {}
            (ok invalid-value)
            responses) => throws
        (c! {:muuntaja/response {:format "application/json"}
             ::mw/options {:coercion custom-coercion}}
            (ok invalid-value)
            responses) => (ok? valid-value)))

    (fact "no coercion"
      (c! {}
          (ok valid-value)
          responses) => (ok? valid-value)
      (c! {}
          (ok invalid-value)
          responses) => throws)))

