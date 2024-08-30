#!/bin/bash

set -ex

rm -r resources/clj-kondo.exports
mkdir -p resources/clj-kondo.exports/metosin/compojure-api/compojure/api
mkdir -p resources/clj-kondo.exports/metosin/compojure-api/compojure_api_kondo_hooks/compojure

bb -f ./scripts/regen_kondo_config.clj
