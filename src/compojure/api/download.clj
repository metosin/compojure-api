(ns compojure.api.download
  (:require [potemkin :refer [import-vars]]
            ring.swagger.download))

(import-vars
  [ring.swagger.download

   FileResponse])
