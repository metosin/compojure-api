(ns compojure.api.swagger
  (:require [compojure.api.core :as c]
            [compojure.api.common :as common]
            [compojure.api.middleware :as mw]
            [ring.util.http-response :refer [ok]]
            [ring.swagger.common :as rsc]
            [ring.swagger.middleware :as rsm]
            [ring.swagger.core :as swagger]
            [ring.swagger.swagger-ui :as swagger-ui]
            [ring.swagger.swagger2 :as swagger2]
            [compojure.api.routes :as routes]))

(defn base-path [request]
  (let [context (swagger/context request)]
    (if (= "" context) "/" context)))

(defn swagger-spec-path
  [app]
  (some-> app
          routes/get-routes
          routes/route-lookup-table
          ::swagger
          keys
          first))

(defn transform-operations [swagger]
  (->> swagger
       (swagger2/transform-operations routes/non-nil-routes)
       (swagger2/transform-operations routes/strip-no-doc-endpoints)))

(defn swagger-ui [options]
  (assert (map? options) "Since 1.1.11, compojure.api.swagger/swagger-ui takes just one map as argument, with `:path` for the path.")
  (c/undocumented
    (swagger-ui/swagger-ui options)))

(defn swagger-docs [{:keys [path] :or {path "/swagger.json"} :as options}]
  (assert (map? options) "Since 1.1.11, compojure.api.swagger/swagger-docs takes just one map as argument, with `:path` for the path.")
  (let [extra-info (dissoc options :path)]
    (c/GET path request
      :no-doc true
      :name ::swagger
      (let [runtime-info (rsm/get-swagger-data request)
            base-path {:basePath (base-path request)}
            options (:ring-swagger (mw/get-options request))
            paths (:paths (mw/get-options request))
            swagger (apply rsc/deep-merge (keep identity [base-path paths extra-info runtime-info]))
            spec (swagger2/swagger-json swagger options)]
        (ok spec)))))

;;
;; Public api
;;

(def swagger-defaults {:ui "/", :spec "/swagger.json"})

(defn swagger-routes
  "Returns routes for swagger-articats (ui & spec). Accepts an options map, with the
  following options:
  **:ui**              Path for the swagger-ui (defaults to \"/\").
                       Setting the value to nil will cause the swagger-ui not to be mounted
  **:spec**            Path for the swagger-spec (defaults to \"/swagger.json\")
                       Setting the value to nil will cause the swagger-ui not to be mounted
  **:data**            Swagger data in the Ring-Swagger format.
  **:options**
    **:ui**            Options to configure the ui
    **:spec**          Options to configure the spec. Nada at the moment.
  Example options:
    {:ui \"/api-docs\"
     :spec \"/swagger.json\"
     :options {:ui {:jsonEditor true}
               :spec {}}
     :data {:basePath \"/app\"
            :info {:version \"1.0.0\"
                   :title \"Sausages\"
                   :description \"Sausage description\"
                   :termsOfService \"http://helloreverb.com/terms/\"
                   :contact {:name \"My API Team\"
                             :email \"foo@example.com\"
                             :url \"http://www.metosin.fi\"}
                   :license {:name: \"Eclipse Public License\"
                             :url: \"http://www.eclipse.org/legal/epl-v10.html\"}}
            :tags [{:name \"sausages\", :description \"Sausage api-set\"}]}}"
  ([] (swagger-routes {}))
  ([options]
   (let [{:keys [ui spec data] {ui-options :ui} :options} (merge swagger-defaults options)
         path (apply str (remove clojure.string/blank? [(:basePath data) spec]))]
     (if (or ui spec)
       (c/routes
         (if ui (swagger-ui (merge (if spec {:swagger-docs path}) ui-options {:path ui})))
         (if spec (swagger-docs (assoc data :path spec))))))))
