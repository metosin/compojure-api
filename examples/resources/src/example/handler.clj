(ns example.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ring.util.http-status :as http-status]
            [schema.core :as s]))

;;
;; Schemas
;;

(s/defschema Pizza
  {:id s/Int
   :name s/Str
   (s/optional-key :description) s/Str
   :size (s/enum :L :M :S)
   :origin {:country (s/enum :FI :PO)
            :city s/Str}})

(s/defschema NewPizza (dissoc Pizza :id))
(s/defschema UpdatedPizza NewPizza)

;;
;; Database
;;

(def pizzas (atom {}))

(let [ids (atom 0)]
  (defn update-pizza! [maybe-id maybe-pizza]
    (let [id (or maybe-id (swap! ids inc))]
      (if maybe-pizza
        (swap! pizzas assoc id (assoc maybe-pizza :id id))
        (swap! pizzas dissoc id))
      (@pizzas id))))

;;
;; Application
;;

(def app
  (api
    {:swagger
     {:ui "/"
      :spec "/swagger.json"
      :data {:info {:title "Resource sample"
                    :description "Example app using `compojure.api.resource`."
                    :contact {:url "https://github.com/metosin/compojure-api/examples/resources"}}
             :tags [{:name "pizza", :description "pizzas"}]}}}

    (context "/pizza/" []
      (resource
        {:tags ["pizza"]
         :get {:summary "get pizzas"
               :description "get all pizzas!"
               :responses {http-status/ok {:schema [Pizza]}}
               :handler (fn [_] (ok (vals @pizzas)))}
         :post {:summary "add's a pizza"
                :parameters {:body-params NewPizza}
                :responses {http-status/created {:schema Pizza
                                                 :description "the created pizza"
                                                 :headers {"Location" s/Str}}}
                :handler (fn [{body :body-params}]
                           (let [{:keys [id] :as pizza} (update-pizza! nil body)]
                             (created (path-for ::pizza {:id id}) pizza)))}}))

    (context "/pizza/:id" []
      :path-params [id :- s/Int]

      (resource
        {:tags ["pizza"]
         :get {:x-name ::pizza
               :summary "gets a pizza"
               :responses {http-status/ok {:schema Pizza}}
               :handler (fn [_]
                          (if-let [pizza (@pizzas id)]
                            (ok pizza)
                            (not-found)))}
         :put {:summary "updates a pizza"
               :parameters {:body-params UpdatedPizza}
               :responses {http-status/ok {:schema Pizza}}
               :handler (fn [{body :body-params}]
                          (if (@pizzas id)
                            (ok (update-pizza! id body))
                            (not-found)))}
         :delete {:summary "deletes a pizza"
                  :handler (fn [_]
                             (update-pizza! id nil)
                             (no-content))}}))))
