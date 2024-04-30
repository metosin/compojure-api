(ns compojure.api.meta.analyzer
  (:require [clojure.set :as set]
            [typed.cljc.analyzer :as ana]
            [typed.cljc.analyzer.ast :as ast]
            [typed.clj.analyzer :as ana-clj]
            [typed.cljc.analyzer.passes :as passes]
            [typed.cljc.analyzer.passes.uniquify :as uniquify2]
            [typed.cljc.analyzer.env :as env]))

(defn dummy-post-walk
  {:pass-info {:walk :post :depends #{#'uniquify2/uniquify-locals}}}
  [ast]
  ast)

(def ^:private scheduled-passes
  (delay
    (passes/schedule
      #{#'uniquify2/uniquify-locals
        #'dummy-post-walk})))

(defn ^:private analyzer-thread-bindings [env]
  (assoc (ana-clj/default-thread-bindings env)
         #'*ns* *ns*
         #'ana/scheduled-passes @scheduled-passes))

(defn- compiler-env->env [&env]
  (assoc (ana-clj/empty-env)
         :locals (into {} (map (fn [sym]
                                 [sym {:op :local
                                       :form sym
                                       :name sym
                                       ::ana/op ::ana/local
                                       :children []}]))
                       (keys &env))))

(defn local-occurs? [form &env locals]
  {:pre [(vector? locals)]}
  (let [env (compiler-env->env &env)
        dummy-body (gensym)
        fn-form `(fn* ~(vec locals) ~dummy-body)]
    (with-bindings (analyzer-thread-bindings env)
      (env/ensure (ana-clj/global-env)
        (let [;; analyze (fn* [l1 l2 ..] placeholder) to initialize unique locals
              ;; return an ast of (fn* [l1# l2# ..] form)
              init-ast (fn rec [ast]
                         (ast/prewalk
                           ast
                           (comp
                             (fn [ast]
                               (case (::ana/op ast)
                                 ::ana/unanalyzed (if (= dummy-body (:form ast))
                                                    (assoc ast :form form)
                                                    (rec (ana/analyze-outer ast)))
                                 ast))
                             uniquify2/uniquify-locals)))
              {[{:keys [params] :as fn-method}] :methods :as init} (init-ast (ana/unanalyzed fn-form env))
              _ (assert (= ::ana/fn (::ana/op init))
                        (pr-str (::ana/op init)))
              _ (assert (= ::ana/fn-method (::ana/op fn-method))
                        (pr-str (::ana/op fn-method)))
              uniquified-locals (into #{} (map :name) params)
              _ (assert (= (count uniquified-locals)
                           (count locals)))
              _ (assert (empty? (set/intersection uniquified-locals (set locals)))
                        {:uniquified-locals uniquified-locals
                         :locals locals})
              found? (volatile! false)
              rec (fn rec [ast]
                    (ast/prewalk
                      ast
                      (comp
                        (fn [ast]
                          (case (::ana/op ast)
                            ::ana/unanalyzed (rec (ana/analyze-outer ast))
                            ::ana/local (if (uniquified-locals (:name ast))
                                          (reduced (vreset! found? true))
                                          ast)
                            ast))
                        uniquify2/uniquify-locals))
                    @found?)
              start (-> fn-method :body :ret)]
          (rec start))))))

(comment
  (assert
    (true? (local-occurs?
      'a {'a true} '[a])))

  (assert
    (false? (local-occurs?
      'b {'a true} '[a])))

  (assert
    (false? (local-occurs?
      '(list 'a 'b) {'a true} '[a])))
  (assert
    (true?
      (local-occurs?
        '(list a 'b) {'a true} '[a])))
  )
