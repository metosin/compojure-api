#!/bin/bash

set -ex

# bb -f ./script/regen_kondo_config.clj

rm -r resources/clj-kondo.exports
mkdir -p resources/clj-kondo.exports/metosin/compojure-api/compojure/api


# rename to .clj
cp src/compojure/api/common.cljc resources/clj-kondo.exports/metosin/compojure-api/compojure/api/common.clj
cp src/compojure/api/core.cljc resources/clj-kondo.exports/metosin/compojure-api/compojure/api/core.clj
