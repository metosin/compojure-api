(ns compojure.api.request)

(def coercion
  "Request-scoped coercion"
  ::coercion)

(def swagger
  "Vector of extra swagger data"
  ::swagger)

(def ring-swagger
  "Ring-swagger options"
  ::ring-swagger)

(def paths
  "Paths"
  ::paths)

(def lookup
  "Reverse routing tree"
  ::lookup)
