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
- [ ] portable swagger-ui
- [x] separate ring-swagger endpoint generation with clean api
- [ ] cors-support?
- [ ] usage
- [ ] test 'em all
- [ ] license
- [x] repackage stuff (response, middleware etc.)
- [ ] one joint entry package/import for easy usage
- [ ] resource-macro for RESTfull-crud stuff

## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
