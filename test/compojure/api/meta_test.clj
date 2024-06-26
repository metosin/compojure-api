(ns compojure.api.meta-test
  (:require [compojure.api.sweet :as sweet :refer :all]
            [compojure.api.meta :as meta :refer [merge-parameters routing]]
            [compojure.api.common :refer [merge-vector]]
            [compojure.api.compojure-compat :refer [make-context]]
            [clojure.data :as data]
            [compojure.core :as cc :refer [let-request make-route wrap-routes]]
            [clojure.walk :as walk]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.test :refer [deftest is testing]]
            [compojure.api.test-domain :refer [Pizza burger-routes]]
            [compojure.api.test-utils :refer :all]
            [compojure.api.exception :as ex]
            [compojure.api.swagger :as swagger]
            [ring.util.http-response :refer :all]
            [ring.util.http-predicates :as http]
            [schema.core :as s]
            [ring.swagger.core :as rsc]
            [ring.util.http-status :as status]
            [compojure.api.middleware :as mw :refer [compose-middleware]]
            [compojure.api.coercion :refer [coerce-request! wrap-coerce-response]]
            [ring.swagger.middleware :as rsm]
            [compojure.api.validator :as validator]
            [compojure.api.request :as request]
            [compojure.api.routes :as routes :refer [map->Route]]
            [muuntaja.core :as m]
            [compojure.api.core :as c]
            [clojure.java.io :as io]
            [muuntaja.format.msgpack]
            [muuntaja.format.yaml]
            [clojure.core.unify :as unify])
  (:import (java.sql SQLException SQLWarning)
           (java.util.regex Pattern)
           (muuntaja.protocols StreamableResponse)
           (java.io File ByteArrayInputStream)))

(set! *warn-on-reflection* true)

(def macroexpand-2 (comp macroexpand-1 macroexpand-1))

(defn is-thrown-with-msg?* [is* ^Class cls re form f]
  (try (f)
       (is* false (str "Expected to throw: " form))
       (catch Throwable outer
         (let [encountered-class-match (atom false)]
           (loop [^Throwable e outer]
             (let [matches-class (instance? cls e)]
               (swap! encountered-class-match #(or % matches-class))
               (if (and matches-class
                        (some->> (.getMessage e) (re-find re)))
                 (is* true "")
                 (let [e' (some-> e .getCause)]
                   (if (identical? e' e)
                     (if @encountered-class-match
                       (is* false (str "Did not match exception message:\n"
                                       (pr-str outer)))
                       (is* false (str "Did not find an exception of class " (.getName cls) ":\n"
                                       (pr-str outer))))
                     (recur e'))))))))))

(defmacro ^:private is-thrown-with-msg?-with-is-fn [is* cls re e]
  `(is-thrown-with-msg?* ~is* ~cls ~re '~e #(do ~e)))

(defmacro is-thrown-with-msg? [cls re e]
  `(is-thrown-with-msg?-with-is-fn (fn [v# msg#] (is v# msg#)) ~cls ~re ~e))

(defn subst-regex [^Pattern regex]
  `(re-pattern ~(.pattern regex)))

(defn- reify-records [form]
  (walk/prewalk (fn [s]
                  (if (record? s)
                    (do (assert (not (contains? s :__record__)))
                        (into {:__record__ (.getName (class s))} s))
                    s))
                 form))

(defn- massage-expansion [form]
  (walk/postwalk (fn [s]
                   (when (symbol? s)
                     (assert (not (str/starts-with? (name s) "?"))
                             "Form not allowed lvars"))
                   (if (instance? Pattern s)
                     (subst-regex s)
                     (if (= '& s)
                       :& ;; clojure.core.unify/wildcard?
                       s)))
                 (reify-records form)))

(defn is-expands* [nsym form & expecteds]
  (binding [*ns* (the-ns nsym)]
    (let [expecteds (or expecteds [(list (gensym 'dummy))])
          ;; support `(let [?a 1] 1) => '(clojure.core/let [?a 1] 1)
          ;; without having the lvars be qualified
          expecteds (mapv (fn [expected]
                            (assert (and (seq? expected) (seq expected)))
                            (let [fexpected (first expected)]
                              (assert (symbol? fexpected) "First form of expected should be a symbol")
                              (assert (not (str/starts-with? (name fexpected) "?")))
                              (->> expected
                                   reify-records
                                   (walk/postwalk (fn [s]
                                                    (if (and (symbol? s)
                                                             (or (str/starts-with? (name s) "?")
                                                                 (= "+compojure-api-request+" (name s))))
                                                      (symbol nil (name s))
                                                      (if (instance? Pattern s)
                                                        (subst-regex s)
                                                        (if (= '& s)
                                                          :& ;; clojure.core.unify/wildcard?
                                                          s))))))))
                          expecteds)]
      (loop [form' (macroexpand-1 form)
             seen [form form']
             [expected :as expecteds] expecteds]
        (if-not (seq expecteds)
          (is true)
          (if-not (and (seq? form') (seq form'))
            (is false)
            (let [fform' (first form')
                  fexpected (first expected)]
              (when (symbol? fform')
                (assert (not (str/starts-with? (name fform') "?"))))
              (if (= fform' fexpected)
                (let [actual-form form'
                      form' (massage-expansion form')
                      unifies (unify/unify form' expected)
                      subst-expected (some->> unifies (unify/subst expected))]
                  (if (and unifies (= form' subst-expected))
                    (if-some [expecteds (next expecteds)]
                      (let [actual-form' (macroexpand-1 actual-form)]
                        (if (identical? actual-form actual-form')
                          (is (empty? expecteds)
                              (str "No expansions matched pattern:\n"
                                   (with-out-str (pp/pprint expected))
                                   "Seen:\n"
                                   (with-out-str
                                     (run! pp/pprint (interpose '=> (map massage-expansion seen))))))
                          (recur actual-form' (conj seen actual-form') expecteds)))
                      (is true))
                    (is false
                        (str "Did not match pattern:\n"
                             (with-out-str (pp/pprint expected))
                             "\nExpansion:\n"
                             (with-out-str (pp/pprint form'))
                             (if unifies
                               (str "\nUnifies to:\n"
                                    (with-out-str (pp/pprint subst-expected))
                                    "With substitution map:\n"
                                    (with-out-str (pp/pprint unifies)))
                               (str "\nDoes not unify\n"))))))
                (let [form'' (macroexpand-1 form')]
                  (if-not (identical? form' form'')
                    (recur form'' (conj seen form'') expecteds)
                    (is false
                        (str "No expansions matched pattern:\n"
                             (with-out-str (pp/pprint expected))
                             "Seen:\n"
                             (with-out-str
                               (run! pp/pprint (interpose '=> (map massage-expansion seen))))))))))))))))

(defmacro is-expands [form & expected-exprs]
  `(is-expands* '~(ns-name *ns*) '~form ~@expected-exprs))

(comment
  (unify/unifier-
    '(+ 1 2)
    '(+ ?a ?b))

  (unify/unifier-
    '(+ 2)
    '(+ ?a ?b))

  (is-expands (+ 1 a)
              (+ 1))

  (unify/unify `(+ 1 a#) `(+ 1 ?a))
  (unify/unify `(+ 1) `(?a/+ 1))

  (is-expands* *ns* `(+ 1 a#) `(+ 1 ?a))
  (expands `(+ 1 a#) `(+ 1 ?a))

  (unify/subst
    '(+ ?a ?b)
    (unify/unify
      `(~'+ a#)
      '(+ ?a ?b)
      ))


  (let [orig ])

  (-> `(GET "/ping" []
            :return String
            (ok "kikka"))
    macroexpand
    pp/pprint
    )

  (-> (take 4 (iterate macroexpand-1 `(sweet/POST "/ping" []) ))
      (nth 2)
      second
      :handler
      (nth 2)
      prn
      )
  )

(deftest meta-expansion-test
  (is-expands (sweet/GET "/ping" [])
              `(c/GET "/ping" [])
              `(map->Route
                 {:path "/ping",
                  :method :get,
                  :info (merge-parameters {}),
                  :handler
                  (make-route
                    :get
                    {:__record__ "clout.core.CompiledRoute"
                     :source "/ping",
                     :re #"/ping",
                     :keys [],
                     :absolute? false}
                    (fn [?request]
                      (let-request [[:as +compojure-api-request+] ?request]
                        (do))))}))
  (is-expands (sweet/POST "/ping" [])
              `(c/POST "/ping" [])
              `(map->Route
                 {:path "/ping",
                  :method :post,
                  :info (merge-parameters {}),
                  :handler
                  (make-route
                    :post
                    {:__record__ "clout.core.CompiledRoute"
                     :source "/ping",
                     :re #"/ping",
                     :keys [],
                     :absolute? false}
                    (fn [?request]
                      (let-request [[:as +compojure-api-request+] ?request]
                        (do))))}))
  (testing "static context"
    (is-expands (context "/a" [] (POST "/ping" []))
                `(c/context "/a" [] (~'POST "/ping" []))
                `(map->Route
                   {:path "/a",
                    :childs
                    (delay
                      (flatten
                        ((fn [+compojure-api-request+]
                           (let-request
                             [[:as +compojure-api-request+] +compojure-api-request+]
                             [(~'POST "/ping" [])]))
                         {}))),
                    :method nil,
                    :info (merge-parameters {:static-context? true}),
                    :handler
                    (let [?form (routing [(~'POST "/ping" [])])]
                      (cc/context
                        "/a"
                        [:as +compojure-api-request+]
                        ?form))})))
  (testing "dynamic context"
    (is-expands (context "/a" [] :dynamic true (POST "/ping" []))
                `(map->Route
                   {:path "/a",
                    :childs
                    (delay
                      (flatten
                        ((fn [+compojure-api-request+]
                           (let-request
                             [[:as +compojure-api-request+] +compojure-api-request+]
                             [(~'POST "/ping" [])]))
                         {}))),
                    :method nil,
                    :info (merge-parameters {:public {:dynamic true}}),
                    :handler
                    (cc/context "/a"
                      [:as +compojure-api-request+]
                      (routing [(~'POST "/ping" [])]))}))))

(deftest is-thrown-with-msg?-test
  (is-thrown-with-msg? Exception #"message" (throw (Exception. "message")))
  (is-thrown-with-msg? AssertionError #"Assert failed" (assert nil))
  (is-thrown-with-msg? Exception #"message" (throw (RuntimeException. (Exception. "message"))))
  (let [a (atom [])
        _ (is-thrown-with-msg?-with-is-fn
            (fn [& args] (swap! a conj args))
            AssertionError
            #"message"
            (throw (RuntimeException. (Exception. "message"))))]
    (let [[ret :as all] @a]
      (when (is (= 1 (count all)))
        (is (= false (first ret)))
        (is (str/starts-with? (second ret) "Did not find an exception of class java.lang.AssertionError"))))))

(deftest bad-body-test
  (is-thrown-with-msg? 
    AssertionError
    #""
    (macroexpand-2
      `(GET "/ping" []
            :body ~'[body :- EXPENSIVE]
            (ok "kikka")))))

(deftest return-double-eval-test
  (testing "no :return double expansion"
    (is-expands (GET "/ping" []
                     :return EXPENSIVE
                     (ok "kikka"))
                `(let [?return {200 {:schema ~'EXPENSIVE, :description ""}}]
                   (map->Route
                     {:path "/ping",
                      :method :get,
                      :info (merge-parameters
                              {:public {:responses [?return]}}),
                      :handler
                      (wrap-routes
                        (make-route
                          :get
                          {:__record__ "clout.core.CompiledRoute",
                           :source "/ping",
                           :re #"/ping",
                           :keys [],
                           :absolute? false}
                          (fn [?request]
                            (let-request [[:as +compojure-api-request+] ?request]
                              (do ~'(ok "kikka")))))
                        (compose-middleware
                          [[wrap-coerce-response
                            (merge-vector
                              [?return])]]))}))))
  (testing "no context"
    (let [times (atom 0)
          route (GET "/ping" []
                     :return (do (swap! times inc) String)
                     (ok "kikka"))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 1 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times))))
  (testing "inferred static context"
    (let [times (atom 0)
          route (context
                  "" []
                  (GET "/ping" []
                       :return (do (swap! times inc) String)
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times))))
  (testing "dynamic context that doesn't bind variables"
    (let [times (atom 0)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping" []
                       :return (do (swap! times inc) String)
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 0 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "dynamic context that binds req and uses it in schema"
    (let [times (atom 0)
          route (context
                  "" req
                  (GET "/ping" req
                       :return (do (swap! times inc)
                                   ;; should never lift this since it refers to request
                                   (second [req String]))
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 0 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "bind :return in static context"
    (let [times (atom {:outer 0 :inner 0})
          route (context
                  "" []
                  :return (do (swap! times update :outer inc)
                              String)
                  (GET "/ping" req
                       :return (do (swap! times update :inner inc)
                                   String)
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= {:outer 1 :inner 1} @times))
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 1} @times))))
  (testing "bind :return in dynamic context"
    (let [times (atom {:outer 0 :inner 0})
          route (context
                  "" []
                  :dynamic true
                  :return (do (swap! times update :outer inc)
                              String)
                  (GET "/ping" req
                       :return (do (swap! times update :inner inc)
                                   String)
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= {:outer 1 :inner 0} @times))
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 11} @times)))))

(deftest coercion-double-eval-test
  (testing "no :coercion double expansion"
    (is-expands (GET "/ping" []
                     :coercion EXPENSIVE
                     (ok "kikka"))
                '(clojure.core/let
                   [?coercion172603 EXPENSIVE]
                   (compojure.api.routes/map->Route
                     {:path "/ping",
                      :method :get,
                      :info
                      (compojure.api.meta/merge-parameters {:coercion ?coercion172603}),
                      :handler
                      (compojure.core/wrap-routes
                        (compojure.core/make-route
                          :get
                          {:__record__ "clout.core.CompiledRoute",
                           :source "/ping",
                           :re (clojure.core/re-pattern "/ping"),
                           :keys [],
                           :absolute? false}
                          (clojure.core/fn
                            [?request__3574__auto__]
                            (compojure.core/let-request
                              [[:as +compojure-api-request+] ?request__3574__auto__]
                              (do (ok "kikka")))))
                        (compojure.api.middleware/compose-middleware
                          [[compojure.api.middleware/wrap-coercion ?coercion172603]]))}))))
  (testing "no context"
    (let [times (atom 0)
          route (GET "/ping" []
                     :coercion (do (swap! times inc) identity)
                     (ok "kikka"))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 1 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times))))
  (testing "inferred static context"
    (let [times (atom 0)
          route (context
                  "" []
                  (GET "/ping" []
                       :coercion (do (swap! times inc) identity)
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times))))
  (testing "dynamic context that doesn't bind variables"
    (let [times (atom 0)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping" []
                       :coercion (do (swap! times inc) identity)
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 0 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "dynamic context that binds req and uses it in schema"
    (let [times (atom 0)
          route (context
                  "" req
                  (GET "/ping" req
                       :coercion (do (swap! times inc)
                                   ;; should never lift this since it refers to request
                                   (second [req identity]))
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 0 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "bind :coercion in static context"
    (let [times (atom {:outer 0 :inner 0})
          route (context
                  "" []
                  :coercion (do (swap! times update :outer inc)
                              String)
                  (GET "/ping" req
                       :coercion (do (swap! times update :inner inc)
                                   identity)
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= {:outer 1 :inner 1} @times))
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 1} @times))))
  (testing "bind :coercion in dynamic context"
    (let [times (atom {:outer 0 :inner 0})
          route (context
                  "" []
                  :dynamic true
                  :coercion (do (swap! times update :outer inc)
                              identity)
                  (GET "/ping" req
                       :coercion (do (swap! times update :inner inc)
                                   identity)
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= {:outer 1 :inner 0} @times))
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 11} @times)))))

(deftest body-double-eval-test
  (testing "no :body double expansion"
    (is-expands (GET "/ping" []
                     :body [body EXPENSIVE]
                     (ok "kikka"))
                `(let [?body-schema ~'EXPENSIVE]
                   (map->Route
                     {:path "/ping",
                      :method :get,
                      :info (merge-parameters
                              {:public {:parameters {:body ?body}}})
                      :handler
                      (make-route
                        :get
                        {:__record__ "clout.core.CompiledRoute",
                         :source "/ping",
                         :re #"/ping",
                         :keys [],
                         :absolute? false}
                        (fn [?request]
                          (let-request [[:as +compojure-api-request+] ?request]
                            (let [~'body (compojure.api.coercion/coerce-request!
                                           ?body
                                           :body-params
                                           :body
                                           false
                                           false
                                           +compojure-api-request+)]
                              (do ~'(ok "kikka"))))))}))))
  (testing "no context"
    (let [times (atom 0)
          route (GET "/ping" []
                     :body [body (do (swap! times inc) s/Any)]
                     (ok "kikka"))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 1 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times))))
  (testing "inferred static context"
    (let [times (atom 0)
          route (context
                  "" []
                  (GET "/ping" []
                       :body [body (do (swap! times inc) s/Any)]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 1 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times))))
  (testing "dynamic context that doesn't bind variables"
    (let [times (atom 0)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping" []
                       :body [body (do (swap! times inc) s/Any)]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 0 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "dynamic context that binds req and uses it in schema"
    (let [times (atom 0)
          route (context
                  "" req
                  (GET "/ping" req
                       :body [body (do (swap! times inc)
                                       ;; should never lift this since it refers to request
                                       (second [req s/Any]))]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 0 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "bind :body in static context"
    (is-thrown-with-msg?
      AssertionError
      #"cannot be :static"
      (eval `(context
               "" []
               :static true
               :body [body s/Any]))))
  (testing "bind :body in dynamic context"
    (let [times (atom {:outer 0 :inner 0})
          route (context
                  "" []
                  :dynamic true
                  :body [body (do (swap! times update :outer inc)
                                  s/Any)]
                  (GET "/ping" req
                       :body [body (do (swap! times update :inner inc)
                                       s/Any)]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= {:outer 1 :inner 0} @times))
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 11} @times)))))

(deftest query-double-eval-test
  (testing "no :query double expansion"
    (is-expands (GET "/ping" []
                     :query [query EXPENSIVE]
                     (ok "kikka"))
                `(let [?query-schema ~'EXPENSIVE]
                   (map->Route
                     {:path "/ping",
                      :method :get,
                      :info (merge-parameters
                              {:public {:parameters {:query ?query-schema}}})
                      :handler
                      (make-route
                        :get
                        {:__record__ "clout.core.CompiledRoute",
                         :source "/ping",
                         :re #"/ping",
                         :keys [],
                         :absolute? false}
                        (fn [?request]
                          (let-request [[:as +compojure-api-request+] ?request]
                            (let [~'query (compojure.api.coercion/coerce-request!
                                            ?query-schema
                                            :query-params
                                            :string
                                            true
                                            false
                                            +compojure-api-request+)]
                              (do ~'(ok "kikka"))))))}))))
  (testing "no context"
    (let [times (atom 0)
          route (GET "/ping" []
                     :query [body (do (swap! times inc) s/Any)]
                     (ok "kikka"))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 1 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times))))
  (testing "inferred static context"
    (let [times (atom 0)
          route (context
                  "" []
                  (GET "/ping" []
                       :query [body (do (swap! times inc) s/Any)]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 1 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times))))
  (testing "dynamic context that doesn't bind variables"
    (let [times (atom 0)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping" []
                       :query [body (do (swap! times inc) s/Any)]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 0 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "dynamic context that binds req and uses it in schema"
    (let [times (atom 0)
          route (context
                  "" req
                  (GET "/ping" req
                       :query [body (do (swap! times inc)
                                       ;; should never lift this since it refers to request
                                       (second [req s/Any]))]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 0 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "bind :query in static context"
    (is-thrown-with-msg?
      AssertionError
      #"cannot be :static"
      (eval `(context
               "" []
               :static true
               :query [body# s/Any]))))
  (testing "bind :query in dynamic context"
    (let [times (atom {:outer 0 :inner 0})
          route (context
                  "" []
                  :dynamic true
                  :query [body (do (swap! times update :outer inc)
                                  s/Any)]
                  (GET "/ping" req
                       :query [body (do (swap! times update :inner inc)
                                       s/Any)]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= {:outer 1 :inner 0} @times))
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 11} @times)))))

(deftest responses-double-eval-test
  (testing "no :responses double expansion"
    (is-expands (GET "/ping" []
                     :responses {200 {:schema EXPENSIVE}}
                     (ok "kikka"))
                `(let [?responses {200 {:schema ~'EXPENSIVE}}]
                   (map->Route
                     {:path "/ping",
                      :method :get,
                      :info (merge-parameters
                              {:public {:responses [?responses]}}),
                      :handler
                      (wrap-routes
                        (make-route
                          :get
                          {:__record__ "clout.core.CompiledRoute",
                           :source "/ping",
                           :re #"/ping",
                           :keys [],
                           :absolute? false}
                          (fn [?request]
                            (let-request [[:as +compojure-api-request+] ?request]
                              (do ~'(ok "kikka")))))
                        (compose-middleware
                          [[wrap-coerce-response
                            (merge-vector
                              [?responses])]]))}))))
  (testing "no context"
    (let [times (atom 0)
          route (GET "/ping" []
                     :responses {200 (do (swap! times inc) String)}
                     (ok "kikka"))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 1 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times))))
  (testing "inferred static context"
    (let [times (atom 0)
          route (context
                  "" []
                  (GET "/ping" []
                       :responses {200 (do (swap! times inc) String)}
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 1 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times))))
  (testing "dynamic context that doesn't bind variables"
    (let [times (atom 0)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping" []
                       :responses {200 (do (swap! times inc) String)}
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 0 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "dynamic context that binds req and uses it in schema"
    (let [times (atom 0)
          route (context
                  "" req
                  (GET "/ping" req
                       :responses {200 (do (swap! times inc)
                                           ;; should never lift this since it refers to request
                                           (second [req String]))}
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 0 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "bind :responses in static context"
    (let [times (atom {:outer 0 :inner 0})
          route (context
                  "" []
                  :responses {200 (do (swap! times update :outer inc)
                                      String)}
                  (GET "/ping" req
                       :responses {200 (do (swap! times update :inner inc)
                                           String)}
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= {:outer 1 :inner 1} @times))
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 1} @times))))
  (testing "bind :responses in dynamic context"
    (let [times (atom {:outer 0 :inner 0})
          route (context
                  "" []
                  :dynamic true
                  :responses {200 (do (swap! times update :outer inc)
                                      String)}
                  (GET "/ping" req
                       :responses {200 (do (swap! times update :inner inc)
                                           String)}
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= {:outer 1 :inner 0} @times))
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 11} @times)))))

(deftest headers-double-eval-test
  (testing "no :headers double expansion"
    (is-expands (GET "/ping" []
                     :headers [headers EXPENSIVE]
                     (ok "kikka"))
                `(let [?headers-schema ~'EXPENSIVE]
                   (map->Route
                     {:path "/ping",
                      :method :get,
                      :info (merge-parameters
                              {:public {:parameters {:header ?headers-schema}}})
                      :handler
                      (make-route
                        :get
                        {:__record__ "clout.core.CompiledRoute",
                         :source "/ping",
                         :re #"/ping",
                         :keys [],
                         :absolute? false}
                        (fn [?request]
                          (let-request [[:as +compojure-api-request+] ?request]
                            (let [~'headers (compojure.api.coercion/coerce-request!
                                            ?headers-schema
                                            :headers
                                            :string
                                            true
                                            false
                                            +compojure-api-request+)]
                              (do ~'(ok "kikka"))))))}))))
  (testing "no context"
    (let [times (atom 0)
          route (GET "/ping" []
                     :headers [body (do (swap! times inc) s/Any)]
                     (ok "kikka"))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 1 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times))))
  (testing "inferred static context"
    (let [times (atom 0)
          route (context
                  "" []
                  (GET "/ping" []
                       :headers [body (do (swap! times inc) s/Any)]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 1 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times))))
  (testing "dynamic context that doesn't bind variables"
    (let [times (atom 0)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping" []
                       :headers [body (do (swap! times inc) s/Any)]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 0 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "dynamic context that binds req and uses it in schema"
    (let [times (atom 0)
          route (context
                  "" req
                  (GET "/ping" req
                       :headers [body (do (swap! times inc)
                                       ;; should never lift this since it refers to request
                                       (second [req s/Any]))]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= 0 @times))
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "bind :headers in static context"
    (is-thrown-with-msg?
      AssertionError
      #"cannot be :static"
      (eval `(context
               "" []
               :static true
               :headers [body# s/Any]))))
  (testing "bind :headers in dynamic context"
    (let [times (atom {:outer 0 :inner 0})
          route (context
                  "" []
                  :dynamic true
                  :headers [body (do (swap! times update :outer inc)
                                  s/Any)]
                  (GET "/ping" req
                       :headers [body (do (swap! times update :inner inc)
                                       s/Any)]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (is (= {:outer 1 :inner 0} @times))
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 11} @times)))))

(comment
  (let [EXPENSIVE 1]
    (GET "/ping" []
         :body-params [field :- EXPENSIVE, field2, {default :- s/Int 42} & foo :- {s/Keyword s/Keyword} :as all]
         (ok "kikka")))
  (macroexpand-1 (GET "/ping" []
         :body-params [{field :-}]
         (ok "kikka")))
  (macroexpand-1 (GET "/ping" []
         :body-params [:as b :- s/Int]
         (ok "kikka")))
  (macroexpand-1
    '(plumbing.core/letk
      [[field :- ?field-schema
        field2 :- ?field2-schema
        {default :- ?default-schema (inc 42)}
        & foo :- {?extra-keys ?extra-vals}
        :as all]
       (coerce-request! ?body-schema :body-params :body true false +compojure-api-request+)]
      nil))
)

(deftest body-params-double-eval-test
  (testing "no :body-params double expansion"
    (is-expands (GET "/ping" []
                     :body-params [field :- EXPENSIVE, field2, {default :- s/Int (inc 42)} & foo :- {s/Keyword s/Keyword} :as all]
                     (ok "kikka"))
                '(clojure.core/let
                   [?body-params-schema {s/Keyword s/Keyword,
                                         :field EXPENSIVE,
                                         :field2 schema.core/Any,
                                         (clojure.core/with-meta
                                           (schema.core/optional-key :default)
                                           {:default '(inc 42)})
                                         s/Int}]
                   (compojure.api.routes/map->Route
                     {:path "/ping",
                      :method :get,
                      :info
                      (compojure.api.meta/merge-parameters
                        {:public {:parameters {:body ?body-params-schema}}}),
                      :handler
                      (compojure.core/make-route
                        :get
                        {:__record__ "clout.core.CompiledRoute",
                         :source "/ping",
                         :re (clojure.core/re-pattern "/ping"),
                         :keys [],
                         :absolute? false}
                        (clojure.core/fn
                          [?request]
                          (compojure.core/let-request
                            [[:as +compojure-api-request+] ?request]
                            (plumbing.core/letk
                              ;; Note: these schemas are just cosmetic. if a future plumbing uses
                              ;; them, the runtime tests below will fail.
                              [[field :- EXPENSIVE
                                ;;Note: default is recalculated each time
                                field2 {default :- s/Int (inc 42)}
                                :& foo :- {s/Keyword s/Keyword}
                                :as all]
                               (compojure.api.coercion/coerce-request!
                                 ?body-params-schema
                                 :body-params
                                 :body
                                 true
                                 false
                                 +compojure-api-request+)]
                              (do (ok "kikka"))))))}))))
  (testing "no context"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (GET "/ping" []
                     :body-params [field :- (record :field s/Str)
                                   field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                   & foo :- {(record :extra-keys s/Keyword)
                                             (record :extra-vals s/Keyword)} :as all]
                     (ok "kikka"))
          exercise #(is (= "kikka" (:body (route {:body-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 11} @times))))
  (testing "inferred static context"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" []
                  (GET "/ping" []
                       :body-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:body-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 11} @times))))
  (testing "dynamic context that doesn't bind variables"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping" []
                       :body-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:body-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 11 :default 11 :extra-keys 11 :extra-vals 11  :default-never 11} @times))))
  (testing "dynamic context that binds req and uses it in schema"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" req
                  (GET "/ping" req
                       :body-params [field :- (record :field (second [req String]))
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:body-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 11 :default 11 :extra-keys 11 :extra-vals 11  :default-never 11} @times))))
  (testing "bind :body-params in static context"
    (is-thrown-with-msg?
      AssertionError
      #"cannot be :static"
      (eval `(context
               "" []
               :static true
               :body-params [field# :- s/Str]))))
  (testing "bind :body-params in dynamic context"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping" req
                       :body-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:body-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 11 :default 11 :extra-keys 11 :extra-vals 11  :default-never 11} @times)))))

(deftest form-params-double-eval-test
  (testing "no :form-params double expansion"
    (is-expands (GET "/ping" []
                     :form-params [field :- EXPENSIVE, field2, {default :- s/Int (inc 42)} & foo :- {s/Keyword s/Keyword} :as all]
                     (ok "kikka"))
                '(clojure.core/let
                   [?form-params-schema108882
                    {s/Keyword s/Keyword,
                     :field EXPENSIVE,
                     :field2 schema.core/Any,
                     (clojure.core/with-meta
                       (schema.core/optional-key :default)
                       {:default '(inc 42)})
                     s/Int}]
                   (compojure.api.routes/map->Route
                     {:path "/ping",
                      :method :get,
                      :info
                      (compojure.api.meta/merge-parameters
                        {:public
                         {:parameters {:formData ?form-params-schema108882},
                          :consumes ["application/x-www-form-urlencoded"]}}),
                      :handler
                      (compojure.core/make-route
                        :get
                        {:__record__ "clout.core.CompiledRoute",
                         :source "/ping",
                         :re (clojure.core/re-pattern "/ping"),
                         :keys [],
                         :absolute? false}
                        (clojure.core/fn
                          [?request__3574__auto__]
                          (compojure.core/let-request
                            [[:as +compojure-api-request+] ?request__3574__auto__]
                            (plumbing.core/letk
                              [[field :- EXPENSIVE
                                field2 {default :-, s/Int (inc 42)}
                                :& foo :- {s/Keyword s/Keyword}
                                :as all]
                               (compojure.api.coercion/coerce-request!
                                 ?form-params-schema108882
                                 :form-params
                                 :string
                                 true
                                 false
                                 +compojure-api-request+)]
                              (do (ok "kikka"))))))}))))
  (testing "no context"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (GET "/ping" []
                     :form-params [field :- (record :field s/Str)
                                   field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                   & foo :- {(record :extra-keys s/Keyword)
                                             (record :extra-vals s/Keyword)} :as all]
                     (ok "kikka"))
          exercise #(is (= "kikka" (:body (route {:form-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 11} @times))))
  (testing "inferred static context"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" []
                  (GET "/ping" []
                       :form-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:form-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 11} @times))))
  (testing "dynamic context that doesn't bind variables"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping" []
                       :form-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:form-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 11 :default 11 :extra-keys 11 :extra-vals 11  :default-never 11} @times))))
  (testing "dynamic context that binds req and uses it in schema"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" req
                  (GET "/ping" req
                       :form-params [field :- (record :field (second [req String]))
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:form-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 11 :default 11 :extra-keys 11 :extra-vals 11  :default-never 11} @times))))
  (testing "bind :form-params in static context"
    (is-thrown-with-msg?
      AssertionError
      #"cannot be :static"
      (eval `(context
               "" []
               :static true
               :form-params [field# :- s/Str]))))
  (testing "bind :form-params in dynamic context"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping" req
                       :form-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:form-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 11 :default 11 :extra-keys 11 :extra-vals 11  :default-never 11} @times)))))

(deftest multipart-params-double-eval-test
  (testing "no :multipart-params double expansion"
    (is-expands (GET "/ping" []
                     :multipart-params [field :- EXPENSIVE, field2, {default :- s/Int (inc 42)} & foo :- {s/Keyword s/Keyword} :as all]
                     (ok "kikka"))
                '(clojure.core/let
                   [?multipart-params-schema108882
                    {s/Keyword s/Keyword,
                     :field EXPENSIVE,
                     :field2 schema.core/Any,
                     (clojure.core/with-meta
                       (schema.core/optional-key :default)
                       {:default '(inc 42)})
                     s/Int}]
                   (compojure.api.routes/map->Route
                     {:path "/ping",
                      :method :get,
                      :info
                      (compojure.api.meta/merge-parameters
                        {:public
                         {:parameters {:formData ?multipart-params-schema108882},
                          :consumes ["multipart/form-data"]}}),
                      :handler
                      (compojure.core/make-route
                        :get
                        {:__record__ "clout.core.CompiledRoute",
                         :source "/ping",
                         :re (clojure.core/re-pattern "/ping"),
                         :keys [],
                         :absolute? false}
                        (clojure.core/fn
                          [?request__3574__auto__]
                          (compojure.core/let-request
                            [[:as +compojure-api-request+] ?request__3574__auto__]
                            (plumbing.core/letk
                              [[field :- EXPENSIVE
                                field2 {default :-, s/Int (inc 42)}
                                :& foo :- {s/Keyword s/Keyword}
                                :as all]
                               (compojure.api.coercion/coerce-request!
                                 ?multipart-params-schema108882
                                 :multipart-params
                                 :string
                                 true
                                 false
                                 +compojure-api-request+)]
                              (do (ok "kikka"))))))}))))
  (testing "no context"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (GET "/ping" []
                     :multipart-params [field :- (record :field s/Str)
                                   field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                   & foo :- {(record :extra-keys s/Keyword)
                                             (record :extra-vals s/Keyword)} :as all]
                     (ok "kikka"))
          exercise #(is (= "kikka" (:body (route {:multipart-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 11} @times))))
  (testing "inferred static context"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" []
                  (GET "/ping" []
                       :multipart-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:multipart-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 11} @times))))
  (testing "dynamic context that doesn't bind variables"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping" []
                       :multipart-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:multipart-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 11 :default 11 :extra-keys 11 :extra-vals 11  :default-never 11} @times))))
  (testing "dynamic context that binds req and uses it in schema"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" req
                  (GET "/ping" req
                       :multipart-params [field :- (record :field (second [req String]))
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:multipart-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 11 :default 11 :extra-keys 11 :extra-vals 11  :default-never 11} @times))))
  (testing "bind :multipart-params in static context"
    (is-thrown-with-msg?
      AssertionError
      #"cannot be :static"
      (eval `(context
               "" []
               :static true
               :multipart-params [field# :- s/Str]))))
  (testing "bind :multipart-params in dynamic context"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping" req
                       :multipart-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:multipart-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 11 :default 11 :extra-keys 11 :extra-vals 11  :default-never 11} @times)))))

(deftest query-params-double-eval-test
  (testing "no :query-params double expansion"
    (is-expands (GET "/ping" []
                     :query-params [field :- EXPENSIVE, field2, {default :- s/Int (inc 42)} & foo :- {s/Keyword s/Keyword} :as all]
                     (ok "kikka"))
                '(clojure.core/let
                   [?query-params-schema108882
                    {s/Keyword s/Keyword,
                     :field EXPENSIVE,
                     :field2 schema.core/Any,
                     (clojure.core/with-meta
                       (schema.core/optional-key :default)
                       {:default '(inc 42)})
                     s/Int}]
                   (compojure.api.routes/map->Route
                     {:path "/ping",
                      :method :get,
                      :info
                      (compojure.api.meta/merge-parameters
                        {:public {:parameters {:query ?query-params-schema108882}}}),
                      :handler
                      (compojure.core/make-route
                        :get
                        {:__record__ "clout.core.CompiledRoute",
                         :source "/ping",
                         :re (clojure.core/re-pattern "/ping"),
                         :keys [],
                         :absolute? false}
                        (clojure.core/fn
                          [?request__3574__auto__]
                          (compojure.core/let-request
                            [[:as +compojure-api-request+] ?request__3574__auto__]
                            (plumbing.core/letk
                              [[field :- EXPENSIVE
                                field2 {default :-, s/Int (inc 42)}
                                :& foo :- {s/Keyword s/Keyword}
                                :as all]
                               (compojure.api.coercion/coerce-request!
                                 ?query-params-schema108882
                                 :query-params
                                 :string
                                 true
                                 false
                                 +compojure-api-request+)]
                              (do (ok "kikka"))))))}))))
  (testing "no context"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (GET "/ping" []
                     :query-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                     (ok "kikka"))
          exercise #(is (= "kikka" (:body (route {:query-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 11} @times))))
  (testing "inferred static context"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" []
                  (GET "/ping" []
                       :query-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:query-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 11} @times))))
  (testing "dynamic context that doesn't bind variables"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping" []
                       :query-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:query-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 11 :default 11 :extra-keys 11 :extra-vals 11  :default-never 11} @times))))
  (testing "dynamic context that binds req and uses it in schema"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" req
                  (GET "/ping" req
                       :query-params [field :- (record :field (second [req String]))
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:query-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 11 :default 11 :extra-keys 11 :extra-vals 11  :default-never 11} @times))))
  (testing "bind :query-params in static context"
    (is-thrown-with-msg?
      AssertionError
      #"cannot be :static"
      (eval `(context
               "" []
               :static true
               :query-params [field :- (record :field s/Str)]))))
  (testing "bind :query-params in dynamic context"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping" req
                       :query-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:query-params {:field "a" :field2 2} :request-method :get :uri "/ping"}))))]
      (is (= {} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 11 :default 11 :extra-keys 11 :extra-vals 11  :default-never 11} @times)))))

(deftest path-params-double-eval-test
  (testing "no :path-params double expansion"
    (is-expands (GET "/ping/:field/:field2/:default" []
                     :path-params [field :- EXPENSIVE, field2, {default :- s/Int (inc 42)} & foo :- {s/Keyword s/Keyword} :as all]
                     (ok "kikka"))
                '(clojure.core/let
                   [?form-params-schema152468
                    {s/Keyword s/Keyword,
                     :field EXPENSIVE,
                     :field2 schema.core/Any,
                     (clojure.core/with-meta
                       (schema.core/optional-key :default)
                       {:default '(inc 42)})
                     s/Int}]
                   (compojure.api.routes/map->Route
                     {:path "/ping/:field/:field2/:default",
                      :method :get,
                      :info
                      (compojure.api.meta/merge-parameters
                        {:public {:parameters {:path ?form-params-schema152468}}}),
                      :handler
                      (compojure.core/make-route
                        :get
                        {:__record__ "clout.core.CompiledRoute",
                         :source "/ping/:field/:field2/:default",
                         :re
                         (clojure.core/re-pattern
                           "/ping/([^/,;?]+)/([^/,;?]+)/([^/,;?]+)"),
                         :keys [:field :field2 :default],
                         :absolute? false}
                        (clojure.core/fn
                          [?request__3574__auto__]
                          (compojure.core/let-request
                            [[:as +compojure-api-request+] ?request__3574__auto__]
                            (plumbing.core/letk
                              [[field
                                :-
                                EXPENSIVE
                                field2
                                {default :-, s/Int (inc 42)}
                                :&
                                foo
                                :-
                                #:s{Keyword s/Keyword}
                                :as
                                all]
                               (compojure.api.coercion/coerce-request!
                                 ?form-params-schema152468
                                 :route-params
                                 :string
                                 true
                                 false
                                 +compojure-api-request+)]
                              (do (ok "kikka"))))))}))))
  (testing "no context"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (GET "/ping/:field/:field2/:default" []
                     :path-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                     (ok "kikka"))
          exercise #(is (= "kikka" (:body (route {:path-params {:field "a" :field2 2} :request-method :get :uri "/ping/1/2/3"}))))]
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 11} @times))))
  (testing "inferred static context"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" []
                  (GET "/ping/:field/:field2/:default" []
                       :path-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:path-params {:field "a" :field2 2} :request-method :get :uri "/ping/1/2/3"}))))]
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 11} @times))))
  (testing "dynamic context that doesn't bind variables"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping/:field/:field2/:default" []
                       :path-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:path-params {:field "a" :field2 2} :request-method :get :uri "/ping/1/2/3"}))))]
      (is (= {} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 11 :default 11 :extra-keys 11 :extra-vals 11  :default-never 11} @times))))
  (testing "dynamic context that binds req and uses it in schema"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" req
                  (GET "/ping/:field/:field2/:default" req
                       :path-params [field :- (record :field (second [req String]))
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:path-params {:field "a" :field2 2} :request-method :get :uri "/ping/1/2/3"}))))]
      (is (= {} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 11 :default 11 :extra-keys 11 :extra-vals 11  :default-never 11} @times))))
  (testing "bind :path-params in static context"
    (is-thrown-with-msg?
      AssertionError
      #"cannot be :static"
      (eval `(context
               "" []
               :static true
               :path-params [field :- (record :field s/Str)]))))
  (testing "bind :path-params in dynamic context"
    (let [times (atom {})
          record (fn [path schema] (swap! times update path (fnil inc 0)) schema)
          route (context
                  "" []
                  :dynamic true
                  (GET "/ping/:field/:field2/:default" req
                       :path-params [field :- (record :field s/Str)
                                     field2 {default :- (record :default s/Int) (record :default-never (inc 42))}
                                     & foo :- {(record :extra-keys s/Keyword)
                                               (record :extra-vals s/Keyword)} :as all]
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:path-params {:field "a" :field2 2} :request-method :get :uri "/ping/1/2/3"}))))]
      (is (= {} @times))
      (exercise)
      (is (= {:field 1 :default 1 :extra-keys 1 :extra-vals 1 :default-never 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:field 11 :default 11 :extra-keys 11 :extra-vals 11  :default-never 11} @times)))))
