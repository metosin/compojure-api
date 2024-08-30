;; Copyright © 2024 James Reeves
;; 
;; Distributed under the Eclipse Public License, the same as Clojure.
(ns compojure-api-kondo-hooks.compojure.core)

(defn- and-binding [req binds]
  `(dissoc (:params ~req) ~@(map keyword (keys binds)) ~@(map str (keys binds))))

(defn- symbol-binding [req sym]
  `(get-in ~req [:params ~(keyword sym)] (get-in ~req [:params ~(str sym)])))

(defn- application-binding [req sym func]
  `(~func ~(symbol-binding req sym)))

(defn- vector-bindings [args req]
  (loop [args args, binds {}]
    (if (seq args)
      (let [[x y z] args]
        (cond
          (= '& x)
          (recur (nnext args) (assoc binds y (and-binding req binds)))
          (= :as x)
          (recur (nnext args) (assoc binds y req))
          (and (symbol? x) (= :<< y) (nnext args))
          (recur (drop 3 args) (assoc binds x (application-binding req x z)))
          (symbol? x)
          (recur (next args) (assoc binds x (symbol-binding req x)))
          :else
          (throw (Exception. (str "Unexpected binding: " x)))))
      (mapcat identity binds))))

(defn- warn-on-*-bindings! [bindings]
  (when (and (vector? bindings) (contains? (set bindings) '*))
    (binding [*out* *err*]
      (println "WARNING: * should not be used as a route binding."))))

(defn- application-symbols [args]
  (loop [args args, syms '()]
    (if (seq args)
      (let [[x y] args]
        (if (and (symbol? x) (= :<< y))
          (recur (drop 3 args) (conj syms x))
          (recur (next args) syms)))
      (seq syms))))

(defmacro ^:no-doc let-request [[bindings request] & body]
  (if (vector? bindings)
    `(let [~@(vector-bindings bindings request)]
       ~(if-let [syms (application-symbols bindings)]
          `(if (and ~@(for [s syms] `(not (nil? ~s)))) (do ~@body))
          `(do ~@body)))
    `(let [~bindings ~request] ~@body)))

(defn compile-route
  "Compile a route in the form `(method path bindings & body)` into a function.
  Used to create custom route macros."
  [method path bindings body]
  (let [greq (gensym "greq")]
    `(fn [~greq]
       ~(macroexpand-1 `(let-request [~bindings ~greq] ~@body)))))
