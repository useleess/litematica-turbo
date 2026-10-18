#!/bin/sh
# Dev helper: creates versions/<mc>/run/saves/bench (creative superflat) with that version's vanilla
# server (jar from Loom's cache, so build the version once first).
# usage: ./make-bench-world.sh <mc> <jdk feature version, e.g. 17>
set -e
cd "$(dirname "$0")"
mc=$1; jdk=$2
T="$(cd ../.. && pwd)/toolchain"
java="$(ls -d "$T/jdk$jdk"/*/ | head -1)bin/java"
jar="$T/gradle-home/caches/fabric-loom/$mc/minecraft-server.jar"
[ -f "$jar" ] || { echo "no server jar at $jar (build versions/$mc first)"; exit 1; }
work="versions/$mc/run/worldgen"
rm -rf "$work"; mkdir -p "$work"
cp "$jar" "$work/server.jar"
cd "$work"
echo "eula=true" > eula.txt
case "$mc" in 1.16*|1.17*|1.18*) flat=flat ;; *) flat='minecraft\:flat' ;; esac
cat > server.properties <<P
level-name=bench
level-type=$flat
gamemode=creative
generate-structures=false
online-mode=false
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
server-port=25599
P
( while ! grep -q 'Done (' logs/latest.log 2>/dev/null; do sleep 1; done; echo stop ) | "$java" -Xmx2G -jar server.jar nogui > server.out 2>&1
cd ..
rm -rf saves/bench; mkdir -p saves
mv worldgen/bench saves/bench
rm -rf worldgen
echo "bench world ready: versions/$mc/run/saves/bench"
