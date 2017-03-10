(ns compojure.api.async
  (:require [compojure.response :as response]
            muuntaja.util))

(muuntaja.util/when-ns 'manifold.deferred
  ;; Compojure is smart enough to get the success value out of deferred by itself,
  ;; but we want to catch the exceptions as well.
  (extend-protocol compojure.response/Sendable
    manifold.deferred.Deferrable
    (send* [deferred request respond raise]
      (manifold.deferred/on-realized (manifold.deferred/->deferred deferred)
                                    #(response/send % request respond raise) raise))))
