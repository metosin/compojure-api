(ns compojure.api.resource-test
  (:require [compojure.api.sweet :refer :all]
            [compojure.api.test-utils :refer :all]
            [plumbing.core :refer [fnk]]
            [clojure.test :refer [deftest is testing]]
            [ring.util.http-response :refer :all]
            [clojure.core.async :as a]
            [schema.core :as s]
            [compojure.api.test-utils :refer [call]])
  (:import (clojure.lang ExceptionInfo)))

(defn is-has-body [expected {:keys [body]}]
  (is (= body expected)))

(defmacro is-request-validation-failed? [form]
  `(is (~'thrown? ExceptionInfo #"Request validation failed" ~form)))

(defmacro is-response-validation-failed? [form]
  `(is (~'thrown? ExceptionInfo #"Response validation failed" ~form)))

(deftest resource-definitions-test

  (testing "only top-level handler"
    (let [handler (resource
                    {:handler (constantly (ok {:total 10}))})]

      (testing "paths and methods don't matter"
        (is-has-body {:total 10} (call handler {:request-method :get, :uri "/"}))
        (is-has-body {:total 10} (call handler {:request-method :head, :uri "/kikka"})))))

  (testing "top-level parameter coercions"
    (let [handler (resource
                    {:parameters {:query-params {:x Long}}
                     :handler (fnk [[:query-params x]]
                                (ok {:total x}))})]

      (is-request-validation-failed? (call handler {:request-method :get}))
      (is-has-body {:total 1} (call handler {:request-method :get, :query-params {:x "1"}}))
      (is-has-body {:total 1} (call handler {:request-method :get, :query-params {:x "1", :y "2"}}))))

  (testing "top-level and operation-level parameter coercions"
    (let [handler (resource
                    {:parameters {:query-params {:x Long}}
                     :get {:parameters {:query-params {(s/optional-key :y) Long}}}
                     :handler (fnk [[:query-params x {y 0}]]
                                (ok {:total (+ x y)}))})]

      (is-request-validation-failed? (call handler {:request-method :get}))
      (is-has-body {:total 1} (call handler {:request-method :get, :query-params {:x "1"}}))
      (is-request-validation-failed? (call handler {:request-method :get, :query-params {:x "1", :y "a"}}))
      (is-has-body {:total 3} (call handler {:request-method :get, :query-params {:x "1", :y "2"}}))

      (testing "non-matching operation level parameters are not used"
        (is-has-body {:total 1} (call handler {:request-method :post, :query-params {:x "1"}}))
        (is (thrown? ClassCastException (call handler {:request-method :post, :query-params {:x "1", :y "2"}}))))))

  (testing "middleware"
    (let [mw (fn [handler k] (fn [req] (update-in (handler req) [:body :mw] (fnil conj '()) k)))
          handler (resource
                    {:middleware [[mw :top1] [mw :top2]]
                     :get {:middleware [[mw :get1] [mw :get2]]}
                     :post {:middleware [[mw :post1] [mw :post2]]}
                     :handler (constantly (ok))})]

      (testing "top + method-level mw are applied if they are set"
        (is-has-body {:mw [:top1 :top2 :get1 :get2]} (call handler {:request-method :get}))
        (is-has-body {:mw [:top1 :top2 :post1 :post2]} (call handler {:request-method :post})))
      (testing "top-level mw are applied if method doesn't have mw"
        (is-has-body {:mw [:top1 :top2]} (call handler {:request-method :put})))))

  (testing "operation-level handlers"
    (let [handler (resource
                    {:parameters {:query-params {:x Long}}
                     :get {:parameters {:query-params {(s/optional-key :y) Long}}
                           :handler (fnk [[:query-params x {y 0}]]
                                      (ok {:total (+ x y)}))}
                     :post {:parameters {:query-params {:z Long}}}})]

      (is-request-validation-failed? (call handler {:request-method :get}))
      (is-has-body {:total 1} (call handler {:request-method :get, :query-params {:x "1"}}))
      (is-request-validation-failed? (call handler {:request-method :get, :query-params {:x "1", :y "a"}}))
      (is-has-body {:total 3} (call handler {:request-method :get, :query-params {:x "1", :y "2"}}))

      (testing "if no handler is found, nil is returned"
        (is (nil? (call handler {:request-method :post, :query-params {:x "1"}}))))))

  (testing "handler preference"
    (let [handler (resource
                    {:get {:handler (constantly (ok {:from "get"}))}
                     :handler (constantly (ok {:from "top"}))})]

      (is-has-body {:from "get"} (call handler {:request-method :get}))
      (is-has-body {:from "top"} (call handler {:request-method :post}))))

  (testing "resource without coercion"
    (let [handler (resource
                    {:coercion nil
                     :get {:parameters {:query-params {(s/optional-key :y) Long
                                                       (s/optional-key :x) Long}}
                           :handler (fn [{{:keys [x y]} :query-params}]
                                      (ok {:x x
                                           :y y}))}})]

      (is-has-body {:x nil, :y nil} (call handler {:request-method :get}))
      (is-has-body {:x "1", :y nil} (call handler {:request-method :get, :query-params {:x "1"}}))
      (is-has-body {:x "1", :y "a"} (call handler {:request-method :get, :query-params {:x "1", :y "a"}}))
      (is-has-body {:x 1, :y 2} (call handler {:request-method :get, :query-params {:x 1, :y 2}}))))
 
  (testing "parameter mappings"
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

      (is-has-body {:query-params {:q "q"}
                    :body-params {:b "b"}
                    :form-params {:f "f"}
                    :header-params {:h "h"}
                    :path-params {:p "p"}}
                   (call handler {:request-method :get
                                  :query-params {:q "q"}
                                  :body-params {:b "b"}
                                  :form-params {:f "f"}
                                  ;; the ring headers
                                  :headers {"h" "h"}
                                  ;; compojure routing
                                  :route-params {:p "p"}}))))

  (testing "response coercion"
    (let [handler (resource
                    {:responses {200 {:schema {:total (s/constrained Long pos? 'pos)}}}
                     :parameters {:query-params {:x Long}}
                     :get {:responses {200 {:schema {:total (s/constrained Long #(>= % 10) 'gte10)}}}
                           :handler (fnk [[:query-params x]]
                                      (ok {:total x}))}
                     :handler (fnk [[:query-params x]]
                                (ok {:total x}))})]

      (is-request-validation-failed? (call handler {:request-method :get}))
      (is-response-validation-failed? (call handler {:request-method :get, :query-params {:x "-1"}}))
      (is-response-validation-failed? (call handler {:request-method :get, :query-params {:x "1"}}))
      (is-has-body {:total 10} (call handler {:request-method :get, :query-params {:x "10"}}))
      (is-has-body {:total 1} (call handler {:request-method :post, :query-params {:x "1"}})))))

(deftest explicit-async-tests-test
  (let [handler (resource
                  {:parameters {:query-params {:x Long}}
                   :responses {200 {:schema {:total (s/constrained Long pos? 'pos)}}}
                   :summary "top-level async handler"
                   :async-handler (fn [{{x :x} :query-params} res _]
                                    (future
                                      (res (ok {:total x})))
                                    nil)
                   :get {:summary "operation-level async handler"
                         :async-handler (fn [{{x :x} :query-params} respond _]
                                          (future
                                            (respond (ok {:total (inc x)})))
                                          nil)}
                   :post {:summary "operation-level sync handler"
                          :handler (fn [{{x :x} :query-params}]
                                     (ok {:total (* x 10)}))}
                   :put {:summary "operation-level async send"
                         :handler (fn [{{x :x} :query-params}]
                                    (a/go
                                      (a/<! (a/timeout 100))
                                      (ok {:total (* x 100)})))}})]

    (testing "top-level async handler"
      (let [respond (promise), res-raise (promise), req-raise (promise)]
        (handler {:query-params {:x 1}} respond (promise))
        (handler {:query-params {:x -1}} (promise) res-raise)
        (handler {:query-params {:x "x"}} (promise) req-raise)

        (is-has-body {:total 1} (deref respond 1000 :timeout))
        (is-response-validation-failed? (throw (deref res-raise 1000 :timeout)))
        (is-request-validation-failed? (throw (deref req-raise 1000 :timeout)))))

    (testing "operation-level async handler"
      (let [respond (promise)]
        (handler {:request-method :get, :query-params {:x 1}} respond (promise))
        (is-has-body {:total 2} (deref respond 1000 :timeout))))

    (testing "sync handler can be called from async"
      (let [respond (promise)]
        (handler {:request-method :post, :query-params {:x 1}} respond (promise))
        (is-has-body {:total 10} (deref respond 1000 :timeout)))
      (testing "response coercion works"
        (let [raise (promise)]
          (handler {:request-method :post, :query-params {:x -1}} (promise) raise)
          (is-response-validation-failed? (throw (deref raise 1000 :timeout))))))

    (testing "core.async ManyToManyChannel"
      (testing "works with 3-arity"
        (let [respond (promise)]
          (handler {:request-method :put, :query-params {:x 1}} respond (promise))
          (is-has-body {:total 100} (deref respond 2000 :timeout)))
        (testing "response coercion works"
          (let [raise (promise)]
            (handler {:request-method :put, :query-params {:x -1}} (promise) raise)
            (is-response-validation-failed? (throw (deref raise 2000 :timeout))))))
      (testing "fails with 1-arity"
        (is (thrown? Exception (handler {:request-method :put, :query-params {:x 1}}))) #_(is-has-body {:total 100})
        (is (thrown? Exception (handler {:request-method :put, :query-params {:x -1}}))) #_response-validation-failed?))))

(deftest compojure-api-routing-integration-test
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

    (testing "normal endpoint works"
      (is-has-body
        {:uri "/rest/no", :path-info "/no"}
        (call app {:request-method :get, :uri "/rest/no"})))

    (testing "wrapped in ANY works"
      (is-has-body
        "ANY"
        (call app {:request-method :get, :uri "/rest/any"})))

    (testing "wrapped in context works"
      (is-has-body
        "CONTEXT"
        (call app {:request-method :get, :uri "/rest/context"})))

    (testing "only exact path match works"
      (is (nil? (call app {:request-method :get, :uri "/rest/context/2"}))))

    (testing "path-parameters work: route-params are left untoucehed, path-params are coerced"
      (is-has-body
        {:path-params {:id 12}
         :route-params {:id "12"}}
        (call app {:request-method :get, :uri "/rest/path/12"})))

    (testing "top-level GET without extra path works"
      (is-has-body
        {:uri "/rest"
         :path-info "/"}
        (call app {:request-method :get, :uri "/rest"})))

    (testing "top-level POST without extra path works"
      (is (nil? (call app {:request-method :post, :uri "/rest"}))))

    (testing "top-level GET with extra path misses"
      (is (nil? (call app {:request-method :get, :uri "/rest/in-peaces"}))))))

(deftest swagger-integration-test
  (testing "explicitly defined methods produce api-docs"
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

      (is (= {:definitions #{:Error :Total}
              :paths {"/rest" {:get {:parameters 2
                                     :responses #{:200 :400}}
                               :post {:parameters 1
                                      :responses #{:400}}}}}
            (-> spec
                (select-keys [:definitions :paths])
                (update :definitions (comp set keys))
                (update-in [:paths "/rest" :get :parameters] count)
                (update-in [:paths "/rest" :get :responses] (comp set keys))
                (update-in [:paths "/rest" :post :parameters] count)
                (update-in [:paths "/rest" :post :responses] (comp set keys)))))))
  (testing "top-level handler doesn't contribute to docs"
    (let [app (api
                (swagger-routes)
                (context "/rest" []
                  (resource
                    {:handler (constantly (ok {:total 1}))})))
          spec (get-spec app)]

      (is (= {} (:paths spec))))))
