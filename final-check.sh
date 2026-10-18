#!/bin/sh
# Dev check: audit + A/B benchmark for every Fabric version; summary in final-check.txt
cd "$(dirname "$0")"
out=final-check.txt
: > $out
for v in 1.16.5 1.17.1 1.18.2 1.19.4 1.20.1 1.20.4 1.21 1.21.4 1.21.10 1.21.11 26.2 26.3; do
    ./audit.sh $v >> $out 2>&1
    ./bench.sh $v >> $out 2>&1
    echo "done $v" >> $out
done
echo ALL DONE >> $out
