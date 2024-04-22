(ns compojure.api.middleware-test
  (:require [compojure.api.middleware :refer :all]
            [compojure.api.exception :as ex]
            [clojure.test :refer [deftest is testing]]
            [ring.util.http-response :refer [ok]]
            [ring.util.http-status :as status]
            [ring.util.test])
  (:import (java.io PrintStream ByteArrayOutputStream)))

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

(deftest encode?-test
  (doseq [[?body ?serializable? ?res :as test-case]
          [[5 true true]
           [5 false false]
           ["foobar" true true]
           ["foobar" false false]
           [{:foobar "1"} false true]
           [{:foobar "1"} true true]
           [[1 2 3] false true]
           [[1 2 3] true true]
           [(ring.util.test/string-input-stream "foobar") false false]]]

    (testing (pr-str test-case)
      (is (= (encode? nil
                      {:body ?body
                       :compojure.api.meta/serializable? ?serializable?})
             ?res)))))

(def default-options (:exceptions api-middleware-defaults))

(defn- call-async [handler request]
  (let [result (promise)]
    (handler request #(result [:ok %]) #(result [:fail %]))
    (if-let [[status value] (deref result 1500 nil)]
      (if (= status :ok)
        value
        (throw value))
      (throw (Exception. "Timeout while waiting for the request handler.")))))

(deftest wrap-exceptions-test
  (with-out-str
    (without-err
      (let [exception (RuntimeException. "kosh")
            exception-class (.getName (.getClass exception))
            handler (-> (fn [_] (throw exception))
                        (wrap-exceptions default-options))
            async-handler (-> (fn [_ _ raise] (raise exception))
                              (wrap-exceptions default-options))]

        (testing "converts exceptions into safe internal server errors"
          (is (= {:status status/internal-server-error
                  :body {:class exception-class
                         :type "unknown-exception"}}
                 (-> (handler {})
                     (select-keys [:status :body]))))
          (is (= {:status status/internal-server-error
                  :body {:class exception-class
                         :type "unknown-exception"}}
                 (-> (call-async async-handler {})
                     (select-keys [:status :body]))))))))

  (with-out-str
    (without-err
      (testing "Thrown ex-info type can be matched"
        (let [handler (-> (fn [_] (throw (ex-info "kosh" {:type ::test})))
                          (wrap-exceptions (assoc-in default-options [:handlers ::test] (fn [ex _ _] {:status 500 :body "hello"}))))]
          (is (= {:status status/internal-server-error
                  :body "hello"}
                 (select-keys (handler {}) [:status :body])))))))

  (without-err
    (testing "Default handler logs exceptions to console"
      (let [handler (-> (fn [_] (throw (RuntimeException. "kosh")))
                        (wrap-exceptions default-options))]
        (is (= "ERROR kosh\n" (with-out-str (handler {})))))))

  (without-err
    (testing "Default request-parsing handler does not log messages"
      (let [handler (-> (fn [_] (throw (ex-info "Error parsing request" {:type ::ex/request-parsing} (RuntimeException. "Kosh"))))
                        (wrap-exceptions default-options))]
        (is (= "" (with-out-str (handler {})))))))

  (without-err
    (testing "Logging can be added to a exception handler"
      (let [handler (-> (fn [_] (throw (ex-info "Error parsing request" {:type ::ex/request-parsing} (RuntimeException. "Kosh"))))
                        (wrap-exceptions (assoc-in default-options [:handlers ::ex/request-parsing] (ex/with-logging ex/request-parsing-handler :info))))]
        (is (= "INFO Error parsing request\n" (with-out-str (handler {}))))))))

(deftest issue-228-test ; "compose-middeleware strips nils aways. #228"
  (let [times2-mw (fn [handler]
                    (fn [request]
                      (* 2 (handler request))))]
    (is (= 6 (((compose-middleware [nil times2-mw nil]) (constantly 3)) nil)))))
