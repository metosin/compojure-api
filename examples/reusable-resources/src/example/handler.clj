(ns example.handler
  (:require [compojure.api.sweet :refer [api context routes GET]]
            [ring.util.http-response :refer :all]
            [clojure.string :as str]
            [example.entity :as entity]
            [example.domain :as domain]))

(def app
  (let [db (atom {})
        entities (domain/entities)
        entity-resource (partial entity/resource db)
        entity-resource-routes (->> entities vals (map entity-resource) (apply routes))
        entity-tags (->> entities keys (map (fn [name] {:name name, :description (str "api to manage " name "s")})))]

    (api
      {:swagger
       {:ui "/"
        :spec "/swagger.json"
        :data {:info {:title "Reusable resources"
                      :description (str "Example app using `compojure.api.resource`.<br> "
                                        "The `*runtime*`-routes are generated at runtime, "
                                        "based on the path. <br>Despite the swagger-ui only "
                                        "shows `sausage` as runtime entity api, <br> apis exist for all "
                                        "defined entities: `pizza`, `kebab`, `sausage` and `beer`.<br>"
                                        "try `/runtime/pizza/`, `/runtime/kebab/` etc.")
                      :contact {:url "https://github.com/metosin/compojure-api/"}}
               :tags entity-tags}}}

      entity-resource-routes

      (context "/runtime" request
        (if-let [entity (or (some-> request :path-info (str/replace #"/" "")) "sausage")]
          (entity-resource (entities entity) "*runtime*"))))))
