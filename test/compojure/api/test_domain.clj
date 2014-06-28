(ns compojure.api.test-domain
  (:require [schema.core :as s]))

(s/defschema Entity {(s/optional-key :data) String})
