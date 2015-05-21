(ns compojure.api.upload
  (:require [potemkin :refer [import-vars]]
            [ring.middleware.multipart-params]
            [ring.swagger.json-schema :as js]
            [schema.core :as s]))

(import-vars
  [ring.middleware.multipart-params

   wrap-multipart-params])

; Works exactly like map schema but wrapped in record for json-type dispatch
(defrecord Upload [m]
  schema.core.Schema
  (walker [this]
    (let [sub-walker (s/subschema-walker m)]
      (clojure.core/fn [x]
       (if (schema.utils/error? x)
         x
         (sub-walker x)))))
  (explain [this] (cons 'file m)))

(def TempFileUpload
  "Schema for file param created by ring.middleware.multipart-params.temp-file store."
  (->Upload {:filename s/Str
             :content-type s/Str
             :size s/Int
             (s/optional-key :tempfile) java.io.File}))

(def ByteArrayUpload
  "Schema for file param created by ring.middleware.multipart-params.byte-array store."
  (->Upload {:filename s/Str
             :content-type s/Str
             :bytes s/Any}))

(defmethod js/json-type Upload [_] {:type "file"})
