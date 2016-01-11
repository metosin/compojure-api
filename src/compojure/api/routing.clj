(ns compojure.api.routing
  (:require [ring.swagger.common :as rsc]
            [compojure.api.impl.logging :as logging])
  (:import [clojure.lang AFn IFn]))

;;
;; Routing
;;

(def ^:dynamic *fail-on-missing-route-info* false)

(defprotocol Routing
  (get-routes [handler]))

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
      (vec
        (for [[p m i] (mapcat get-routes (filter (partial satisfies? Routing) childs))]
          [(->paths path p) m (rsc/deep-merge info i)]))
      [[path method info]]))

  IFn
  (invoke [_ request]
    (handler request))
  (applyTo [this args]
    (AFn/applyToHelper this args)))

(defn create [path method info childs handler]
  (when-let [invalid-childs (seq (remove (partial satisfies? Routing) childs))]
    (let [message "Not all child routes satisfy compojure.api.routing/Routing."
          data {:path path
                :method method
                :info info
                :childs childs
                :invalid invalid-childs}]
      (if *fail-on-missing-route-info*
        (throw (ex-info message data))
        (logging/log! :warn message data))))
  (->Route path method info childs handler))
