(ns compojure.api.core
  (:require [compojure.api.meta :as meta]
            [compojure.api.routes :as routes]
            [clojure.tools.macro :as macro]))

(defn- ring-handler [handlers]
  (condp = (count handlers)
    0 (constantly nil)
    1 (first handlers)
    (fn [request] (some #(% request) handlers))))

(defn routes
  "Create a Ring handler by combining several handlers into one."
  [& handlers]
  (if-let [handlers (seq (keep identity handlers))]
    (routes/create nil nil {} (vec handlers) (ring-handler handlers))))

(defmacro defroutes
  "Define a Ring handler function from a sequence of routes.
  The name may optionally be followed by a doc-string and metadata map."
  [name & routes]
  (let [[name routes] (macro/name-with-attributes name routes)]
    `(def ~name (routes ~@routes))))

(defmacro let-routes
  "Takes a vector of bindings and a body of routes.

  Equivalent to: `(let [...] (routes ...))`"
  [bindings & body]
  `(let ~bindings (routes ~@body)))

(defn undocumented [& handlers]
  (let [handlers (keep identity handlers)]
    (routes/create nil nil {} nil (ring-handler handlers))))

(defn middleware-fn [middleware]
  (if (vector? middleware)
    (let [[f & arguments] middleware]
      #(apply f % arguments))
    middleware))

(defn compose-middleware [middleware]
  (->> middleware
       (map middleware-fn)
       (apply comp identity)))

(defmacro middleware
  "Wraps routes with given middlewares using thread-first macro."
  [middleware & body]
  (let [routes? (> (count body) 1)]
    `(let [body# ~(if routes? `(routes ~@body) (first body))
           wrap-mw# (compose-middleware ~middleware)]
       (routes/create "" nil {} [body#] (wrap-mw# body#)))))

(defmacro context [& args] (meta/restructure nil      args {:routes 'routes}))

(defmacro GET     [& args] (meta/restructure :get     args nil))
(defmacro ANY     [& args] (meta/restructure nil      args nil))
(defmacro HEAD    [& args] (meta/restructure :head    args nil))
(defmacro PATCH   [& args] (meta/restructure :patch   args nil))
(defmacro DELETE  [& args] (meta/restructure :delete  args nil))
(defmacro OPTIONS [& args] (meta/restructure :options args nil))
(defmacro POST    [& args] (meta/restructure :post    args nil))
(defmacro PUT     [& args] (meta/restructure :put     args nil))
