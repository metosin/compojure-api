(ns compojure.api.perf-test
  (:require [compojure.api.sweet :refer :all]
            [compojure.api.test-utils :as h]
            [criterium.core :as cc]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [muuntaja.core :as m]
            [clojure.java.io :as io])
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

(s/defschema Order {:id s/Str
                    :name s/Str
                    (s/optional-key :description) s/Str
                    :address (s/maybe {:street s/Str
                                       :country (s/enum "FI" "PO")})
                    :orders [{:name #"^k"
                              :price s/Any
                              :shipping s/Bool}]})

;; slurps also the body, which is not needed in real life!
(defn bench []

  ; 27µs
  ; 27µs (-0%)
  ; 25µs (1.0.0)
  ; 25µs (muuntaja)
  ; 32µs (jsonista)
  (let [app (api
              (GET "/30" []
                (ok {:result 30})))
        call #(h/get* app "/30")]

    (title "GET JSON")
    (println (call))
    (assert (= {:result 30} (second (call))))
    (cc/quick-bench (call)))

  ;; 73µs
  ;; 53µs (-27%)
  ;; 50µs (1.0.0)
  ;; 38µs (muuntaja), -24%
  ;; 34µs (muuntaja), -11%
  (let [app (api
              (POST "/plus" []
                :return {:result s/Int}
                :body-params [x :- s/Int, y :- s/Int]
                (ok {:result (+ x y)})))
        data (h/json-string {:x 10, :y 20})
        call #(post* app "/plus" data)]

    (title "JSON POST with 2-way coercion")
    (assert (= {:result 30} (h/parse (call))))
    (cc/quick-bench (call)))

  ;; 85µs
  ;; 67µs (-21%)
  ;; 66µs (1.0.0)
  ;; 56µs (muuntaja), -15%
  ;; 49µs (jsonista), -13%
  (let [app (api
              (context "/a" []
                (context "/b" []
                  (context "/c" []
                    (POST "/plus" []
                      :return {:result s/Int}
                      :body-params [x :- s/Int, y :- s/Int]
                      (ok {:result (+ x y)}))))))
        data (h/json-string {:x 10, :y 20})
        call #(post* app "/a/b/c/plus" data)]

    (title "JSON POST with 2-way coercion + contexts")
    (assert (= {:result 30} (h/parse (call))))
    (cc/quick-bench (call)))

  ;; 266µs
  ;; 156µs (-41%)
  ;; 146µs (1.0.0)
  ;;  74µs (muuntaja), -49%
  ;;  51µs (jsonista), -30%
  (let [app (api
              (POST "/echo" []
                :return Order
                :body [order Order]
                (ok order)))
        data (h/json-string {:id "123"
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
    (s/validate Order (h/parse (call)))
    (cc/quick-bench (call))))

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
          data (h/json-string {:x 10, :y 20})
          call #(post* app "/plus" data)]

      (title "JSON POST to pre-defined resource with 2-way coercion")
      (assert (= {:result 30} (h/parse (call))))
      (cc/quick-bench (call)))

    ;; 68µs
    ;; 52µs (muuntaja)
    (let [app (api
                (context "/plus" []
                  (resource resource-map)))
          data (h/json-string {:x 10, :y 20})
          call #(post* app "/plus" data)]

      (title "JSON POST to inlined resource with 2-way coercion")
      (assert (= {:result 30} (h/parse (call))))
      (cc/quick-bench (call)))

    ;; 26µs
    (let [my-resource (resource resource-map)
          app my-resource
          data {:x 10, :y 20}
          call #(app {:request-method :post :uri "/irrelevant" :body-params data})]

      (title "direct POST to pre-defined resource with 2-way coercion")
      (assert (= {:result 30} (:body (call))))
      (cc/quick-bench (call)))

    ;; 30µs
    (let [my-resource (resource resource-map)
          app (context "/plus" []
                my-resource)
          data {:x 10, :y 20}
          call #(app {:request-method :post :uri "/plus" :body-params data})]

      (title "POST to pre-defined resource with 2-way coercion")
      (assert (= {:result 30} (:body (call))))
      (cc/quick-bench (call)))

    ;; 40µs
    (let [app (context "/plus" []
                (resource resource-map))
          data {:x 10, :y 20}
          call #(app {:request-method :post :uri "/plus" :body-params data})]

      (title "POST to inlined resource with 2-way coercion")
      (assert (= {:result 30} (:body (call))))
      (cc/quick-bench (call)))))

(defn e2e-json-comparison-different-payloads []
  (let [json-request (fn [data]
                       {:uri "/echo"
                        :request-method :post
                        :headers {"content-type" "application/json"
                                  "accept" "application/json"}
                        :body (h/json-string data)})
        request-stream (fn [request]
                         (let [b (.getBytes ^String (:body request))]
                           (fn []
                             (assoc request :body (ByteArrayInputStream. b)))))
        app (api
              {:formats (assoc m/default-options :return :bytes)}
              (POST "/echo" []
                :body [body s/Any]
                (ok body)))]
    (doseq [file ["dev-resources/json/json10b.json"
                  "dev-resources/json/json100b.json"
                  "dev-resources/json/json1k.json"
                  "dev-resources/json/json10k.json"
                  "dev-resources/json/json100k.json"]
            :let [data (h/parse (slurp file))
                  request (json-request data)
                  request! (request-stream request)]]

      "10b"
      ;; 42µs
      ;; 24µs (muuntaja), -43%
      ;; 18µs (muuntaja+jsonista), -43%

      "100b"
      ;; 79µs
      ;; 39µs (muuntaja), -50%
      ;; 20µs (muuntaja+jsonista), -44%

      "1k"
      ;; 367µs
      ;;  92µs (muuntaja), -75%
      ;;  29µs (muuntaja+jsonista), -65%

      "10k"
      ;; 2870µs
      ;; 837µs (muuntaja), -70%
      ;; 147µs (muuntaja+jsonista) -81%

      "100k"
      ;; 10800µs
      ;;  8050µs (muuuntaja), -25%
      ;;  1260µs (muuntaja+jsonista 0.5.0) -84%

      (title file)
      (cc/quick-bench (-> (request!) app :body slurp)))))

(defn e2e-json-comparison-different-payloads-no-slurp []
  (let [json-request (fn [data]
                       {:uri "/echo"
                        :request-method :post
                        :headers {"content-type" "application/json"
                                  "accept" "application/json"}
                        :body (h/json-string data)})
        request-stream (fn [request]
                         (let [b (.getBytes ^String (:body request))]
                           (fn []
                             (assoc request :body (ByteArrayInputStream. b)))))
        app (api
              {:formats (assoc m/default-options :return :bytes)}
              (POST "/echo" []
                :body [body s/Any]
                (ok body)))]
    (doseq [file ["dev-resources/json/json10b.json"
                  "dev-resources/json/json100b.json"
                  "dev-resources/json/json1k.json"
                  "dev-resources/json/json10k.json"
                  "dev-resources/json/json100k.json"]
            :let [data (h/parse (slurp file))
                  request (json-request data)
                  request! (request-stream request)]]

      "10b"
      ;; 38µs (1.x)
      ;; 14µs (2.0.0-alpha21)

      "100b"
      ;; 74µs (1.x)
      ;; 16µs (2.0.0-alpha21)

      "1k"
      ;; 322µs (1.x)
      ;;  24µs (2.0.0-alpha21)

      "10k"
      ;; 3300µs (1.x)
      ;;  120µs (2.0.0-alpha21)

      "100k"
      ;; 10600µs (1.x)
      ;;  1000µs (2.0.0-alpha21)

      (title file)
      ;;(println (-> (request!) app :body slurp))
      (cc/quick-bench (app (request!))))))

(comment
  (bench)
  (resource-bench)
  (e2e-json-comparison-different-payloads)
  (e2e-json-comparison-different-payloads-no-slurp))

(comment
  (bench)
  (resource-bench))

(comment
  (let [api1 (api
               (GET "/30" [] (ok)))
        api2 (api
               {:api {:disable-api-middleware? true}}
               (GET "/30" [] (ok)))
        app (GET "/30" [] (ok))

        request {:request-method :get, :uri "/30"}
        count 100000
        call1 #(api1 request)
        call2 #(api2 request)
        call3 #(app request)]

    (title "api1")
    (time
      (dotimes [_ count]
        (call1)))
    (cc/quick-bench (call1))

    (title "api2")
    (time
      (dotimes [_ count]
        (call2)))
    #_(cc/quick-bench (call2))

    (title "app")
    (time
      (dotimes [_ count]
        (call3)))
    #_(cc/quick-bench (call3))))