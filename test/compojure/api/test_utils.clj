(ns compojure.api.test-utils
  (:require [cheshire.core :as cheshire]
            [clojure.string :as str]
            [peridot.core :as p]
            [compojure.api.routes :as routes])
  (:import [java.io InputStream]))

(defn read-body [body]
  (if (instance? InputStream body)
    (slurp body)
    body))

(defn parse-body [body]
  (let [body (read-body body)
        body (if (instance? String body)
               (cheshire/parse-string body true)
               body)]
    body))

(defn extract-schema-name [ref-str]
  (last (str/split ref-str #"/")))

(defn find-definition [spec ref]
  (let [schema-name (keyword (extract-schema-name ref))]
    (get-in spec [:definitions schema-name])))

;;
;; integration tests
;;

;;
;; common
;;

(defn json [x] (cheshire/generate-string x))

(defn follow-redirect [state]
  (if (some-> state :response :headers (get "Location"))
    (p/follow-redirect state)
    state))

(defn raw-get* [app uri & [params headers]]
  (let [{{:keys [status body headers]} :response}
        (-> (p/session app)
            (p/request uri
                       :request-method :get
                       :params (or params {})
                       :headers (or headers {}))
            follow-redirect)]
    [status (read-body body) headers]))

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

(defn raw-post* [app uri & [data content-type headers]]
  (let [{{:keys [status body]} :response}
        (-> (p/session app)
            (p/request uri
                       :request-method :post
                       :headers (or headers {})
                       :content-type (or content-type "application/json")
                       :body (.getBytes data)))]
    [status (read-body body)]))

(defn post* [app uri & [data]]
  (let [[status body] (raw-post* app uri data)]
    [status (parse-body body)]))

(defn headers-post* [app uri headers]
  (let [[status body] (raw-post* app uri "" nil headers)]
    [status (parse-body body)]))

;;
;; Route compilation
;;

(defmacro ignore-non-documented-route-warning [& body]
  `(with-out-str
     (binding [routes/*fail-on-missing-route-info* false]
       ~@body)))

;;
;; get-spec
;;

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
