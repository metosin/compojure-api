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

  (title "...compojure...")

  (let [app (c/routes
              (c/GET "/a/b/c/1" [] "ok")
              (c/GET "/a/b/c/2" [] "ok")
              (c/GET "/a/b/c/3" [] "ok")
              (c/GET "/a/b/c/4" [] "ok")
              (c/GET "/a/b/c/5" [] "ok")
              (c/GET "/a/b/1" [] "ok")
              (c/GET "/a/b/2" [] "ok")
              (c/GET "/a/b/3" [] "ok")
              (c/GET "/a/b/4" [] "ok")
              (c/GET "/a/b/5" [] "ok")
              (c/GET "/a/1" [] "ok")
              (c/GET "/a/2" [] "ok")
              (c/GET "/a/3" [] "ok")
              (c/GET "/a/4" [] "ok")
              (c/GET "/a/5" [] "ok"))

        call #(app {:request-method :get :uri "/a/b/c/1"})]

    (title "GET with context")
    (./aprint (call))
    (cc/quick-bench (call)))

  ;; 1.9µs

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

        call #(app {:request-method :get :uri "/a/b/c/1"})]

    (title "GET with context")
    (./aprint (call))
    (cc/quick-bench (call)))

  ;; 10.7µs

  )

(defn compojure-api-bench []

  (title "...compojure-api...")

  (let [app (s/routes
              (s/GET "/a/b/c/1" [] "ok")
              (s/GET "/a/b/c/2" [] "ok")
              (s/GET "/a/b/c/3" [] "ok")
              (s/GET "/a/b/c/4" [] "ok")
              (s/GET "/a/b/c/5" [] "ok")
              (s/GET "/a/b/1" [] "ok")
              (s/GET "/a/b/2" [] "ok")
              (s/GET "/a/b/3" [] "ok")
              (s/GET "/a/b/4" [] "ok")
              (s/GET "/a/b/5" [] "ok")
              (s/GET "/a/1" [] "ok")
              (s/GET "/a/2" [] "ok")
              (s/GET "/a/3" [] "ok")
              (s/GET "/a/4" [] "ok")
              (s/GET "/a/5" [] "ok"))

        call #(app {:request-method :get :uri "/a/b/c/1"})]

    (title "GET with context")
    (./aprint (call))
    (cc/quick-bench (call)))

  ;; ???µs

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

        call #(app {:request-method :get :uri "/a/b/c/1"})]

    (title "GET with context")
    (./aprint (call))
    (cc/quick-bench (call)))

  ;; 19.7µs

  )

(defn bench []
  (compojure-bench)
  (compojure-api-bench))

(comment
  (bench))
