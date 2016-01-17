(ns compojure.api.middleware-test
  (:require [compojure.api.middleware :refer :all]
            [compojure.api.exception :as ex]
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

(def default-options (:exceptions api-middleware-defaults))

(facts "wrap-exceptions"
  (with-out-str
    (without-err
      (let [exception (RuntimeException. "kosh")
            exception-class (.getName (.getClass exception))
            handler (-> (fn [_] (throw exception))
                        (wrap-exceptions default-options))]

        (fact "converts exceptions into safe internal server errors"
          (handler {}) => (contains {:status status/internal-server-error
                                     :body (contains {:class exception-class
                                                      :type "unknown-exception"})})))))

  (with-out-str
    (without-err
      (fact "Slingshot exception map type can be matched"
        (let [handler (-> (fn [_] (throw+ {:type ::test} (RuntimeException. "kosh")))
                          (wrap-exceptions (assoc-in default-options [:handlers ::test] (fn [ex _ _] {:status 500 :body "hello"}))))]
          (handler {}) => (contains {:status status/internal-server-error
                                     :body "hello"})))))

  (without-err
    (fact "Default handler logs exceptions to console"
      (let [handler (-> (fn [_] (throw (RuntimeException. "kosh")))
                        (wrap-exceptions default-options))]
        (with-out-str (handler {})) => "ERROR kosh\n")))

  (without-err
    (fact "Default request-parsing handler does not log messages"
      (let [handler (-> (fn [_] (throw (ex-info "Error parsing request" {:type ::ex/request-parsing} (RuntimeException. "Kosh"))))
                        (wrap-exceptions default-options))]
        (with-out-str (handler {})) => "")))

  (without-err
    (fact "Logging can be added to a exception handler"
      (let [handler (-> (fn [_] (throw (ex-info "Error parsing request" {:type ::ex/request-parsing} (RuntimeException. "Kosh"))))
                        (wrap-exceptions (assoc-in default-options [:handlers ::ex/request-parsing] (ex/with-logging ex/request-parsing-handler :info))))]
        (with-out-str (handler {})) => "INFO Error parsing request\n"))))
