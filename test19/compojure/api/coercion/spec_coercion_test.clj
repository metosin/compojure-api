(ns compojure.api.coercion.spec-coercion-test
  (:require [midje.sweet :refer :all]
            [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [compojure.api.test-utils :refer :all]
            [compojure.api.sweet :refer :all]
            [compojure.api.request :as request]
            [compojure.api.coercion :as coercion]
            [compojure.api.coercion.core :as cc]
            [compojure.api.coercion.spec :as cs]
            [spec-tools.data-spec :as ds]
            [spec-tools.core :as st]))

(s/def ::kikka spec/keyword?)
(s/def ::spec (s/keys :req-un [::kikka]))

(def valid-value {:kikka :kukka})
(def invalid-value {:kikka "kukka"})

(fact "request-coercion"
  (let [c! #(coercion/coerce-request! ::spec :body-params :body false false %)]

    (fact "default coercion"
      (c! {:body-params valid-value
           ::request/coercion :spec}) => valid-value
      (c! {:body-params invalid-value
           ::request/coercion :spec}) => (throws)
      (try
        (c! {:body-params invalid-value
             ::request/coercion :spec})
        (catch Exception e
          (ex-data e) => (contains {:type :compojure.api.exception/request-validation
                                    :coercion (coercion/resolve-coercion :spec)
                                    :in [:request :body-params]
                                    :spec st/spec?
                                    :value invalid-value
                                    :problems [{:in [:kikka]
                                                :path [:kikka]
                                                :pred `keyword?
                                                :val "kukka"
                                                :via [::spec ::kikka]}]
                                    :request (contains {:body-params {:kikka "kukka"}})}))))

    (fact "format-based coercion"
      (c! {:body-params valid-value
           ::request/coercion :spec
           :muuntaja/request {:format "application/json"}}) => valid-value
      (c! {:body-params invalid-value
           ::request/coercion :spec
           :muuntaja/request {:format "application/json"}}) => valid-value)

    (fact "no coercion"
      (c! {:body-params valid-value
           ::request/coercion nil
           :muuntaja/request {:format "application/json"}}) => valid-value
      (c! {:body-params invalid-value
           ::request/coercion nil
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

    (fact "default coercion"
      (c! {::request/coercion :spec}
          (ok valid-value)
          responses) => (ok? valid-value)
      (c! {::request/coercion :spec}
          (ok invalid-value)
          responses) => (throws)
      (try
        (c! {::request/coercion :spec} (ok invalid-value) responses)
        (catch Exception e
          (ex-data e) => (contains {:type :compojure.api.exception/response-validation
                                    :coercion (coercion/resolve-coercion :spec)
                                    :in [:response :body]
                                    :spec st/spec?
                                    :value invalid-value
                                    :problems anything
                                    :request {::request/coercion :spec}}))))

    (fact "format-based custom coercion"
      (fact "request-negotiated response format"
        (c! irrelevant
            (ok invalid-value)
            responses) => throws
        (c! {:muuntaja/response {:format "application/json"}
             ::request/coercion custom-coercion}
            (ok invalid-value)
            responses) => (ok? valid-value)))

    (fact "no coercion"
      (c! {::request/coercion nil}
          (ok valid-value)
          responses) => (ok? valid-value)
      (c! {::request/coercion nil}
          (ok invalid-value)
          responses) => (ok? invalid-value))))

(s/def ::x spec/int?)
(s/def ::y spec/int?)
(s/def ::total spec/pos-int?)

(facts "apis"
  (let [app (api
              {:coercion :spec}

              (POST "/body" []
                :summary "let & spec"
                :body [{:keys [x y]} (s/keys :req-un [::x ::y])]
                (ok {:total (+ x y)}))

              (POST "/body-map" []
                :summary "let & anonymous data-spec"
                :body [{:keys [x y]} {:x int?, (ds/opt :y) ::y}]
                (ok {:total (+ x (or y 0))}))

              (GET "/query" []
                :summary "let & spec"
                :query [{:keys [x y]} (s/keys :req-un [::x ::y])]
                (ok {:total (+ x y)}))

              (GET "/query-params" []
                :summary "letk & named specs "
                :query-params [x :- ::x, y :- ::y]
                (ok {:total (+ x y)}))

              (POST "/body-params" []
                :summary "letk & predicates & optional"
                :body-params [x :- int?, {y :- ::y 0}]
                (ok {:total (+ x y)}))

              (GET "/response" []
                :query-params [x :- ::x, y :- ::y]
                :return (s/keys :req-un [::total])
                (ok {:total (+ x y)}))

              (context "/resource" []
                (resource
                  {:get {:summary "parameters as specs"
                         :parameters {:query-params (s/keys :req-un [::x ::y])}
                         :responses {200 {:schema (s/keys :req-un [::total])}}
                         :handler (fn [{{:keys [x y]} :query-params}]
                                    (ok {:total (+ x y)}))}
                   :post {:summary "parameters as data-specs"
                          :parameters {:body-params {:x int? :y int?}}
                          :responses {200 {:schema (s/keys :req-un [::total])}}
                          :handler (fn [{{:keys [x y]} :query-params}]
                                     (ok {:total (+ x y)}))}})))]

    (fact "query"
      (let [[status body] (get* app "/query" {:x "1", :y 2})]
        status => 200
        body => {:total 3})
      (let [[status body] (get* app "/query" {:x "1", :y "kaks"})]
        status => 400
        body => {:coercion "spec"
                 :in ["request" "query-params"]
                 :problems [{:in ["y"]
                             :path ["y"]
                             :pred "clojure.core/int?"
                             :val "kaks"
                             :via ["compojure.api.coercion.spec-coercion-test/y"]}]
                 :spec "(spec-tools.core/spec {:spec (clojure.spec.alpha/keys :req-un [:compojure.api.coercion.spec-coercion-test/x :compojure.api.coercion.spec-coercion-test/y]), :type :map, :keys #{:y :x}})"
                 :type "compojure.api.exception/request-validation"
                 :value {:x "1", :y "kaks"}}))
    
    (fact "body"
      (let [[status body] (post* app "/body" (json {:x 1, :y 2, :z 3}))]
        status => 200
        body => {:total 3}))

    (fact "body-map"
      (let [[status body] (post* app "/body-map" (json {:x 1, :y 2}))]
        status => 200
        body => {:total 3})
      (let [[status body] (post* app "/body-map" (json {:x 1}))]
        status => 200
        body => {:total 1}))

    (fact "query-params"
      (let [[status body] (get* app "/query-params" {:x "1", :y 2})]
        status => 200
        body => {:total 3})
      (let [[status body] (get* app "/query-params" {:x "1", :y "a"})]
        status => 400
        body => (contains {:coercion "spec"
                           :in ["request" "query-params"]})))

    (fact "body-params"
      (let [[status body] (post* app "/body-params" (json {:x 1, :y 2}))]
        status => 200
        body => {:total 3})
      (let [[status body] (post* app "/body-params" (json {:x 1}))]
        status => 200
        body => {:total 1})
      (let [[status body] (post* app "/body-params" (json {:x "1"}))]
        status => 400
        body => (contains {:coercion "spec"
                           :in ["request" "body-params"]})))

    (fact "response"
      (let [[status body] (get* app "/response" {:x 1, :y 2})]
        status => 200
        body => {:total 3})
      (let [[status body] (get* app "/response" {:x -1, :y -2})]
        status => 500
        body => (contains {:coercion "spec"
                           :in ["response" "body"]})))))
