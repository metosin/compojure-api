(ns user
  (:require [reloaded.repl :refer [system init start stop go reset]]))

(reloaded.repl/set-init! #(do (require 'examples.server) ((resolve 'examples.server/new-system))))
