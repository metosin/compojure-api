(ns compojure.api.meta-test
  (:require [compojure.api.sweet :as sweet :refer :all]
            [compojure.api.meta :as meta :refer [merge-parameters static-context routing]]
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

(def macroexpand-2 (comp macroexpand-1 macroexpand-1))

(defn is-thrown-with-msg?* [is* ^Class cls re form f]
  (try (f)
       (is* false (str "Expected to throw: " form))
       (catch Throwable outer
         (let [encountered-class-match (atom false)]
           (loop [e outer]
             (let [matches-class (instance? cls e)]
               (swap! encountered-class-match #(or % matches-class))
               (if (and matches-class
                        (some->> (ex-message e) (re-find re)))
                 (is* true "")
                 (let [e' (ex-cause e)]
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
                    (static-context "/a"
                      (routing [(~'POST "/ping" [])]))})))
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
                      (routing [(~'POST "/ping" [])]))})))
  (testing "static-context"
    (is-expands (static-context "/a"
                                (routing [(POST "/ping" [])]))
                `(make-context
                   {:__record__ "clout.core.CompiledRoute",
                    :source "/a:__path-info",
                    :re #"/a(|/.*)",
                    :keys [:__path-info],
                    :absolute? false}
                   (let [?r ~'(routing [(POST "/ping" [])])]
                     (fn [?_] ?r))))))

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
                       :return (do (swap! times inc) String)
                       (ok "kikka")))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "dynamic context where schema is bound outside context"
    (let [times (atom 0)
          route (let [s String]
                  (context
                    "" []
                    :dynamic true
                    (GET "/ping" []
                         ;;TODO could lift this since the locals occur outside the context
                         :return (do (swap! times inc) s)
                         (ok "kikka"))))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
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
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 11} @times))))
  (testing "idea for lifting impl"
    (let [times (atom 0)
          route (let [rs (GET "/ping" req
                              :return (do (swap! times inc)
                                          String)
                              (ok "kikka"))]
                  (context
                    "" []
                    :dynamic true
                    rs))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times)))))

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
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "dynamic context where schema is bound outside context"
    (let [times (atom 0)
          route (let [s s/Any]
                  (context
                    "" []
                    :dynamic true
                    (GET "/ping" []
                         ;;TODO could lift this since the locals occur outside the context
                         :body [body (do (swap! times inc) s)]
                         (ok "kikka"))))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
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
               :body [body (do (swap! times update :outer inc)
                               s/Any)]
               (GET "/ping" req
                    :body [body (do (swap! times update :inner inc)
                                    s/Any)]
                    (ok "kikka"))))))
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
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 11} @times))))
  (testing "idea for lifting impl"
    (let [times (atom 0)
          route (let [rs (GET "/ping" req
                              :body [body (do (swap! times inc)
                                              s/Any)]
                              (ok "kikka"))]
                  (context
                    "" []
                    :dynamic true
                    rs))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times)))))

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
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "dynamic context where schema is bound outside context"
    (let [times (atom 0)
          route (let [s s/Any]
                  (context
                    "" []
                    :dynamic true
                    (GET "/ping" []
                         ;;TODO could lift this since the locals occur outside the context
                         :query [body (do (swap! times inc) s)]
                         (ok "kikka"))))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
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
               :query [body (do (swap! times update :outer inc)
                               s/Any)]
               (GET "/ping" req
                    :query [body (do (swap! times update :inner inc)
                                    s/Any)]
                    (ok "kikka"))))))
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
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 11} @times))))
  (testing "idea for lifting impl"
    (let [times (atom 0)
          route (let [rs (GET "/ping" req
                              :query [body (do (swap! times inc)
                                              s/Any)]
                              (ok "kikka"))]
                  (context
                    "" []
                    :dynamic true
                    rs))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times)))))

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
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "dynamic context where schema is bound outside context"
    (let [times (atom 0)
          route (let [s String]
                  (context
                    "" []
                    :dynamic true
                    (GET "/ping" []
                         ;;TODO could lift this since the locals occur outside the context
                         :responses {200 (do (swap! times inc) s)}
                         (ok "kikka"))))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
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
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "bind :return in static context"
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
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 1} @times))))
  (testing "bind :return in dynamic context"
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
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 11} @times))))
  (testing "idea for lifting impl"
    (let [times (atom 0)
          route (let [rs (GET "/ping" req
                              :responses {200 (do (swap! times inc)
                                                  String)}
                              (ok "kikka"))]
                  (context
                    "" []
                    :dynamic true
                    rs))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times)))))

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
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "dynamic context where schema is bound outside context"
    (let [times (atom 0)
          route (let [s s/Any]
                  (context
                    "" []
                    :dynamic true
                    (GET "/ping" []
                         ;;TODO could lift this since the locals occur outside the context
                         :headers [body (do (swap! times inc) s)]
                         (ok "kikka"))))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
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
               :headers [body (do (swap! times update :outer inc)
                               s/Any)]
               (GET "/ping" req
                    :headers [body (do (swap! times update :inner inc)
                                    s/Any)]
                    (ok "kikka"))))))
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
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 11} @times))))
  (testing "idea for lifting impl"
    (let [times (atom 0)
          route (let [rs (GET "/ping" req
                              :headers [body (do (swap! times inc)
                                              s/Any)]
                              (ok "kikka"))]
                  (context
                    "" []
                    :dynamic true
                    rs))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times)))))

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
                   [?G__51754 EXPENSIVE
                    ?G__51755 schema.core/Any
                    ?G__51756
                    s/Int
                    ?G__51759
                    s/Keyword
                    ?G__51760
                    s/Keyword
                    ?G__51762 {?G__51759 ?G__51760,
                               :field ?G__51754,
                               :field2 ?G__51755,
                               (clojure.core/with-meta
                                 (schema.core/optional-key :default)
                                 {:default '(inc 42)})
                               ?G__51756}]
                   (compojure.api.routes/map->Route
                     {:path "/ping",
                      :method :get,
                      :info
                      (compojure.api.meta/merge-parameters
                        {:public {:parameters {:body ?G__51762}}}),
                      :handler
                      (compojure.core/make-route
                        :get
                        {:__record__ "clout.core.CompiledRoute",
                         :source "/ping",
                         :re (clojure.core/re-pattern "/ping"),
                         :keys [],
                         :absolute? false}
                        (clojure.core/fn [?request]
                          (compojure.core/let-request
                            [[:as +compojure-api-request+] ?request]
                            (plumbing.core/letk
                              [[field :- ?G__51754
                                field2 :- ?G__51755
                                {default :-, ?G__51756 (inc 42)}
                                :& foo :- {?G__51759 ?G__51760}
                                :as all]
                               (compojure.api.coercion/coerce-request!
                                 ?G__51762
                                 :body-params
                                 :body
                                 true
                                 false
                                 +compojure-api-request+)]
                              (do (ok "kikka"))))))}))))
  (testing "no context"
    (let [times (atom 0)
          route (GET "/ping" []
                     :body [body (do (swap! times inc) s/Any)]
                     (ok "kikka"))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
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
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 11 @times))))
  (testing "dynamic context where schema is bound outside context"
    (let [times (atom 0)
          route (let [s s/Any]
                  (context
                    "" []
                    :dynamic true
                    (GET "/ping" []
                         ;;TODO could lift this since the locals occur outside the context
                         :body [body (do (swap! times inc) s)]
                         (ok "kikka"))))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
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
               :body [body (do (swap! times update :outer inc)
                               s/Any)]
               (GET "/ping" req
                    :body [body (do (swap! times update :inner inc)
                                    s/Any)]
                    (ok "kikka"))))))
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
      (exercise)
      (is (= {:outer 1 :inner 1} @times))
      (dorun (repeatedly 10 exercise))
      (is (= {:outer 1 :inner 11} @times))))
  (testing "idea for lifting impl"
    (let [times (atom 0)
          route (let [rs (GET "/ping" req
                              :body [body (do (swap! times inc)
                                              s/Any)]
                              (ok "kikka"))]
                  (context
                    "" []
                    :dynamic true
                    rs))
          exercise #(is (= "kikka" (:body (route {:request-method :get :uri "/ping"}))))]
      (exercise)
      (is (= 1 @times))
      (dorun (repeatedly 10 exercise))
      (is (= 1 @times)))))
