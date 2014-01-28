# compojure-api

A collection of helpers on top of Compojure for making clean and lean web apis.

## Usage

```
lein ring server
```

## TODO
- [x] initial swagger-json output
- [x] path-parameters
- [x] expand macros (read routes from compojure internals) => gentle-macroexpand
- [x] generate smart nicknames
- [x] syntax quote to get full paths to compojure stuff
- [x] meta-data driven endpoints (route and in & out parameters etc)
- [x] clean api ON TOP to support keyword-pair type inputs
- [x] easy adding of middlewares
- [x] multiple apis in single app
- [x] portable swagger-ui
- [x] separate ring-swagger endpoint generation with clean api
- [x] support returning lists
- [ ] smart destructuring of query parameters
- [ ] error messages
- [ ] consumes
- [ ] swagger-ui witn partially generated html
- [ ] travis
- [ ] cors-support?
- [ ] usage
- [ ] test 'em all
- [ ] license
- [x] repackage stuff (response, middleware etc.)
- [x] one joint entry package/import for easy usage
- [ ] resource-macro for RESTfull-crud stuff

## License

Copyright © 2014 Metosin Oy

Distributed under the Eclipse Public License, the same as Clojure.
