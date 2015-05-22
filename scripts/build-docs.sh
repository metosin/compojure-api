#!/bin/bash

rev=$(git rev-parse HEAD)
remoteurl=$(git ls-remote --get-url origin)

git fetch
if [[ -z $(git branch -r --list origin/gh-pages) ]]; then
    (
    mkdir gh-pages
    cd gh-pages
    git init
    git remote add origin ${remoteurl}
    git checkout -b gh-pages
    git commit --allow-empty -m "Init"
    git push -u origin gh-pages
    )
elif [[ ! -d gh-pages ]]; then
    git clone --branch gh-pages ${remoteurl} gh-pages
else
    (
    cd gh-pages
    git pull
    )
fi

mkdir -p gh-pages/doc
lein doc
cd gh-pages
git add --all
git commit -m "Build docs from ${rev}."
git push origin gh-pages
