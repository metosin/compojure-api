(ns compojure.api.middleware-test
  (:require [compojure.api.middleware :refer :all]
            [midje.sweet :refer :all]
            [ring.util.http-response :refer [ok]]
            [ring.util.http-status :as status]
            ring.util.test
            [slingshot.slingshot :refer [throw+]])
  (:import [java.io PrintStream ByteArrayOutputStream]))

(defmacro without-err
  "Evaluates exprs in a context in which *err* is bound to a fresh
  StringWriter.  Returns the string created by any nested printing
  calls."
  [& body]
  `(let [s# (PrintStream. (ByteArrayOutputStream.))
         err# (System/err)]
     (System/setErr s#)
     (try
       ~@body
       (finally
         (System/setErr err#)))))

(facts serializable?
  (tabular
    (fact
      (serializable? nil
                     {:body ?body
                      :compojure.api.meta/serializable? ?serializable?}) => ?res)
    ?body ?serializable? ?res
    5 true true
    5 false false
    "foobar" true true
    "foobar" false false

    {:foobar "1"} false true
    {:foobar "1"} true true
    [1 2 3] false true
    [1 2 3] true true

    (ring.util.test/string-input-stream "foobar") false false))

(facts "wrap-exceptions"
  (with-out-str
    (without-err
      (let [exception (RuntimeException. "kosh")
            exception-class (.getName (.getClass exception))
            handler (-> (fn [_] (throw exception))
                        (wrap-exceptions {}))]

        (fact "converts exceptions into safe internal server errors"
          (handler {}) => (contains {:status status/internal-server-error
                                     :body (contains {:class exception-class
                                                      :type "unknown-exception"})})))))

  (with-out-str
   (without-err
     (fact "Slingshot exception map type can be matched"
       (let [handler (-> (fn [_] (throw+ {:type ::test} (RuntimeException. "kosh")))
                         (wrap-exceptions {:handlers {::test (fn [ex _ _] {:status 500 :body "hello"})}}))]
         (handler {}) => (contains {:status status/internal-server-error
                                    :body "hello"}))))))
