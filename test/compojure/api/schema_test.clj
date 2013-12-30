(ns compojure.api.schema-test
  (:require [midje.sweet :refer :all]
            [schema.core :as s]
            [schema.macros :as sm]
            [compojure.api.schema :refer :all]))

(def Tag {(s/optional-key :id)   (with-meta s/Int {:description "Unique identifier for the tag"})
          (s/optional-key :name) (with-meta sString {:description "Friendly name for the tag"})})
(def Tag' {:id "Tag"
           :properties {:id {:type "integer"
                             :format "int64"
                             :description "Unique identifier for the tag"}
                        :name {:type "string"
                               :description "Friendly name for the tag"}}})

(def Category  {:id (with-meta s/Int {:description "Category unique identifier"
                                      :minimum "0.0"
                                      :maximum "100.0"})
                :name (with-meta sString {:description "Name of the category"
                                          :minimum "0.0"
                                          :maximum "100.0"})})

(def Category' {:id "Category"
                :properties {:id {:type "integer"
                                  :format "int64"
                                  :description "Category unique identifier"
                                  :minimum "0.0"
                                  :maximum "100.0"}
                             :name {:type "string"
                                    :description "Name of the category"
                                    :minimum "0.0"
                                    :maximum "100.0"}}})

(fact "simple schema"
  (transform 'Tag) => Tag')

#_(fact "Swagger Petstore example"
  (transform 'Category) => Category')
