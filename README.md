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
- [ ] multiple apis in single app
- [ ] portable swagger-ui
- [x] meta-data driven endpoints (route and in & out parameters etc)
- [x] clean api ON TOP to support keyword-pair type inputs
- [x] easy adding of middlewares
- [ ] cors-support?
- [ ] separate swagger-stuff into separate project
- [ ] usage
- [ ] test 'em all

## License

Copyright © 2013 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
