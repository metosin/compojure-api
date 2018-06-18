(ns compojure.api.test-utils
  (:require [clojure.string :as str]
            [peridot.core :as p]
            [muuntaja.core :as m]
            [compojure.api.routes :as routes]
            [compojure.api.middleware :as mw])
  (:import (java.io InputStream)
           (muuntaja.protocols ByteResponse)))

(def muuntaja (mw/create-muuntaja))

(defn slurp-body [body]
  (if (satisfies? muuntaja.protocols/IntoInputStream body)
    (m/slurp body)
    body))

(defn parse-body [body]
  (if (or (string? body) (instance? ByteResponse body) (instance? InputStream body))
    (m/decode muuntaja "application/json" (slurp-body body))
    body))

(defn extract-schema-name [ref-str]
  (last (str/split ref-str #"/")))

(defn find-definition [spec ref]
  (let [schema-name (keyword (extract-schema-name ref))]
    (get-in spec [:definitions schema-name])))

(defn json-stream [x]
  (m/encode muuntaja "application/json" x))

(def json-string (comp slurp json-stream))

(defn parse [x]
  (m/decode muuntaja "application/json" x))

(defn follow-redirect [state]
  (if (some-> state :response :headers (get "Location"))
    (p/follow-redirect state)
    state))

(def ^:dynamic *async?* (= "true" (System/getProperty "compojure-api.test.async")))

(defn- call-async [handler request]
  (let [result (promise)]
    (handler request #(result [:ok %]) #(result [:fail %]))
    (if-let [[status value] (deref result 1500 nil)]
      (if (= status :ok)
        value
        (throw value))
      (throw (Exception. (str "Timeout while waiting for the request handler. "
                              request))))))

(defn call
  "Call handler synchronously or asynchronously depending on *async?*."
  [handler request]
  (if *async?*
    (call-async handler request)
    (handler request)))

(defn raw-get* [app uri & [params headers]]
  (let [{{:keys [status body headers]} :response}
        (-> (cond->> app *async?* (partial call-async))
            (p/session)
            (p/request uri
                       :request-method :get
                       :params (or params {})
                       :headers (or headers {}))
            follow-redirect)]
    [status (slurp-body body) headers]))

(defn get* [app uri & [params headers]]
  (let [[status body headers]
        (raw-get* app uri params headers)]
    [status (parse-body body) headers]))

(defn form-post* [app uri params]
  (let [{{:keys [status body]} :response}
        (-> (p/session app)
            (p/request uri
                       :request-method :post
                       :params params))]
    [status (parse-body body)]))

(defn raw-put-or-post* [app uri method data content-type headers]
  (let [{{:keys [status body]} :response}
        (-> (p/session app)
            (p/request uri
                       :request-method method
                       :headers (or headers {})
                       :content-type (or content-type "application/json")
                       :body (.getBytes data)))]
    [status (slurp-body body)]))

(defn raw-post* [app uri & [data content-type headers]]
  (raw-put-or-post* app uri :post data content-type headers))

(defn post* [app uri & [data]]
  (let [[status body] (raw-post* app uri data)]
    [status (parse-body body)]))

(defn put* [app uri & [data]]
  (let [[status body] (raw-put-or-post* app uri :put data nil nil)]
    [status (parse-body body)]))

(defn headers-post* [app uri headers]
  (let [[status body] (raw-post* app uri "" nil headers)]
    [status (parse-body body)]))

;;
;; ring-request
;;

(defn ring-request [m format data]
  {:uri "/echo"
   :request-method :post
   :body (m/encode m format data)
   :headers {"content-type" format
             "accept" format}})

;;
;; get-spec
;;

(defn extract-paths [app]
  (-> app routes/get-routes routes/all-paths))

(defn get-spec [app]
  (let [[status spec] (get* app "/swagger.json" {})]
    (assert (= status 200))
    (if (:paths spec)
      (update-in spec [:paths] (fn [paths]
                                 (into
                                   (empty paths)
                                   (for [[k v] paths]
                                     [(if (= k (keyword "/"))
                                        "/" (str "/" (name k))) v]))))
      spec)))
