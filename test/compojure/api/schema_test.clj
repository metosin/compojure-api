(ns compojure.api.schema-test
  (:require [midje.sweet :refer :all]
            [schema.core :as s]
            [schema.macros :as sm]
            [compojure.api.schema :refer :all]))

(def Tag {:id   (with-meta s/Int {:description "Unique identifier for the tag"})
          :name (with-meta sString {:description "Friendly name for the tag"})})

(def Tag' {:id "Tag"
           :required [:id :name]
           :properties {:id {:type "integer"
                             :format "int64"
                             :description "Unique identifier for the tag"}
                        :name {:type "string"
                               :description "Friendly name for the tag"}}})

(fact "simple schema"
  (transform 'Tag) => Tag')
