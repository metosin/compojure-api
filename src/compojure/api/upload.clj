(ns compojure.api.upload
  (:require [potemkin :refer [import-vars]]
            [ring.middleware.multipart-params]
            [ring.swagger.json-schema :as js]
            [schema.core :as s]))

(import-vars
  [ring.middleware.multipart-params

   wrap-multipart-params])

; Works exactly like map schema but wrapped in record for json-type dispatch
(defrecord File [m]
  schema.core.Schema
  (walker [this]
    (let [sub-walker (s/subschema-walker m)]
      (clojure.core/fn [x]
       (if (schema.utils/error? x)
         x
         (sub-walker x)))))
  (explain [this] (cons 'file m)))

(def temp-file
  (File. {:filename s/Str
          :content-type s/Str
          :size s/Int
          (s/optional-key :tempfile) java.io.File}))

(def byte-array
  (File. {:filename s/Str
          :content-type s/Str
          :bytes s/Any}))

(defmethod js/json-type File [_] {:type "file"})
