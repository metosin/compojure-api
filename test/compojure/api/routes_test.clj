(ns compojure.api.routes-test
  (:require [compojure.api.routes :refer :all]
            [midje.sweet :refer :all])
  (:import [java.security SecureRandom]
           [org.joda.time LocalDate]
           [com.fasterxml.jackson.core JsonGenerationException]))

(facts "->path"

  (fact "missing path parameter"
    (#'path-string "/api/:kikka" {})
    => (throws IllegalArgumentException))

  (fact "missing serialization"
    (#'path-string "/api/:kikka" {:kikka (SecureRandom.)})
    => (throws JsonGenerationException))

  (fact "happy path"
    (#'path-string "/a/:b/:c/d/:e/f" {:b (LocalDate/parse "2015-05-22")
                                 :c 12345
                                 :e :kikka})
    => "/a/2015-05-22/12345/d/kikka/f"))
