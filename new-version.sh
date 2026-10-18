#!/bin/sh
# Dev helper: start a new version folder as a copy of another one.
# usage: ./new-version.sh <from-mc> <to-mc>   e.g. ./new-version.sh 1.20.4 1.20.1
set -e
cd "$(dirname "$0")/versions"
from=$1; to=$2
fp=mc$(echo "$from" | tr -d .); tp=mc$(echo "$to" | tr -d .)
[ -e "$to" ] && { echo "versions/$to exists"; exit 1; }
mkdir "$to"
cp "$from/build.gradle" "$from/gradle.properties" "$to/"
cp -r "$from/src" "$to/src"
mv "$to/src/main/java/com/litematicaturbo/$fp" "$to/src/main/java/com/litematicaturbo/$tp"
grep -rl "$fp" "$to/src" | xargs sed -i "s/com\.litematicaturbo\.$fp/com.litematicaturbo.$tp/g"
echo "created versions/$to (package $tp)"
