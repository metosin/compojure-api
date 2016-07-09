(ns example.entity
  (:require [compojure.api.sweet :as sweet]
            [ring.util.http-response :refer :all]
            [ring.util.http-status :as http-status]
            [clojure.string :as str]
            [schema.core :as s]))

;;
;; Database
;;

(defn update! [db name maybe-id maybe-entity]
  (let [id (or maybe-id (::ids (swap! db update ::ids (fnil inc 0))))]
    (if maybe-entity
      (swap! db update name assoc id (assoc maybe-entity :id id))
      (swap! db update name dissoc id))
    (get-in @db [name id])))

;;
;; Routes
;;

(defn resource
  ([db Schema]
    (resource db Schema nil))
  ([db Schema tag]
   (let [entity (str/lower-case (s/schema-name Schema))
         tag (or tag entity)
         update! (partial update! db entity)
         qualified-name (keyword (-> Schema meta :ns str) (-> Schema meta :name (str tag)))
         NewSchema (s/schema-with-name (dissoc Schema :id) (str "New" (s/schema-name Schema)))
         UpdatedSchema (s/schema-with-name NewSchema (str "Updated" (s/schema-name Schema)))]

     (sweet/routes
       (sweet/context (format "/%s/" entity) []
         (sweet/resource
           {:tags [tag]
            :get {:summary (format "get %ss" entity)
                  :description (format "get all %ss!" entity)
                  :responses {http-status/ok {:schema [Schema]}}
                  :handler (fn [_] (ok (-> @db (get entity) vals)))}
            :post {:summary "add's a pizza"
                   :parameters {:body-params NewSchema}
                   :responses {http-status/created {:schema Schema
                                                    :description (format "the created %s" entity)
                                                    :headers {"Location" s/Str}}}
                   :handler (fn [{body :body-params}]
                              (let [{:keys [id] :as entity} (update! nil body)]
                                (created (sweet/path-for qualified-name {:id id}) entity)))}}))

       (sweet/context (format "/%s/:id" entity) []
         :path-params [id :- s/Int]

         (sweet/resource
           {:tags [tag]
            :get {:x-name qualified-name
                  :summary (format "gets a %s" entity)
                  :responses {http-status/ok {:schema Schema}}
                  :handler (fn [_]
                             (if-let [entity (get-in @db [entity id])]
                               (ok entity)
                               (not-found)))}
            :put {:summary (str "updates a %s" entity)
                  :parameters {:body-params UpdatedSchema}
                  :responses {http-status/ok {:schema Schema}}
                  :handler (fn [{body :body-params}]
                             (if (get-in @db [entity id])
                               (ok (update! id body))
                               (not-found)))}
            :delete {:summary (str "deletes a %s" entity)
                     :handler (fn [_]
                                (update! id nil)
                                (no-content))}}))))))
