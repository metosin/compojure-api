(ns compojure.api.core
  (:require [compojure.api.meta :as meta]
            [compojure.api.async]
            [compojure.api.routes :as routes]
            [compojure.api.middleware :as mw]
            [compojure.core :as compojure]
            [clojure.tools.macro :as macro]))

(defn ring-handler
  "Creates vanilla ring-handler from any invokable thing (e.g. compojure-api route)"
  [handler]
  (fn
    ([request] (handler request))
    ([request respond raise] (handler request respond raise))))

(defn routes
  "Create a Ring handler by combining several handlers into one."
  [& handlers]
  (let [handlers (seq (keep identity (flatten handlers)))]
    (routes/map->Route
      {:childs (vec handlers)
       :handler (meta/routing handlers)})))

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
    (routes/map->Route {:handler (meta/routing handlers)})))

(defmacro middleware
  "Wraps routes with given middlewares using thread-first macro.

  Note that middlewares will be executed even if routes in body
  do not match the request uri. Be careful with middleware that
  has side-effects."
  {:style/indent 1
   :deprecated "1.1.14"
   :superseded-by "route-middleware"}
  [middleware & body]
  (assert (= "true" (System/getProperty "compojure.api.core.allow-dangerous-middleware"))
          (str "compojure.api.core.middleware is deprecated because of security issues. "
               "Please use route-middleware instead. "
               "Set compojure.api.core.allow-dangerous-middleware=true to keep using middleware."))
  `(let [body# (routes ~@body)
         wrap-mw# (mw/compose-middleware ~middleware)]
     (routes/create nil nil {} [body#] (wrap-mw# body#))))

(defn route-middleware
  "Wraps routes with given middlewares using thread-first macro."
  {:style/indent 1
   :supercedes "middleware"}
  [middleware & body]
  (let [handler (apply routes body)
        x-handler (compojure/wrap-routes handler (mw/compose-middleware middleware))]
    ;; use original handler for docs and wrapped handler for implementation
    (routes/map->Route
      {:childs [handler]
       :handler x-handler})))

(defmacro context {:style/indent 2} [& args] (meta/restructure nil args {:context? true :&form &form :&env &env}))

(defmacro GET {:style/indent 2} [& args] (meta/restructure :get args nil))
(defmacro ANY {:style/indent 2} [& args] (meta/restructure nil args nil))
(defmacro HEAD {:style/indent 2} [& args] (meta/restructure :head args nil))
(defmacro PATCH {:style/indent 2} [& args] (meta/restructure :patch args nil))
(defmacro DELETE {:style/indent 2} [& args] (meta/restructure :delete args nil))
(defmacro OPTIONS {:style/indent 2} [& args] (meta/restructure :options args nil))
(defmacro POST {:style/indent 2} [& args] (meta/restructure :post args nil))
(defmacro PUT {:style/indent 2} [& args] (meta/restructure :put args nil))
