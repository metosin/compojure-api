(ns compojure.api.core
  (:require [compojure.api.meta :as meta]
            [compojure.api.routes :as routes]
            [compojure.api.middleware :as mw]
            [compojure.core :as compojure]
            [clojure.tools.macro :as macro]))

(defn- handle [handlers request]
  (some #(% request) handlers))

(defn routes
  "Create a Ring handler by combining several handlers into one."
  [& handlers]
  (let [handlers (seq (keep identity handlers))]
    (routes/create nil nil {} (vec handlers) (partial handle handlers))))

(defmacro defroutes
  "Define a Ring handler function from a sequence of routes.
  The name may optionally be followed by a doc-string and metadata map."
  {:style/indent 1}
  [name & routes]
  (let [[name routes] (macro/name-with-attributes name routes)]
    `(def ~name (routes ~@routes))))

(defmacro let-routes
  "Takes a vector of bindings and a body of routes.

  Equivalent to: `(let [...] (routes ...))`"
  {:style/indent 1}
  [bindings & body]
  `(let ~bindings (routes ~@body)))

(defn undocumented
  "Routes without route-documentation. Can be used to wrap routes,
  not satisfying compojure.api.routes/Routing -protocol."
  [& handlers]
  (let [handlers (keep identity handlers)]
    (routes/create nil nil {} nil (partial handle handlers))))

(defn- pre-init [middleware]
  (let [proxy (middleware (fn [req] ((:route-handler req) req)))]
    (fn [handler]
      (fn [request]
        (proxy (assoc request :route-handler handler))))))

(defn wrap-routes
  "Apply a middleware function to routes after they have been matched."
  ([handler middleware]
   (let [middleware (pre-init middleware)]
     (fn [request]
       (let [mw (:route-middleware request identity)]
         (handler (assoc request :route-middleware (comp mw middleware)))))))
  ([handler middleware & args]
   (wrap-routes handler #(apply middleware % args))))

(defn middleware
  "Wraps routes with given middlewares using thread-first macro."
  {:style/indent 1}
  [middleware & body]
  (let [handler (apply routes body)
        x-handler (wrap-routes handler (mw/compose-middleware middleware))]
    ;; use original handler for docs and wrapped handler for implementation
    (routes/create nil nil {} [handler] x-handler)))

(defmacro context {:style/indent 2} [& args] (meta/restructure nil      args {:context? true}))

(defmacro GET     {:style/indent 2} [& args] (meta/restructure :get     args nil))
(defmacro ANY     {:style/indent 2} [& args] (meta/restructure nil      args nil))
(defmacro HEAD    {:style/indent 2} [& args] (meta/restructure :head    args nil))
(defmacro PATCH   {:style/indent 2} [& args] (meta/restructure :patch   args nil))
(defmacro DELETE  {:style/indent 2} [& args] (meta/restructure :delete  args nil))
(defmacro OPTIONS {:style/indent 2} [& args] (meta/restructure :options args nil))
(defmacro POST    {:style/indent 2} [& args] (meta/restructure :post    args nil))
(defmacro PUT     {:style/indent 2} [& args] (meta/restructure :put     args nil))
