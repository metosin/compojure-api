(ns compojure.api.routing
  (:require [ring.swagger.common :as rsc])
  (:import [clojure.lang AFn IFn]))

;;
;; Routing
;;

(defprotocol Routing
  (get-routes [this]))

(extend-protocol Routing
  Object
  (routing [_] nil))

;;
;; Routes
;;

(defn- ->path [path]
  (if-not (#{"/" ""} path) path))

(defn- ->paths [p1 p2]
  (or (->path (str (->path p1) (->path p2))) "/"))

(defrecord Route [path method info childs handler]
  Routing
  (get-routes [_]
    (if (seq childs)
      (seq
        (for [[p m i] (mapcat get-routes childs)]
          [(->paths path p) m (rsc/deep-merge info i)]))
      [[path method info]]))

  IFn
  (invoke [_ request]
    (handler request))
  (applyTo [this args]
    (AFn/applyToHelper this args)))
