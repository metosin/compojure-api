(ns compojure.api.coercion.spec-coercion-test
  (:require [midje.sweet :refer :all]
            [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [compojure.api.middleware :as mw]
            [compojure.api.coercion :as coercion]
            [compojure.api.coercion.spec :as cs]))

(s/def ::kikka spec/keyword?)
(s/def ::spec (s/keys :req-un [::kikka]))

(def valid-value {:kikka :kukka})
(def invalid-value {:kikka "kukka"})

(fact "request-coercion"
  (let [c! #(coercion/coerce-request! ::spec :body-params :body false %)]

    (fact ":spec coercion"
      (c! {:body-params valid-value
           ::mw/options {:coercion :spec}}) => valid-value
      (c! {:body-params invalid-value
           ::mw/options {:coercion :spec}}) => throws
      (try
        (c! {:body-params invalid-value
             ::mw/options {:coercion :spec}})
        (catch Exception e
          (ex-data e) => (just {:type :compojure.api.exception/request-validation
                                :coercion :spec
                                :in [:request :body-params]
                                :spec ::spec
                                :value invalid-value
                                :problems [{:in [:kikka]
                                            :path [:kikka]
                                            :pred `keyword?
                                            :val "kukka"
                                            :via [::spec ::kikka]}]
                                :request (contains
                                           {:body-params {:kikka "kukka"}})}))))

    (fact "format-based coercion"
      (c! {:body-params valid-value
           ::mw/options {:coercion :spec}
           :muuntaja/request {:format "application/json"}}) => valid-value
      (c! {:body-params invalid-value
           ::mw/options {:coercion :spec}
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

(def responses {200 {:schema ::spec}})

(def custom-coercion
  (cs/->SpecCoercion
    :custom
    (-> cs/default-options
        (assoc-in [:response :formats "application/json"] cs/json-conforming))))

(fact "response-coercion"
  (let [c! coercion/coerce-response!]

    (fact ":spec coercion"
      (c! {::mw/options {:coercion :spec}}
          (ok valid-value)
          responses) => (ok? valid-value)
      (c! {::mw/options {:coercion :spec}}
          (ok invalid-value)
          responses) => throws
      (try
        (c! {::mw/options {:coercion :spec}} (ok invalid-value) responses)
        (catch Exception e
          (ex-data e) => (contains {:type :compojure.api.exception/response-validation
                                    :coercion :schema
                                    :in [:response :body]
                                    :spec ::spec
                                    :value invalid-value
                                    :problems irrelevant
                                    :request {::mw/options {:coercion :spec}}}))))

    (fact "format-based custom coercion"
      (fact "request-negotiated response format"
        (c! {::mw/options {:coercion :spec}}
            (ok invalid-value)
            responses) => throws
        (c! {:muuntaja/response {:format "application/json"}
             ::mw/options {:coercion custom-coercion}}
            (ok invalid-value)
            responses) => (ok? valid-value)))

    (fact "no coercion"
      (c! {::mw/options {:coercion nil}}
          (ok valid-value)
          responses) => (ok? valid-value)
      (c! {::mw/options {:coercion nil}}
          (ok invalid-value)
          responses) => (ok? invalid-value))))
