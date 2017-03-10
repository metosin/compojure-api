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

;; FIXME: Compojure 1.6 should contain these two function
;; Current version applies middlewares in wrong order

(defn- pre-init [middleware]
  (let [proxy (middleware
               (fn
                 ([request]
                  ((:route-handler request) request))
                 ([request respond raise]
                  ((:route-handler request) request respond raise))))]
    (fn [handler]
      (let [prep-request #(assoc % :route-handler handler)]
        (fn
          ([request]
           (proxy (prep-request request)))
          ([request respond raise]
           (proxy (prep-request request) respond raise)))))))

(defn wrap-routes
  {:no-doc true}
  ([handler middleware]
   (let [middleware   (pre-init middleware)
         prep-request (fn [request]
                        (let [mw (:route-middleware request identity)]
                          (assoc request :route-middleware (comp mw middleware))))]
       (fn
         ([request]
          (handler (prep-request request)))
         ([request respond raise]
          (handler (prep-request request) respond raise)))))
  ([handler middleware & args]
   (wrap-routes handler #(apply middleware % args))))

(defn route-middleware
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
