(ns compojure.api.meta
  (:require [compojure.api.common :as common :refer [extract-parameters]]))

(defmulti restructure-param
  "Restructures a key value pair in smart routes. By default the key
  is consumed form the :parameters map in acc. k = given key, v = value."
  (fn [k v acc] k))

(defn restructure [method [path arg & args] {:keys [context?]}]
  (let [[options body] (extract-parameters args true)
        [path-string lets arg-with-request arg] (destructure-compojure-api-request path arg)

        {:keys [lets
                letks
                responses
                middleware
                middlewares
                swagger
                parameters
                body]} (reduce
                         (fn [acc [k v]]
                           (restructure-param k v (update-in acc [:parameters] dissoc k)))
                         {:lets lets
                          :letks []
                          :responses nil
                          :middleware []
                          :swagger {}
                          :body body}
                         options)

        ;; migration helpers
        _ (assert (not middlewares) ":middlewares is deprecated with 1.0.0, use :middleware instead.")
        _ (assert (not parameters) ":parameters is deprecated with 1.0.0, use :swagger instead.")

        ;; response coercion middleware, why not just code?
        middleware (if (seq responses) (conj middleware `[coerce/body-coercer-middleware (common/merge-vector ~responses)]) middleware)]

    (if context?

      ;; context
      (let [form `(compojure.core/routes ~@body)
            form (if (seq letks) `(p/letk ~letks ~form) form)
            form (if (seq lets) `(let ~lets ~form) form)
            form (if (seq middleware) `((mw/compose-middleware ~middleware) ~form) form)
            form `(compojure.core/context ~path ~arg-with-request ~form)

            ;; create and apply a separate lookup-function to find the inner routes
            childs (let [form (vec body)
                         form (if (seq letks) `(dummy-letk ~letks ~form) form)
                         form (if (seq lets) `(dummy-let ~lets ~form) form)
                         form `(compojure.core/let-request [~arg-with-request ~'+compojure-api-request+] ~form)
                         form `(fn [~'+compojure-api-request+] ~form)
                         form `(~form {})]
                     form)]

        `(routes/create ~path-string ~method (merge-parameters ~swagger) ~childs ~form))

      ;; endpoints
      (let [form `(do ~@body)
            form (if (seq letks) `(p/letk ~letks ~form) form)
            form (if (seq lets) `(let ~lets ~form) form)
            form (compojure.core/compile-route method path arg-with-request (list form))
            form (if (seq middleware) `(compojure.core/wrap-routes ~form (mw/compose-middleware ~middleware)) form)]

        `(routes/create ~path-string ~method (merge-parameters ~swagger) nil ~form)))))
