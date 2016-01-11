(ns compojure.api.swagger
  (:require [compojure.api.common :refer :all]
            [compojure.core :refer :all]
            [compojure.api.core :refer [GET* undocumented*]]
            [compojure.api.common :refer [extract-parameters]]
            [compojure.api.middleware :as mw]
            [ring.util.http-response :refer [ok]]
            [ring.swagger.common :as rsc]
            [ring.swagger.middleware :as rsm]
            [ring.swagger.core :as swagger]
            [ring.swagger.ui :as rsui]
            [ring.swagger.swagger2 :as swagger2]))

;;
;; generate schema names
;;

#_(defn ensure-parameter-schema-names [endpoint]
  (if (get-in endpoint [:parameters :body])
    (update-in endpoint [:parameters :body] #(swagger/with-named-sub-schemas % "Body"))
    endpoint))

#_(defn ensure-return-schema-names [endpoint]
  (if (get-in endpoint [:responses])
    (update-in
      endpoint [:responses]
      (fn [responses]
        (into {} (map
                   (fn [[k v]]
                     [k (update-in v [:schema] swagger/with-named-sub-schemas "Response")])
                   responses))))
    endpoint))

;;
;; routes
;;

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

(defn swagger-docs
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
  (let [[path body] (if (string? (first body))
                      [(first body) (rest body)]
                      ["/swagger.json" body])
        [extra-info] (extract-parameters body)]
    (GET* path request
      :no-doc true
      :name ::swagger
      (let [runtime-info (rsm/get-swagger-data request)
            base-path {:basePath (base-path request)}
            options (:ring-swagger (mw/get-options request))
            routes (:routes (mw/get-options request))
            swagger (rsc/deep-merge base-path routes extra-info runtime-info)
            spec (swagger2/swagger-json swagger options)]
        (ok spec)))))

#_(defn swagger-spec-path [api]
  (some-> api meta :lookup ::swagger first first))

#_(defn swagger-api? [api]
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
