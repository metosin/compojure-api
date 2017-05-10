(ns compojure.api.resource-test
  (:require [compojure.api.sweet :refer :all]
            [compojure.api.test-utils :refer :all]
            [plumbing.core :refer [fnk]]
            [midje.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import (clojure.lang ExceptionInfo)))

(defn has-body [expected]
  (fn [{:keys [body]}]
    (= body expected)))

(def request-validation-failed?
  (throws ExceptionInfo #"Request validation failed"))

(def response-validation-failed?
  (throws ExceptionInfo #"Response validation failed"))

(facts "resource definitions"

  (fact "only top-level handler"
    (let [handler (resource
                    {:handler (constantly (ok {:total 10}))})]

      (fact "paths and methods don't matter"
        (handler {:request-method :get, :uri "/"}) => (has-body {:total 10})
        (handler {:request-method :head, :uri "/kikka"}) => (has-body {:total 10}))))

  (fact "top-level parameter coercions"
    (let [handler (resource
                    {:parameters {:query-params {:x Long}}
                     :handler (fnk [[:query-params x]]
                                (ok {:total x}))})]

      (handler {:request-method :get}) => request-validation-failed?
      (handler {:request-method :get, :query-params {:x "1"}}) => (has-body {:total 1})
      (handler {:request-method :get, :query-params {:x "1", :y "2"}}) => (has-body {:total 1})))

  (fact "top-level and operation-level parameter coercions"
    (let [handler (resource
                    {:parameters {:query-params {:x Long}}
                     :get {:parameters {:query-params {(s/optional-key :y) Long}}}
                     :handler (fnk [[:query-params x {y 0}]]
                                (ok {:total (+ x y)}))})]

      (handler {:request-method :get}) => request-validation-failed?
      (handler {:request-method :get, :query-params {:x "1"}}) => (has-body {:total 1})
      (handler {:request-method :get, :query-params {:x "1", :y "a"}}) => request-validation-failed?
      (handler {:request-method :get, :query-params {:x "1", :y "2"}}) => (has-body {:total 3})

      (fact "non-matching operation level parameters are not used"
        (handler {:request-method :post, :query-params {:x "1"}}) => (has-body {:total 1})
        (handler {:request-method :post, :query-params {:x "1", :y "2"}}) => (throws ClassCastException))))

  (fact "operation-level handlers"
    (let [handler (resource
                    {:parameters {:query-params {:x Long}}
                     :get {:parameters {:query-params {(s/optional-key :y) Long}}
                           :handler (fnk [[:query-params x {y 0}]]
                                      (ok {:total (+ x y)}))}
                     :post {:parameters {:query-params {:z Long}}}})]

      (handler {:request-method :get}) => request-validation-failed?
      (handler {:request-method :get, :query-params {:x "1"}}) => (has-body {:total 1})
      (handler {:request-method :get, :query-params {:x "1", :y "a"}}) => request-validation-failed?
      (handler {:request-method :get, :query-params {:x "1", :y "2"}}) => (has-body {:total 3})

      (fact "if no handler is found, nil is returned"
        (handler {:request-method :post, :query-params {:x "1"}}) => nil)))

  (fact "handler preference"
    (let [handler (resource
                    {:get {:handler (constantly (ok {:from "get"}))}
                     :handler (constantly (ok {:from "top"}))})]

      (handler {:request-method :get}) => (has-body {:from "get"})
      (handler {:request-method :post}) => (has-body {:from "top"})))

  (fact "resource without coercion"
    (let [handler (resource
                    {:get {:parameters {:query-params {(s/optional-key :y) Long
                                                       (s/optional-key :x) Long}}
                           :handler (fn [{{:keys [x y]} :query-params}]
                                      (ok {:x x
                                           :y y}))}}
                    {:coercion (constantly nil)})]

      (handler {:request-method :get}) => (has-body {:x nil, :y nil})
      (handler {:request-method :get, :query-params {:x "1"}}) => (has-body {:x "1", :y nil})
      (handler {:request-method :get, :query-params {:x "1", :y "a"}}) => (has-body {:x "1", :y "a"})
      (handler {:request-method :get, :query-params {:x 1, :y 2}}) => (has-body {:x 1, :y 2})))

  (fact "parameter mappings"
    (let [handler (resource
                    {:get {:parameters {:query-params {:q s/Str}
                                        :body-params {:b s/Str}
                                        :form-params {:f s/Str}
                                        :header-params {:h s/Str}
                                        :path-params {:p s/Str}}
                           :handler (fn [request]
                                      (ok (select-keys request [:query-params
                                                                :body-params
                                                                :form-params
                                                                :header-params
                                                                :path-params])))}})]

      (handler {:request-method :get
                :query-params {:q "q"}
                :body-params {:b "b"}
                :form-params {:f "f"}
                ;; the ring headers
                :headers {"h" "h"}
                ;; compojure routing
                :route-params {:p "p"}}) => (has-body {:query-params {:q "q"}
                                                       :body-params {:b "b"}
                                                       :form-params {:f "f"}
                                                       :header-params {:h "h"}
                                                       :path-params {:p "p"}})))

  (fact "response coercion"
    (let [handler (resource
                    {:responses {200 {:schema {:total (s/constrained Long pos? 'pos)}}}
                     :parameters {:query-params {:x Long}}
                     :get {:responses {200 {:schema {:total (s/constrained Long #(>= % 10) 'gte10)}}}
                           :handler (fnk [[:query-params x]]
                                      (ok {:total x}))}
                     :handler (fnk [[:query-params x]]
                                (ok {:total x}))})]

      (handler {:request-method :get}) => request-validation-failed?
      (handler {:request-method :get, :query-params {:x "-1"}}) => response-validation-failed?
      (handler {:request-method :get, :query-params {:x "1"}}) => response-validation-failed?
      (handler {:request-method :get, :query-params {:x "10"}}) => (has-body {:total 10})
      (handler {:request-method :post, :query-params {:x "1"}}) => (has-body {:total 1}))))

(fact "3-arity handler"
  (let [handler   (resource
                   {:parameters {:query-params {:x Long}}
                    :responses  {200 {:schema {:total (s/constrained Long pos? 'pos)}}}
                    :handler    (fn [{{x :x} :query-params} res _]
                                  (future
                                    (res (ok {:total x})))
                                  nil)})
        respond (promise), res-raise (promise), req-raise (promise)]

    (handler  {:query-params {:x 1}} respond nil)
    (handler  {:query-params {:x -1}} identity res-raise)
    (handler  {:query-params {:x "x"}} identity req-raise)

    (deref respond 1000 :timeout) => (has-body {:total 1})
    (throw (deref res-raise 1000 :timeout)) => response-validation-failed?
    (throw (deref req-raise 1000 :timeout)) => request-validation-failed?))

(fact "compojure-api routing integration"
  (let [app (context "/rest" []

              (GET "/no" request
                (ok (select-keys request [:uri :path-info])))

              ;; works & api-docs
              (context "/context" []
                (resource
                  {:handler (constantly (ok "CONTEXT"))}))

              ;; works, but no api-docs
              (ANY "/any" []
                (resource
                  {:handler (constantly (ok "ANY"))}))

              (context "/path/:id" []
                (resource
                  {:parameters {:path-params {:id s/Int}}
                   :handler (fn [request]
                              (ok (select-keys request [:path-params :route-params])))}))

              (resource
                {:get {:handler (fn [request]
                                  (ok (select-keys request [:uri :path-info])))}}))]

    (fact "normal endpoint works"
      (app {:request-method :get, :uri "/rest/no"}) => (has-body {:uri "/rest/no", :path-info "/no"}))

    (fact "wrapped in ANY works"
      (app {:request-method :get, :uri "/rest/any"}) => (has-body "ANY"))

    (fact "wrapped in context works"
      (app {:request-method :get, :uri "/rest/context"}) => (has-body "CONTEXT"))

    (fact "only exact path match works"
      (app {:request-method :get, :uri "/rest/context/2"}) => nil)

    (fact "path-parameters work: route-params are left untoucehed, path-params are coerced"
      (app {:request-method :get, :uri "/rest/path/12"}) => (has-body {:path-params {:id 12}
                                                                       :route-params {:id "12"}}))

    (fact "top-level GET without extra path works"
      (app {:request-method :get, :uri "/rest"}) => (has-body {:uri "/rest"
                                                               :path-info "/"}))

    (fact "top-level POST without extra path works"
      (app {:request-method :post, :uri "/rest"}) => nil)

    (fact "top-level GET with extra path misses"
      (app {:request-method :get, :uri "/rest/in-peaces"}) => nil)))

(fact "swagger-integration"
  (let [app (api
              (swagger-routes)
              (context "/rest" []
                (resource
                  {:parameters {:query-params {:x Long}}
                   :responses {400 {:schema (s/schema-with-name {:code s/Str} "Error")}}
                   :get {:parameters {:query-params {:y Long}}
                         :responses {200 {:schema (s/schema-with-name {:total Long} "Total")}}}
                   :post {}
                   :handler (constantly (ok {:total 1}))})))
        spec (get-spec app)]

    spec => (contains
              {:definitions (just
                              {:Error irrelevant
                               :Total irrelevant})
               :paths (just
                        {"/rest" (just
                                   {:get (just
                                           {:parameters (two-of irrelevant)
                                            :responses (just {:200 irrelevant, :400 irrelevant})})
                                    :post (just
                                            {:parameters (one-of irrelevant)
                                             :responses (just {:400 irrelevant})})})})})))
