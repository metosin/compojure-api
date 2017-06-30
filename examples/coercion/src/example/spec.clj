(ns example.spec
  (:require [compojure.api.sweet :refer [context POST resource]]
            [ring.util.http-response :refer [ok]]
            [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]))

(s/def ::x spec/int?)
(s/def ::y spec/int?)
(s/def ::total spec/int?)
(s/def ::total-map (s/keys :req-un [::total]))

(def routes
  (context "/spec" []
    :tags ["spec"]
    :coercion :spec

    (POST "/plus" []
      :summary "plus with clojure.spec using data-specs"
      :body-params [x :- ::x, {y :- ::y 0}]
      :return ::total-map
      (ok {:total (+ x y)}))

    (context "/plus" []
      (resource
        {:get
         {:summary "data-driven plus with clojure.spec using data-specs"
          :parameters {:query-params (s/keys :req-un [::x ::y])}
          :responses {200 {:schema ::total-map}}
          :handler (fn [{{:keys [x y]} :query-params}]
                     (ok {:total (+ x y)}))}}))))
