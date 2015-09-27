(ns user
  (:require [reloaded.repl :refer [set-init! system init start stop go reset]]))

(set-init! #(do (require 'examples.server) ((resolve 'examples.server/new-system))))
