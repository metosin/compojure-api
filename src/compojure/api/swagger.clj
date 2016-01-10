(ns compojure.api.swagger
  (:require [clojure.set :refer [union]]
            [clojure.walk :as walk]
            [compojure.api.common :refer :all]
            [compojure.api.routes :as routes]
            [compojure.api.meta :as m]
            [compojure.core :refer :all]
            [compojure.api.core :refer [GET* undocumented*]]
            [compojure.api.middleware :as mw]
            [ring.util.http-response :refer [ok]]
            [ring.swagger.common :as rsc]
            [ring.swagger.middleware :as rsm]
            [ring.swagger.core :as swagger]
            [ring.swagger.ui :as rsui]
            [ring.swagger.swagger2 :as swagger2]
            [schema.core :as s]
            [compojure.api.routing :as r]))

;;
;; ensure path parameters
;;

(defn path-params [s]
  (map (comp keyword second) (re-seq #":(.[^:|(/]*)[/]?" s)))

(defn string-path-parameters [uri]
  (let [params (path-params uri)]
    (if (seq params)
      (zipmap params (repeat String)))))

(defn- remove-keyword-namespaces [schema]
  (walk/prewalk
    (fn [x]
      (if (and (keyword? x) (namespace x))
        (keyword (name x))
        x))
    schema))

(defn ensure-path-parameters [uri route-with-meta]
  (if (seq (path-params uri))
    (update-in route-with-meta [:metadata :parameters :path]
               #(dissoc (merge (string-path-parameters uri)
                               (remove-keyword-namespaces %))
                        s/Keyword))
    route-with-meta))

;;
;; generate schema names
;;

(defn ensure-parameter-schema-names [route-with-meta]
  (if (get-in route-with-meta [:metadata :parameters :body])
    (update-in route-with-meta [:metadata :parameters :body]
               #(swagger/with-named-sub-schemas
                 (remove-keyword-namespaces %) "Body"))
    route-with-meta))

(defn ensure-return-schema-names [route-with-meta]
  (if (get-in route-with-meta [:metadata :responses])
    (update-in
      route-with-meta [:metadata :responses]
      (fn [responses]
        (into {} (map
                   (fn [[k v]]
                     [k (update-in v [:schema] swagger/with-named-sub-schemas "Response")])
                   responses))))
    route-with-meta))

;;
;; routes
;;

#_(defn attach-meta-data-to-route [[{:keys [uri] :as route} [_ {:keys [body meta]}]]]
  (let [meta (merge-meta meta (route-metadata body))
        route-with-meta (if-not (empty? meta) (assoc route :metadata meta) route)]
    (->> route-with-meta
         (ensure-path-parameters uri)
         ensure-parameter-schema-names
         ensure-return-schema-names
         ->swagger2)))

; TODO: remove
(defn swagger-info [handler]
  (routes/->ring-swagger (r/get-routes handler)))

(defn- deprecated! [& args]
  (apply println (concat ["DEPRECATED:"] args)))

(defn base-path [request]
  (let [context (swagger/context request)]
    (if (= "" context) "/" context)))

;;
;; Public api
;;

(defn swagger-ui [& params]
  (undocumented*
    (apply rsui/swagger-ui params)))

(defn select-swagger2-parameters
  "Validates the given Swagger 2.0 format against the Schema. Prints warnings to STDOUT
  if old input was used. Fails with missing 2.0 keys."
  [info]
  (let [mapping {:version [:info :version]
                 :title [:info :title]
                 :description [:info :description]
                 :termsOfServiceUrl [:info :termsOfService]
                 :license [:info :license :name]}
        old-keys (set (keys mapping))
        info (reduce
               (fn [info [k v]]
                 (if (old-keys k)
                   (do
                     (deprecated! "swagger-docs -" k "is deprecated, see docs for details.")
                     (-> info
                         (dissoc k)
                         (assoc-in (mapping k) v)))
                   info))
               info
               info)]
    info))

(defmacro swagger-docs
  "Route to serve the swagger api-docs. If the first
   parameter is a String, it is used as a url for the
   api-docs, otherwise \"/swagger.json\" will be used.
   Next Keyword value pairs OR a map for meta-data.
   Meta-data can be any valid swagger 2.0 data. Common
   case is to introduce API Info and Tags here:

       {:info {:version \"1.0.0\"
               :title \"Sausages\"
               :description \"Sausage description\"
               :termsOfService \"http://helloreverb.com/terms/\"
               :contact {:name \"My API Team\"
                         :email \"foo@example.com\"
                         :url \"http://www.metosin.fi\"}
               :license {:name: \"Eclipse Public License\"
                         :url: \"http://www.eclipse.org/legal/epl-v10.html\"}}
        :tags [{:name \"sausages\", :description \"Sausage api-set}]}"
  [& body]
  (let [[path key-values] (if (string? (first body))
                            [(first body) (rest body)]
                            ["/swagger.json" body])
        first-value (first key-values)
        extra-info (select-swagger2-parameters (if (map? first-value)
                                                 first-value
                                                 (apply hash-map key-values)))]
    `(GET* ~path {:as request#}
       :no-doc true
       :name ::swagger
       (let [runtime-info# (rsm/get-swagger-data request#)
             base-path# {:basePath (base-path request#)}
             options# (:ring-swagger (mw/get-options request#))
             routes# (:routes (mw/get-options request#))]
         (ok
           (let [swagger# (rsc/deep-merge base-path#
                                          routes#
                                          ~extra-info
                                          runtime-info#)
                 result# (swagger2/swagger-json swagger# options#)]
             result#))))))

(defn swagger-spec-path [api]
  (some-> api meta :lookup ::swagger first first))

(defn swagger-api? [api]
  (boolean (swagger-spec-path api)))

;; FIXME!
(defn validate
  "Validates a api. If the api is Swagger-enabled, the swagger-docs
  endpoint is requested. Returns either the (valid) api or throws an
  exception."
  [api]
  (throw (ex-info "Reimplement" {}))
  #_(let [{:keys [routes options]} (meta api)
        routes (routes/route-vector-to-route-map routes)]
    (assert (not (nil? routes)) "Api did not contain route definitions.")
    (when (swagger-api? api)

      ;; validate routes locally to get the unmasked root cause
      (s/with-fn-validation
        (swagger2/swagger-json routes options))

      ;; validate the swagger spec
      (let [{:keys [status body]} (api {:request-method :get
                                        :uri (swagger-spec-path api)
                                        mw/rethrow-exceptions? true})]
        (if-not (= status 200)
          (throw (IllegalArgumentException. ^String (slurp body))))))
    api))
