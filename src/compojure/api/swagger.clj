(ns compojure.api.swagger
  (:require [compojure.api.common :refer :all]
            [compojure.api.core :refer [GET undocumented]]
            [compojure.api.common :refer [extract-parameters]]
            [compojure.api.middleware :as mw]
            [ring.util.http-response :refer [ok]]
            [ring.swagger.common :as rsc]
            [ring.swagger.validator :as v]
            [ring.swagger.middleware :as rsm]
            [ring.swagger.core :as swagger]
            [ring.swagger.ui :as rsui]
            [ring.swagger.swagger2 :as swagger2]
            [compojure.api.routes :as routes]
            [cheshire.core :as cheshire]))

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

(defn transform-operations [swagger]
  (->> swagger
       (swagger2/transform-operations routes/non-nil-routes)
       (swagger2/transform-operations routes/strip-no-doc-endpoints)))

(defn base-path [request]
  (let [context (swagger/context request)]
    (if (= "" context) "/" context)))

;;
;; Public api
;;

(defn swagger-ui [& params]
  (undocumented
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
    (GET path request
        [extra-info] (common/extract-parameters body false)]
      :no-doc true
      :name ::swagger
      (let [runtime-info (rsm/get-swagger-data request)
            base-path {:basePath (base-path request)}
            options (:ring-swagger (mw/get-options request))
            paths (:paths (mw/get-options request))
            swagger (rsc/deep-merge base-path paths extra-info runtime-info)
            spec (swagger2/swagger-json swagger options)]
        (ok spec)))))

(defn swagger-spec-path [app]
  (some-> app
          routes/get-routes
          routes/route-lookup-table
          ::swagger
          keys
          first))

(defn validate
  "Validates a api. If the api is Swagger-enabled, the swagger-spec
  is requested and validated against the JSON Schema. Returns either
  the (valid) api or throws an exception."
  [api]
  (when-let [uri (swagger-spec-path api)]
    (let [{status :status :as response} (api {:request-method :get
                                              :uri uri
                                              mw/rethrow-exceptions? true})
          body (-> response :body slurp (cheshire/parse-string true))]

      (when-not (= status 200)
        (throw (ex-info (str "Coudn't read swagger spec from " uri)
                        {:status status
                         :body body})))

      (when-let [errors (seq (v/validate body))]
        (throw (ex-info (str "Invalid swagger spec from " uri)
                        {:errors errors
                         :body body})))))
  api)
