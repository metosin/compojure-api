(ns compojure.api.meta.analyzer
  (:require [typed.cljc.analyzer :as ana]
            [typed.clj.analyzer :as ana-clj]
            [typed.cljc.analyzer.passes :as passes]
            [typed.cljc.analyzer.passes.uniquify :as uniquify2]))

(defn dummy-post-walk
  {:pass-info {:walk :post :depends #{}}}
  [ast]
  ast)

(def ^:private scheduled-passes
  (delay
    (passes/schedule
      #{#'uniquify2/uniquify-locals
        #'dummy-post-walk})))

(def ^:private extra-bindings
  (delay
    {#'ana/scheduled-passes @scheduled-passes}))

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
  (ana-clj/analyze form
                   (compiler-env->env &env)
                   {:bindings @extra-bindings}))

(comment
  (time (local-occurs?
          'a {'a true} #{'a}))
  )
