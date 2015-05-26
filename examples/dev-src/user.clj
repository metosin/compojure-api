(ns user
  (:require [reloaded.repl :refer [system init start stop go reset]]
            [examples.server :refer [new-system]]))

(reloaded.repl/set-init! #(new-system))
