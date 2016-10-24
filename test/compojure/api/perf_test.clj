(ns compojure.api.perf-test
  (:require [compojure.api.sweet :refer :all]
            [compojure.api.test-utils :as h]
            [criterium.core :as cc]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [cheshire.core :as cheshire])
  (:import (java.io ByteArrayInputStream)))

;;
;; start repl with `lein perf repl`
;; perf measured with the following setup:
;;
;; Model Name:            MacBook Pro
;; Model Identifier:      MacBookPro11,3
;; Processor Name:        Intel Core i7
;; Processor Speed:       2,5 GHz
;; Number of Processors:  1
;; Total Number of Cores: 4
;; L2 Cache (per Core):   256 KB
;; L3 Cache:              6 MB
;; Memory:                16 GB
;;

(defn title [s]
  (println
    (str "\n\u001B[35m"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\n## " s " ##\n"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\u001B[0m\n")))

(defn post* [app uri json]
  (->
    (app {:uri uri
          :request-method :post
          :headers {"content-type" "application/json"}
          :body (io/input-stream (.getBytes json))})
    :body
    slurp))

(defn parse [s] (json/parse-string s true))

(s/defschema Order {:id s/Str
                    :name s/Str
                    (s/optional-key :description) s/Str
                    :address (s/maybe {:street s/Str
                                       :country (s/enum "FI" "PO")})
                    :orders [{:name #"^k"
                              :price s/Any
                              :shipping s/Bool}]})

(defn bench []

  ; 27µs
  ; 27µs (-0%)
  ; 25µs (1.0.0)
  ; 25µs (muuntaja)
  (let [app (api
              (GET "/30" []
                (ok {:result 30})))
        call #(h/get* app "/30")]

    (title "GET JSON")
    (assert (= {:result 30} (second (call))))
    (cc/bench (call)))

  ;; 73µs
  ;; 53µs (-27%)
  ;; 50µs (1.0.0)
  ;; 38µs (muuntaja), -24%
  (let [app (api
              (POST "/plus" []
                :return {:result s/Int}
                :body-params [x :- s/Int, y :- s/Int]
                (ok {:result (+ x y)})))
        data (h/json {:x 10, :y 20})
        call #(post* app "/plus" data)]

    (title "JSON POST with 2-way coercion")
    (assert (= {:result 30} (parse (call))))
    (cc/bench (call)))

  ;; 85µs
  ;; 67µs (-21%)
  ;; 66µs (1.0.0)
  ;; 56µs (muuntaja), -15%
  (let [app (api
              (context "/a" []
                (context "/b" []
                  (context "/c" []
                    (POST "/plus" []
                      :return {:result s/Int}
                      :body-params [x :- s/Int, y :- s/Int]
                      (ok {:result (+ x y)}))))))
        data (h/json {:x 10, :y 20})
        call #(post* app "/a/b/c/plus" data)]

    (title "JSON POST with 2-way coercion + contexts")
    (assert (= {:result 30} (parse (call))))
    (cc/bench (call)))

  ;; 266µs
  ;; 156µs (-41%)
  ;; 146µs (1.0.0)
  ;;  74µs (muuntaja), -49%
  (let [app (api
              (POST "/echo" []
                :return Order
                :body [order Order]
                (ok order)))
        data (h/json {:id "123"
                      :name "Tommi's order"
                      :description "Totally great order"
                      :address {:street "Randomstreet 123"
                                :country "FI"}
                      :orders [{:name "k1"
                                :price 123.0
                                :shipping true}
                               {:name "k2"
                                :price 42.0
                                :shipping false}]})
        call #(post* app "/echo" data)]

    (title "JSON POST with nested data")
    (s/validate Order (parse (call)))
    (cc/bench (call))))

(defn resource-bench []

  (let [resource-map {:post {:responses {200 {:schema {:result s/Int}}}
                             :parameters {:body-params {:x s/Int, :y s/Int}}
                             :handler (fn [{{:keys [x y]} :body-params}]
                                        (ok {:result (+ x y)}))}}]

    ;; 62µs
    ;; 44µs (muuntaja)
    (let [my-resource (resource resource-map)
          app (api
                (context "/plus" []
                  my-resource))
          data (h/json {:x 10, :y 20})
          call #(post* app "/plus" data)]

      (title "JSON POST to pre-defined resource with 2-way coercion")
      (assert (= {:result 30} (parse (call))))
      (cc/bench (call)))

    ;; 68µs
    ;; 52µs (muuntaja)
    (let [app (api
                (context "/plus" []
                  (resource resource-map)))
          data (h/json {:x 10, :y 20})
          call #(post* app "/plus" data)]

      (title "JSON POST to inlined resource with 2-way coercion")
      (assert (= {:result 30} (parse (call))))
      (cc/bench (call)))

    ;; 26µs
    (let [my-resource (resource resource-map)
          app my-resource
          data {:x 10, :y 20}
          call #(app {:request-method :post :uri "/irrelevant" :body-params data})]

      (title "direct POST to pre-defined resource with 2-way coercion")
      (assert (= {:result 30} (:body (call))))
      (cc/bench (call)))

    ;; 30µs
    (let [my-resource (resource resource-map)
          app (context "/plus" []
                my-resource)
          data {:x 10, :y 20}
          call #(app {:request-method :post :uri "/plus" :body-params data})]

      (title "POST to pre-defined resource with 2-way coercion")
      (assert (= {:result 30} (:body (call))))
      (cc/bench (call)))

    ;; 40µs
    (let [app (context "/plus" []
                (resource resource-map))
          data {:x 10, :y 20}
          call #(app {:request-method :post :uri "/plus" :body-params data})]

      (title "POST to inlined resource with 2-way coercion")
      (assert (= {:result 30} (:body (call))))
      (cc/bench (call)))))

(defn e2e-json-comparison-different-payloads []
  (let [json-request (fn [data]
                       {:uri "/echo"
                        :request-method :post
                        :headers {"content-type" "application/json"
                                  "accept" "application/json"}
                        :body (cheshire/generate-string data)})
        request-stream (fn [request]
                         (let [b (.getBytes ^String (:body request))]
                           (fn []
                             (assoc request :body (ByteArrayInputStream. b)))))
        app (api
              (POST "/echo" []
                :body [body s/Any]
                (ok body)))]
    (doseq [file ["dev-resources/json/json10b.json"
                  "dev-resources/json/json100b.json"
                  "dev-resources/json/json1k.json"
                  "dev-resources/json/json10k.json"
                  "dev-resources/json/json100k.json"]
            :let [data (cheshire/parse-string (slurp file))
                  request (json-request data)
                  request! (request-stream request)]]

      "10b"
      ;; 42µs
      ;; 24µs (muuntaja), -43%

      "100b"
      ;; 79µs
      ;; 39µs (muuntaja), -50%

      "1k"
      ;; 367µs
      ;;  92µs (muuntaja), -75%

      "10k"
      ;; 2870µs
      ;; 837µs (muuntaja), -70%

      "100k"
      ;; 10800µs
      ;;  8050µs (muuuntaja), -25%

      (title file)
      (cc/bench (-> (request!) app :body slurp)))))

(comment
  (bench)
  (resource-bench)
  (e2e-json-comparison-different-payloads))

(comment
  (bench)
  (resource-bench))
