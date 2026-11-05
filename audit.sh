#!/bin/sh
# Dev check: mixin audit for the given versions. usage: ./audit.sh 1.20.1 1.21.4 ...
cd "$(dirname "$0")"
T="$(cd ../.. && pwd)/toolchain"
# at most 3 test runs on this PC at once (the shared toolchain/test-slot.sh; a 4th waits)
[ -f "$T/test-slot.sh" ] && . "$T/test-slot.sh" && test_slot_take
export GRADLE_USER_HOME="$T/gradle-home"
# tags Gradle's build process with this copy's folder (see gradle-run-tag.sh); its temp folder goes at the end
. ./gradle-run-tag.sh
. ./no-early-window.sh

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
    no_early_window "$v" "versions/$v/run"
    gradle_for "$v"
    # a build that dies without an AUDIT line (seen on the busy test box: a Gradle daemon or download that
    # breaks off) is tried again, up to 3 times; a real patch failure still ends with its AUDIT line
    for attempt in 1 2 3; do
        $GRADLE "$TASK" -Paudit "$MC_ARG" "$JVMARGS" --console=plain > "$log" 2>&1
        grep -qE 'AUDIT [0-9]+ mixins' "$log" && break
        echo "$v: attempt $attempt ended without an audit line:"
        grep -E -A3 "What went wrong|Exception|Error:|error:" "$log" | grep -v "Patch did not apply" | head -n 16 | sed 's/^/    | /'
        [ "$attempt" = 3 ] || sleep 10
    done
    echo "$v: $(grep -oE 'AUDIT [0-9]+ mixins, failures: [0-9]+' "$log" || echo 'NO RESULT (see '$log')')"
    grep -E "Patch did not apply" "$log" | sed 's/^.*Patch did not apply: /    /'
done
rm -rf "$GRADLE_TMP"
