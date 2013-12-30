(ns compojure.api.schema-test
  (:require [midje.sweet :refer :all]
            [schema.core :as s]
            [schema.macros :as sm]
            [compojure.api.schema :refer :all]))

(defmodel Tag {(optional :id)   (field s/Int {:description "Unique identifier for the tag"})
               (optional :name) (field s/String {:description "Friendly name for the tag"})})

(def Tag' {:id "Tag"
           :properties {:id {:type "integer"
                             :format "int64"
                             :description "Unique identifier for the tag"}
                        :name {:type "string"
                               :description "Friendly name for the tag"}}})

(defmodel Category  {(optional :id)   (field s/Int {:description "Category unique identifier" :minimum "0.0" :maximum "100.0"})
                     (optional :name) (field s/String {:description "Name of the category"})})

(def Category' {:id "Category"
                :properties {:id {:type "integer"
                                  :format "int64"
                                  :description "Category unique identifier"
                                  :minimum "0.0"
                                  :maximum "100.0"}
                             :name {:type "string"
                                    :description "Name of the category"}}})

(defmodel Pet  {:id                   (field s/Int {:description "Unique identifier for the Pet" :minimum "0.0" :maximum "100.0"})
                :name                 (field s/String {:description "Friendly name of the pet"})
                (optional :category)  (field Category {:description "Category the pet is in"})
                (optional :photoUrls) (field [s/String] {:description "Image URLs"})
                (optional :tags)      (field [Tag] {:description "Tags assigned to this pet"})
                (optional :status)    (field (s/enum :available :pending :sold) {:description "pet status in the store"})})

(def Pet' {:id "Pet"
           :required [:id :name]
           :properties {:id {:type "integer"
                             :format "int64"
                             :description "Unique identifier for the Pet"
                             :minimum "0.0"
                             :maximum "100.0"}
                        :category {:$ref "Category"
                                   :description "Category the pet is in"}
                        :name {:type "string"
                               :description "Friendly name of the pet"}
                        :photoUrls {:type "array"
                                    :description "Image URLs"
                                    :items {:type "string"}}
                        :tags {:type "array"
                               :description "Tags assigned to this pet"
                               :items {:$ref "Tag"}}
                        :status {:type "string"
                                 :description "pet status in the store"
                                 :enum [:pending :sold :available]}}})

(facts "simple schemas"
  (transform 'Tag) => Tag'
  (transform 'Category) => Category'
  (transform 'Pet) => Pet')

(fact "Swagger Petstore example"
  (transform-models 'Pet) => {:Pet Pet'
                              :Tag Tag'
                              :Category Category'})
