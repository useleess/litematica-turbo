#!/bin/sh
# Dev check: A/B benchmark (stock Litematica, then Turbo) for the given versions.
# Needs versions/<mc>/run/saves/bench (make-bench-world.sh). usage: ./bench.sh 1.20.1 1.21.4 ...
cd "$(dirname "$0")"
T="$(cd ../.. && pwd)/toolchain"
export GRADLE_USER_HOME="$T/gradle-home"

# Forge / NeoForge targets (<mc>-forge, <mc>-neoforge) are built by forge/ (Architectury Loom)
gradle_for()
{
    case "$1" in
        *-forge|*-neoforge) export JAVA_HOME="$T/jdk25/jdk-25.0.4.1+1"; GRADLE="forge/gradlew -p forge"; TASK=":$1:runClient"; MC_ARG="-Pmc=$1" ;;
        *) export JAVA_HOME="$T/jdk25/jdk-25.0.4.1+1"; GRADLE="./gradlew"; TASK=":versions:$1:runClient"; MC_ARG="-Pmc=$1" ;;
    esac
}

# Test launches are muted: master volume 0 in the run's options.txt
mute()
{
    o="versions/$1/run/options.txt"
    mkdir -p "versions/$1/run"
    if grep -q '^soundCategory_master:' "$o" 2>/dev/null; then
        sed -i 's/^soundCategory_master:.*/soundCategory_master:0.0/' "$o"
    else
        echo 'soundCategory_master:0.0' >> "$o"
    fi
    # (Neo)Forge load-warning screen (other mods' metadata warnings) would block the automatic world load
    sed -i 's/^\(\s*\)showLoadWarnings = true/\1showLoadWarnings = false/' "versions/$1"/run/config/*forge-client.toml 2>/dev/null
}
ref=versions/1.20.4/run
for v in "$@"; do
    run="versions/$v/run"
    mkdir -p "$run/config"
    # Forge / NeoForge targets reuse the bench world of the matching Fabric version
    base=${v%-forge}; base=${base%-neoforge}; [ "$base" = 1.21.1 ] && base=1.21
    if [ ! -d "$run/saves/bench" ] && [ -d "versions/$base/run/saves/bench" ]; then
        mkdir -p "$run/saves"; cp -r "versions/$base/run/saves/bench" "$run/saves/bench"
    fi
    [ -f "$run/options.txt" ] || cp "$ref/options.txt" "$run/"
    [ -f "$run/config/litematica.json" ] || cp "$ref/config/litematica.json" "$run/config/"
    # Litematica before 1.18 reads schematic format 5 at most
    # Forgematica 1.17.1 crashes (also without Turbo) placing the Storage region's hoppers/comparators
    case "$v" in 1.17.1-forge) schem=main_storage_v5_nostorage.litematic ;; 1.16*|1.17*) schem=main_storage_v5.litematic ;; *) schem=main_storage.litematic ;; esac
    [ -f "$run/$schem" ] || cp "$ref/$schem" "$run/"
    touch "$run/turbo-bench.txt"
    start=$(wc -l < "$run/turbo-bench.txt")
    mute "$v"
    gradle_for "$v"
    for mode in stock turbo; do
        flag=""; [ $mode = stock ] && flag="-Pstock"
        $GRADLE "$TASK" "$MC_ARG" "-PbenchSchematic=$schem" $flag --console=plain > "versions/$v/bench-$mode.log" 2>&1
        grep -qE "Patch did not apply|Exception in|crashed" "versions/$v/bench-$mode.log" && echo "$v $mode: problems, see versions/$v/bench-$mode.log"
    done
    echo "== $v"
    tail -n +$((start + 1)) "$run/turbo-bench.txt" | grep -v "placement calls"
done
