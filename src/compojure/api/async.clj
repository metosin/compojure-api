(ns compojure.api.async
  (:require [compojure.response :as response]
            compojure.api.routes
            muuntaja.util))

;; NB: muuntaja.util/when-ns eats all exceptions inside the body, including
;; those about unresolvable symbols. Keep this in mind when debugging the
;; definitions below.

(muuntaja.util/when-ns
  'manifold.deferred
  ;; Compojure is smart enough to get the success value out of deferred by
  ;; itself, but we want to catch the exceptions as well.
  (extend-protocol compojure.response/Sendable
    manifold.deferred.IDeferred
    (send* [deferred request respond raise]
      (manifold.deferred/on-realized deferred #(response/send % request respond raise) raise))))

(muuntaja.util/when-ns
  'clojure.core.async
  (extend-protocol compojure.response/Sendable
    clojure.core.async.impl.channels.ManyToManyChannel
    (send* [channel request respond raise]
      (clojure.core.async/go
        (let [message (clojure.core.async/<! channel)]
          (if (instance? Throwable message)
            (raise message)
            (response/send message request respond raise)))))))

(extend-protocol compojure.response/Sendable
  compojure.api.routes.Route
  (send* [this request respond raise]
    ((.handler this) request #(response/send % request respond raise) raise)))
