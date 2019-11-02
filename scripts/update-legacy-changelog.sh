#!/bin/bash

set -euo pipefail
git fetch origin
git show origin/1.1.x:CHANGELOG.md > CHANGELOG-1.1.x.md
