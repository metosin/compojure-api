(ns compojure.api.upload
  (:require [potemkin :refer [import-vars]]
            ring.middleware.multipart-params
            ring.swagger.upload))

(import-vars
  [ring.middleware.multipart-params

   wrap-multipart-params]

  [ring.swagger.upload

   TempFileUpload
   ByteArrayUpload])
