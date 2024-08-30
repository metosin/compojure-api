#!/bin/bash

set -ex

rm -r resources/clj-kondo.exports
mkdir -p resources/clj-kondo.exports/metosin/compojure-api/compojure/api

bb -f ./scripts/regen_kondo_config.clj

# rename to .clj
cp src/compojure/api/common.cljc resources/clj-kondo.exports/metosin/compojure-api/compojure/api/common.clj
cp src/compojure/api/core.cljc resources/clj-kondo.exports/metosin/compojure-api/compojure/api/core.clj
