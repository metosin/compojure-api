(ns compojure.api.middleware.rmf-muuntaja-adapter
  (:require [compojure.api.middleware :refer [rmf-format->muuntaja-formats]]
            [muuntaja.core :as m]
            ;[muuntaja.format.msgpack]
            [muuntaja.format.yaml]))

(swap! rmf-format->muuntaja-formats
       #(into (let [defaults (:formats m/default-options)]
                (reduce-kv (fn [m k s]
                             (assoc m k {s (get defaults s)}))
                           {:yaml-kw {"application/x-yaml" muuntaja.format.yaml/format}}
                           {:json "application/json"
                            :json-kw "application/json"
                            :edn "application/edn"
                            ;:clojure "application/clojure"
                            ;:yaml "application/x-yaml"
                            ;:yaml-kw "application/x-yaml"
                            ;:yaml-in-html "text/html"
                            :transit-json "application/transit+json"
                            :transit-msgpack "application/transit+msgpack"}))
              %))
