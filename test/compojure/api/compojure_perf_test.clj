(ns compojure.api.compojure-perf-test
  (:require [compojure.core :as c]
            [compojure.api.sweet :as s]
            [criterium.core :as cc]
            [ring.util.http-response :refer :all]))

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

(defn compojure-bench []

  ;; 3.8µs
  ;; 2.6µs
  (let [app (c/routes
              (c/GET "/a/b/c/1" [] "ok")
              (c/GET "/a/b/c/2" [] "ok")
              (c/GET "/a/b/c/3" [] "ok")
              (c/GET "/a/b/c/4" [] "ok")
              (c/GET "/a/b/c/5" [] "ok"))

        call #(app {:request-method :get :uri "/a/b/c/5"})]

    (title "Compojure - GET flattened")
    (assert (-> (call) :body (= "ok")))
    (cc/quick-bench (call)))

  ;; 15.9µs
  ;; 11.6µs
  (let [app (c/context "/a" []
              (c/context "/b" []
                (c/context "/c" []
                  (c/GET "/1" [] "ok")
                  (c/GET "/2" [] "ok")
                  (c/GET "/3" [] "ok")
                  (c/GET "/4" [] "ok")
                  (c/GET "/5" [] "ok"))
                (c/GET "/1" [] "ok")
                (c/GET "/2" [] "ok")
                (c/GET "/3" [] "ok")
                (c/GET "/4" [] "ok")
                (c/GET "/5" [] "ok"))
              (c/GET "/1" [] "ok")
              (c/GET "/2" [] "ok")
              (c/GET "/3" [] "ok")
              (c/GET "/4" [] "ok")
              (c/GET "/5" [] "ok"))

        call #(app {:request-method :get :uri "/a/b/c/5"})]

    (title "Compojure - GET with context")
    (assert (-> (call) :body (= "ok")))
    (cc/quick-bench (call))))

(defn compojure-api-bench []

  ;; 3.8µs
  ;; 2.7µs
  (let [app (s/routes
              (s/GET "/a/b/c/1" [] "ok")
              (s/GET "/a/b/c/2" [] "ok")
              (s/GET "/a/b/c/3" [] "ok")
              (s/GET "/a/b/c/4" [] "ok")
              (s/GET "/a/b/c/5" [] "ok"))

        call #(app {:request-method :get :uri "/a/b/c/5"})]

    (title "Compojure API - GET flattened")
    (assert (-> (call) :body (= "ok")))
    (cc/quick-bench (call)))

  ;; 20.0µs
  ;; 17.0µs
  (let [app (s/context "/a" []
              (s/context "/b" []
                (s/context "/c" []
                  (s/GET "/1" [] "ok")
                  (s/GET "/2" [] "ok")
                  (s/GET "/3" [] "ok")
                  (s/GET "/4" [] "ok")
                  (s/GET "/5" [] "ok"))
                (s/GET "/1" [] "ok")
                (s/GET "/2" [] "ok")
                (s/GET "/3" [] "ok")
                (s/GET "/4" [] "ok")
                (s/GET "/5" [] "ok"))
              (s/GET "/1" [] "ok")
              (s/GET "/2" [] "ok")
              (s/GET "/3" [] "ok")
              (s/GET "/4" [] "ok")
              (s/GET "/5" [] "ok"))

        call #(app {:request-method :get :uri "/a/b/c/5"})]

    (title "Compojure API - GET with context")
    (assert (-> (call) :body (= "ok")))
    (cc/quick-bench (call))))

(defn compojure-api-mw-bench []

  ;; 47.0µs (15 + 3906408 calls)
  (let [calls (atom nil)
        mw (fn [handler x] (swap! calls update x (fnil inc 0)) (fn [req] (handler req)))
        app (s/context "/a" []
              :middleware [[mw :a]]
              (s/context "/b" []
                :middleware [[mw :b]]
                (s/context "/c" []
                  :middleware [[mw :c]]
                  (s/GET "/1" [] :middleware [[mw :c1]] "ok")
                  (s/GET "/2" [] :middleware [[mw :c2]] "ok")
                  (s/GET "/3" [] :middleware [[mw :c3]] "ok")
                  (s/GET "/4" [] :middleware [[mw :c4]] "ok")
                  (s/GET "/5" [] :middleware [[mw :c5]] "ok"))
                (s/GET "/1" [] :middleware [[mw :b1]] "ok")
                (s/GET "/2" [] :middleware [[mw :b2]] "ok")
                (s/GET "/3" [] :middleware [[mw :b3]] "ok")
                (s/GET "/4" [] :middleware [[mw :b4]] "ok")
                (s/GET "/5" [] :middleware [[mw :b5]] "ok"))
              (s/GET "/1" [] :middleware [[mw :a1]] "ok")
              (s/GET "/2" [] :middleware [[mw :a2]] "ok")
              (s/GET "/3" [] :middleware [[mw :a3]] "ok")
              (s/GET "/4" [] :middleware [[mw :a4]] "ok")
              (s/GET "/5" [] :middleware [[mw :a5]] "ok"))

        call #(app {:request-method :get :uri "/a/b/c/5"})]

    (clojure.pprint/pprint {:calls @calls, :total (->> @calls vals (reduce +))})
    (reset! calls nil)
    (title "Compojure API - GET with context with middleware")
    (assert (-> (call) :body (= "ok")))
    (cc/quick-bench (call))
    (clojure.pprint/pprint {:calls @calls, :total (->> @calls vals (reduce +))})))

(defn bench []
  (compojure-bench)
  (compojure-api-bench)
  (compojure-api-mw-bench))

(comment
  (bench))
