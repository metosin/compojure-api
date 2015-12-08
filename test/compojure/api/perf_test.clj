(ns compojure.api.perf-test
  (:require [compojure.api.sweet :refer :all]
            [compojure.api.test-utils :refer :all]
            [criterium.core :as cc]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

;;
;; start repl with `lein perf repl`
;; perf measured with the following setup:
;;
;; Model Name:	          MacBook Pro
;; Model Identifier:	    MacBookPro11,3
;; Processor Name:	      Intel Core i7
;; Processor Speed:	      2,5 GHz
;; Number of Processors:	1
;; Total Number of Cores:	4
;; L2 Cache (per Core):	  256 KB
;; L3 Cache:	            6 MB
;; Memory:	              16 GB
;;

(defn title [s]
  (println
    (str "\n\u001B[35m"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\n## " s " ##\n"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\u001B[0m\n")))

(s/defschema Order {:id s/Str
                    :name s/Str
                    (s/optional-key :description) s/Str
                    :address (s/maybe {:street s/Str
                                       :country (s/enum "FI" "PO")})
                    :orders [{:name #"^k"
                              :price s/Any
                              :shipping s/Bool}]})

(defn bench []


  (let [app (api
              (GET* "/30" []
                (ok {:result 30})))
        call #(get* app "/30")]

    (title "GET JSON")

    (assert (= {:result 30} (second (call))))
    (cc/bench (call)))

  ; 26µs => 26µs (-0%)

  (let [app (api
              (POST* "/plus" []
                :return {:result s/Int}
                :body-params [x :- s/Int, y :- s/Int]
                (ok {:result (+ x y)})))
        data (json {:x 10, :y 20})
        call #(post* app "/plus" data)]

    (title "JSON POST with 2-way coercion")

    (assert (= {:result 30} (second (call))))
    (cc/bench (call)))

  ;; 87µs => 65µs (-25%)

  (let [app (api
              (context* "/a" []
                (context* "/b" []
                  (context* "/c" []
                    (POST* "/plus" []
                      :return {:result s/Int}
                      :body-params [x :- s/Int, y :- s/Int]
                      (ok {:result (+ x y)}))))))
        data (json {:x 10, :y 20})
        call #(post* app "/a/b/c/plus" data)]

    (title "JSON POST with 2-way coercion + contexts")

    (assert (= {:result 30} (second (call))))
    (cc/bench (call)))

  ;; 102µs => 78µs (-24%)

  (let [app (api
              (POST* "/echo" []
                :return Order
                :body [order Order]
                (ok order)))
        data (json {:id "123"
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

    (s/check Order (second (call)))
    (cc/bench (call)))

  ;; 311µs => 194µs (-38%)

  )

(comment
  (bench))
