#!/bin/bash

set -ex

rm -r resources/clj-kondo.exports
mkdir -p resources/clj-kondo.exports/metosin/compojure-api/compojure/api

bb -f ./scripts/regen_kondo_config.clj
