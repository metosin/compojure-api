(ns compojure.api.compojure-compat
  "Compatibility for older Compojure versions."
  (:require [clout.core :as clout]
            [compojure.core :as c]))

;; Copy-pasted from Compojure 1.6 to maintain backwards-compatibility with
;; Compojure 1.5. Essentially the same code existed in Compojure 1.5 but with a
;; different name.
;; <https://github.com/weavejester/compojure/blob/3bdbc2f0c151ebb54b2f77780b417475e8277549/src/compojure/core.clj>

(defn- context-request [request route]
  (if-let [params (clout/route-matches route request)]
    (let [uri     (:uri request)
          path    (:path-info request uri)
          context (or (:context request) "")
          subpath (:__path-info params)
          params  (dissoc params :__path-info)]
      (-> request
          (#'c/assoc-route-params (#'c/decode-route-params params))
          (assoc :path-info (if (= subpath "") "/" subpath)
                 :context   (#'c/remove-suffix uri subpath))))))

(defn ^:no-doc make-context [route make-handler]
  (letfn [(handler
            ([request]
             ((make-handler request) request))
            ([request respond raise]
             ((make-handler request) request respond raise)))]
    (if (#{":__path-info" "/:__path-info"} (:source route))
      handler
      (fn
        ([request]
         (if-let [request (context-request request route)]
           (handler request)))
        ([request respond raise]
         (if-let [request (context-request request route)]
           (handler request respond raise)
           (respond nil)))))))
