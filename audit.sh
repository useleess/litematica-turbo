#!/bin/sh
# Dev check: mixin audit for the given versions. usage: ./audit.sh 1.20.1 1.21.4 ...
cd "$(dirname "$0")"
T="$(cd ../.. && pwd)/toolchain"
# at most 3 test runs on this PC at once (the shared toolchain/test-slot.sh; a 4th waits)
[ -f "$T/test-slot.sh" ] && . "$T/test-slot.sh" && test_slot_take
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
for v in "$@"; do
    log="versions/$v/audit.log"
    mute "$v"
    gradle_for "$v"
    $GRADLE "$TASK" -Paudit "$MC_ARG" --console=plain > "$log" 2>&1
    echo "$v: $(grep -oE 'AUDIT [0-9]+ mixins, failures: [0-9]+' "$log" || echo 'NO RESULT (see '$log')')"
    grep -E "Patch did not apply" "$log" | sed 's/^.*Patch did not apply: /    /'
done
