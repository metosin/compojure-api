(ns compojure.api.validator
  (:require [compojure.api.swagger :as swagger]
            [compojure.api.impl.json :as json]
            [ring.swagger.validator :as rsv]
            [muuntaja.core :as m]
            [compojure.api.middleware :as mw]))

(defn validate
  "Validates a api. If the api is Swagger-enabled, the swagger-spec
  is requested and validated against the JSON Schema. Returns either
  the (valid) api or throws an exception. Requires lazily the
  ring.swagger.validator -namespace allowing it to be excluded, #227"
  [api]
  (when-let [uri (swagger/swagger-spec-path api)]
    (let [{status :status :as response} (api {:request-method :get
                                              :uri uri
                                              ::mw/rethrow-exceptions? true})
          body (->> response :body slurp (m/decode json/instance "application/json"))]

      (when-not (= status 200)
        (throw (ex-info (str "Coudn't read swagger spec from " uri)
                        {:status status
                         :body body})))

      (when-let [errors (seq (rsv/validate body))]
        (throw (ex-info (str "Invalid swagger spec from " uri)
                        {:errors errors
                         :body body})))))
  api)
