# Reusable resources

Simple [compojure-api](https://github.com/metosin/compojure-api)-project using [`resources`](https://github.com/metosin/compojure-api/blob/master/src/compojure/api/resource.clj).

Demonstrates how to build reusable resource apis - both predefined & runtime-generated.

(not REST, just http-apis here).

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

Copyright © 2014-2016 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
