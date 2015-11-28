(ns compojure.api.test-utils
  (:require [cheshire.core :as cheshire]
            [clojure.string :as str]
            [peridot.core :as p])
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

(defn raw-post*
  "Takes optional map, with optional keys :headers, :data and :content-type"
  [app uri & [options]]
  (let [{{:keys [status body]} :response}
        (-> (p/session app)
            (p/request uri
                       :request-method :post
                       :headers (or (:headers options) {})
                       :content-type (or (:content-type options) "application/json")
                       :body (.getBytes (:body options))))]
    [status (read-body body)]))

(defn post* [app uri & [options]]
  (let [[status body] (raw-post* app uri options)]
    [status (parse-body body)]))

(defn headers-post* [app uri headers]
  (let [[status body] (raw-post* app uri {:body "" :headers headers})]
    [status (parse-body body)]))
