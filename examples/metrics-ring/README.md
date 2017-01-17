# Simple compojure-api app

Demonstrates how to use [metrics-clojure](https://github.com/sjl/metrics-clojure) with compojure-api.

## Usage

### Run the application locally

`lein ring server`

### Packaging and running as standalone jar

```
lein do clean, ring uberjar
java -jar target/server.jar
```

### Packaging as war

`lein ring uberwar`

## License

Copyright © 2016-2017 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
