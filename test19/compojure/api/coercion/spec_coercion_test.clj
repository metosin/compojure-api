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
            [spec-tools.core :as st]
            [compojure.api.validator :as validator]
            [compojure.api.swagger :as swagger]
            [compojure.api.routes :as routes]))

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

    (fact "coercion also unforms"
      (let [spec (s/or :int spec/int? :keyword spec/keyword?)
            c! #(coercion/coerce-request! spec :body-params :body false false %)]
        (c! {:body-params 1
             ::request/coercion :spec}) => 1
        (c! {:body-params :kikka
             ::request/coercion :spec}) => :kikka))

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
(s/def ::xy (s/keys :req-un [::x ::y]))
(s/def ::total spec/pos-int?)

(facts "apis"
  (let [app (api
              {:swagger {:spec "/swagger.json"}
               :coercion :spec}

              (POST "/body" []
                :body [{:keys [x y]} ::xy]
                (ok {:total (+ x y)}))

              (PUT "/body" []
                :body [body ::xy]
                (ok body))

              (POST "/body-map" []
                :body [{:keys [x y]} {:x int?, (ds/opt :y) ::y}]
                (ok {:total (+ x (or y 0))}))

              (GET "/query" []
                :query [{:keys [x y]} ::xy]
                (ok {:total (+ x y)}))

              (GET "/query-params" []
                :query-params [x :- ::x, y :- ::y]
                (ok {:total (+ x y)}))

              (POST "/body-params" []
                :body-params [x :- int?, {y :- ::y 0}]
                (ok {:total (+ x y)}))

              (POST "/body-string" []
                :body [body string?]
                (ok {:body body}))

              (GET "/response" []
                :query-params [x :- ::x, y :- ::y]
                :return (s/keys :req-un [::total])
                (ok {:total (+ x y)}))

              (context "/resource" []
                (resource
                  {:get {:parameters {:query-params ::xy}
                         :responses {200 {:schema (s/keys :req-un [::total])}}
                         :handler (fn [{{:keys [x y]} :query-params}]
                                    (ok {:total (+ x y)}))}
                   :post {:parameters {:body-params {:x int? (ds/opt :y) int?}}
                          :responses {200 {:schema (s/keys :req-un [::total])}}
                          :handler (fn [{{:keys [x y]} :body-params}]
                                     (ok {:total (+ x (or y 0))}))}
                   :put {:parameters {:body-params ::xy}
                         :handler (fn [{body-params :body-params}]
                                    (ok body-params))}})))]

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
                             :via ["compojure.api.coercion.spec-coercion-test/xy"
                                   "compojure.api.coercion.spec-coercion-test/y"]}]
                 :spec "(spec-tools.core/spec {:spec (clojure.spec.alpha/keys :req-un [:compojure.api.coercion.spec-coercion-test/x :compojure.api.coercion.spec-coercion-test/y]), :type :map, :keys #{:y :x}})"
                 :type "compojure.api.exception/request-validation"
                 :value {:x "1", :y "kaks"}}))

    (fact "body"
      (let [[status body] (post* app "/body" (json-string {:x 1, :y 2, :z 3}))]
        status => 200
        body => {:total 3}))

    (fact "body-map"
      (let [[status body] (post* app "/body-map" (json-string {:x 1, :y 2}))]
        status => 200
        body => {:total 3})
      (let [[status body] (post* app "/body-map" (json-string {:x 1}))]
        status => 200
        body => {:total 1}))

    (fact "body-string"
      (let [[status body] (post* app "/body-string" (json-string "kikka"))]
        status => 200
        body => {:body "kikka"}))

    (fact "query-params"
      (let [[status body] (get* app "/query-params" {:x "1", :y 2})]
        status => 200
        body => {:total 3})
      (let [[status body] (get* app "/query-params" {:x "1", :y "a"})]
        status => 400
        body => (contains {:coercion "spec"
                           :in ["request" "query-params"]})))

    (fact "body-params"
      (let [[status body] (post* app "/body-params" (json-string {:x 1, :y 2}))]
        status => 200
        body => {:total 3})
      (let [[status body] (post* app "/body-params" (json-string {:x 1}))]
        status => 200
        body => {:total 1})
      (let [[status body] (post* app "/body-params" (json-string {:x "1"}))]
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
                           :in ["response" "body"]})))

    (fact "resource"
      (fact "parameters as specs"
        (let [[status body] (get* app "/resource" {:x 1, :y 2})]
          status => 200
          body => {:total 3})
        (let [[status body] (get* app "/resource" {:x -1, :y -2})]
          status => 500
          body => (contains {:coercion "spec"
                             :in ["response" "body"]})))

      (fact "parameters as data-specs"
        (let [[status body] (post* app "/resource" (json-string {:x 1, :y 2}))]
          status => 200
          body => {:total 3})
        (let [[status body] (post* app "/resource" (json-string {:x 1}))]
          status => 200
          body => {:total 1})
        (let [[status body] (post* app "/resource" (json-string {:x -1, :y -2}))]
          status => 500
          body => (contains {:coercion "spec"
                             :in ["response" "body"]}))))

    (fact "extra keys are stripped from body-params before validation"
      (fact "for resources"
        (let [[status body] (put* app "/resource" (json-string {:x 1, :y 2 ::kikka "kakka"}))]
          status => 200
          body => {:x 1, :y 2}))
      (fact "for endpoints"
        (let [[status body] (put* app "/body" (json-string {:x 1, :y 2 ::kikka "kakka"}))]
          status => 200
          body => {:x 1, :y 2})))

    (fact "generates valid swagger spec"
      (validator/validate app) =not=> (throws))

    (fact "swagger spec has all things"
      (let [total-schema {:description "",
                          :schema {:properties
                                   {:total {:format "int64",
                                            :minimum 1,
                                            :type "integer"}},
                                   :required ["total"],
                                   :type "object"}}]
        (get-spec app)
        => (contains
             {:definitions {}
              :paths (contains
                       {"/body" {:post
                                 {:parameters
                                  [{:description ""
                                    :in "body"
                                    :name "compojure.api.coercion.spec-coercion-test/xy"
                                    :required true
                                    :schema {:properties
                                             {:x {:format "int64"
                                                  :type "integer"}
                                              :y {:format "int64"
                                                  :type "integer"}}
                                             :required ["x" "y"]
                                             :type "object"}}]
                                  :responses {:default {:description ""}}}
                                 :put
                                 {:parameters
                                  [{:description ""
                                    :in "body"
                                    :name "compojure.api.coercion.spec-coercion-test/xy"
                                    :required true
                                    :schema {:properties
                                             {:x {:format "int64"
                                                  :type "integer"}
                                              :y {:format "int64"
                                                  :type "integer"}}
                                             :required ["x" "y"]
                                             :type "object"}}]
                                  :responses {:default {:description ""}}}}
                        "/body-map" {:post
                                     {:parameters
                                      [{:description ""
                                        :in "body"
                                        :name ""
                                        :required true
                                        :schema {:properties {:x {:format "int64"
                                                                  :type "integer"}
                                                              :y {:format "int64"
                                                                  :type "integer"}}
                                                 :required ["x"]
                                                 :type "object"}}]
                                      :responses {:default {:description ""}}}}
                        "/query" {:get {:parameters
                                        [{:description "" :format "int64"
                                          :in "query" :name "x" :required true
                                          :type "integer"}
                                         {:description "" :format "int64"
                                          :in "query" :name "y" :required true
                                          :type "integer"}]
                                        :responses {:default {:description ""}}}}
                        "/query-params" {:get
                                         {:parameters
                                          [{:description ""
                                            :format "int64"
                                            :in "query"
                                            :name "x"
                                            :required true
                                            :type "integer"}
                                           {:description ""
                                            :format "int64"
                                            :in "query"
                                            :name "y"
                                            :required true
                                            :type "integer"}]
                                          :responses {:default {:description ""}}}}
                        "/body-params" {:post
                                        {:parameters
                                         [{:description ""
                                           :in "body"
                                           :name ""
                                           :required true
                                           :schema {:properties {:x {:format "int64"
                                                                     :type "integer"}
                                                                 :y {:format "int64"
                                                                     :type "integer"}}
                                                    :required ["x"]
                                                    :type "object"}}]
                                         :responses {:default {:description ""}}}}
                        "/body-string" {:post
                                        {:parameters
                                         [{:description ""
                                           :in "body"
                                           :name ""
                                           :required true
                                           :schema {:type "string"}}]
                                         :responses {:default {:description ""}}}}
                        "/response" {:get
                                     {:parameters
                                      [{:description ""
                                        :format "int64"
                                        :in "query"
                                        :name "x"
                                        :required true
                                        :type "integer"}
                                       {:description ""
                                        :format "int64"
                                        :in "query"
                                        :name "y"
                                        :required true
                                        :type "integer"}]
                                      :responses {:200 total-schema
                                                  :default {:description ""}}}}
                        "/resource" {:get
                                     {:parameters
                                      [{:description ""
                                        :format "int64"
                                        :in "query"
                                        :name "x"
                                        :required true
                                        :type "integer"}
                                       {:description ""
                                        :format "int64"
                                        :in "query"
                                        :name "y"
                                        :required true
                                        :type "integer"}]
                                      :responses {:200 total-schema
                                                  :default {:description ""}}}
                                     :post
                                     {:parameters
                                      [{:description ""
                                        :in "body"
                                        :name ""
                                        :required true
                                        :schema {:properties {:x {:format "int64"
                                                                  :type "integer"}
                                                              :y {:format "int64"
                                                                  :type "integer"}}
                                                 :required ["x"]
                                                 :type "object"}}]
                                      :responses {:200 total-schema
                                                  :default {:description ""}}}
                                     :put
                                     {:parameters
                                      [{:description ""
                                        :in "body"
                                        :name "compojure.api.coercion.spec-coercion-test/xy"
                                        :required true
                                        :schema {:properties {:x {:format "int64"
                                                                  :type "integer"}
                                                              :y {:format "int64"
                                                                  :type "integer"}}
                                                 :required ["x" "y"]
                                                 :type "object"}}]
                                      :responses {:default {:description ""}}}}})})))))
