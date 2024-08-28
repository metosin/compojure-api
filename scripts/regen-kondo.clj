#!/bin/bash

set -e

bb -f ./script/regen_kondo_config.clj

mkdir -p resources/clj-kondo.exports/metosin/compojure-api/compojure/api

;; rename to .clj
cp src/compojure/api/common.cljc resources/clj-kondo.exports/metosin/compojure-api/compojure/api/common.clj
