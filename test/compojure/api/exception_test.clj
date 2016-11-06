(ns compojure.api.exception-test
  (:require [compojure.api.exception :refer :all]
            [midje.sweet :refer :all]
            [schema.core :as s])
  (:import (schema.utils ValidationError NamedError)))

(fact "stringify-error"
  (fact "ValidationError"
    (class (s/check s/Int "foo")) => ValidationError
    (stringify-error (s/check s/Int "foo")) => "(not (integer? \"foo\"))"
    (stringify-error (s/check {:foo s/Int} {:foo "foo"})) => {:foo "(not (integer? \"foo\"))"})
  (fact "NamedError"
    (class (s/check (s/named s/Int "name") "foo")) => NamedError
    (stringify-error (s/check (s/named s/Int "name") "foo")) => "(named (not (integer? \"foo\")) \"name\")"))
