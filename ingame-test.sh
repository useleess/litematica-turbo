#!/bin/sh
# Litematica Turbo in-game tests: starts each version's dev game client in test mode (the test agent jar from
# selftest/, -Dlitematicaturbo.selftest=client), mirrors its SCOPE-TEST lines, then sums up. See TESTS.md.
#
#   ./ingame-test.sh [client] [versions...]     default: every folder in versions/. Turbo is client-only, so
#                                               there are no server tests: plain ./ingame-test.sh = client tests
#   FILTER=settings/,stats/f3 ./ingame-test.sh 1.21.4    only tests whose id starts with one of these
#   FILTER=knownbug/ ./ingame-test.sh                    only the known, not yet fixed mod bugs
#   ./ingame-test.sh client with=sodium,iris filter=sodium/ 1.21.4    the same game with Sodium and Iris added
#                                               (versions in the table in gradle/with-mods.gradle; with Iris a
#                                               shader pack is installed, the tests switch shaders on and off)
#   LANES=1 ./ingame-test.sh                     one game at a time (default 2: the Fabric versions and the
#                                               Forge / NeoForge versions side by side, two separate Gradle builds)
#
# GAME CLIENTS NEVER START ON THE USER'S PC: run this on the test box:
#   "../../discord-bot/.venv/Scripts/python.exe" ../../discord-bot/testbox.py run . ingame-test.sh client
#
# Every run gets its own throwaway game folder (versions/<v>/run-test, deleted after; muted, no pause on lost
# focus). Full game output: versions/<v>/selftest.log. Exit code 0 only when every version reached its summary
# and no test failed. Only paths relative to this script are used (toolchain = ../../toolchain).
cd "$(dirname "$0")" || exit 2
HERE="$(pwd)"
T="$(cd ../.. && pwd)/toolchain"
# at most 3 test runs on this PC at once (the shared toolchain/test-slot.sh; a 4th waits)
[ -f "$T/test-slot.sh" ] && . "$T/test-slot.sh" && test_slot_take
export GRADLE_USER_HOME="$T/gradle-home"
# tags Gradle's build process with this copy's folder (see gradle-run-tag.sh); its temp folder goes at the end
. ./gradle-run-tag.sh
JDK25="$T/jdk25/jdk-25.0.4.1+1"
JDK8="$(ls -d "$T"/jdk8/*/ | head -n 1)"
[ "$1" = client ] && shift
# filter=<prefix>[,<prefix>] among the arguments = FILTER (the test box runner passes arguments, not variables)
for arg in "$@"; do
    case "$arg" in filter=*) FILTER="${arg#filter=}" ;; with=*) WITH="${arg#with=}" ;; esac
done
VERSIONS=""
for arg in "$@"; do
    case "$arg" in filter=*|with=*) ;; *) VERSIONS="$VERSIONS $arg" ;; esac
done
VERSIONS="${VERSIONS# }"
[ -n "$VERSIONS" ] || VERSIONS="$(ls versions)"
LANES=${LANES:-2}

# the test agent: plain Java 8, everything of the game by reflection, so one jar runs on every version
WORK="$HERE/build-selftest"
rm -rf "$WORK"
mkdir -p "$WORK/classes" "$WORK/results"
if ! "${JDK8}bin/javac" -nowarn -encoding UTF-8 -d "$WORK/classes" selftest/src/com/litematicaturbo/selftest/*.java \
    || ! printf 'Premain-Class: com.litematicaturbo.selftest.Agent\n' > "$WORK/manifest.txt" \
    || ! "${JDK8}bin/jar" cfm "$WORK/litematica-turbo-selftest.jar" "$WORK/manifest.txt" -C "$WORK/classes" .; then
    echo "all: NO RESULT (the test agent in selftest/ does not compile)"
    echo "SCOPE TESTS ALL: 0 passed, 0 failed, 0 skipped"
    exit 1
fi
AGENT="$(cd "$WORK" && (pwd -W 2>/dev/null || pwd))/litematica-turbo-selftest.jar"
# with=...iris: the shader pack the tests switch on - Miniature Shader (MIT licence, by mateuskreuch), downloaded for the run only
SHADER_PACK=miniature-shader-2.19.zip
case ",$WITH," in
    *,iris,*) if ! curl -sfL -o "$WORK/$SHADER_PACK" "https://cdn.modrinth.com/data/UaS8ROxa/versions/LWmZ94RG/$SHADER_PACK"; then
                  echo "all: NO RESULT (the test shader pack $SHADER_PACK could not be downloaded from Modrinth)"
                  echo "SCOPE TESTS ALL: 0 passed, 0 failed, 0 skipped"
                  exit 1
              fi ;;
esac

# one version: build + start its client in test mode, mirror the protocol, keep the summary line
run_version()
{
    v=$1
    dir="versions/$v"
    case "$v" in
        *-forge|*-neoforge) gradle="forge/gradlew -p forge"; task=":$v:runClient" ;;
        *) gradle="./gradlew"; task=":versions:$v:runClient" ;;
    esac
    if [ ! -f "$dir/build.gradle" ]; then
        echo "$v: UNKNOWN VERSION (no $dir)" > "$WORK/results/$v"
        return
    fi
    if [ -n "$WITH" ] && ! grep -q "^ *'$v' *:" gradle/with-mods.gradle; then
        echo "$v: 0 passed, 0 failed, 0 skipped (no Sodium / Iris for $v: gradle/with-mods.gradle)" > "$WORK/results/$v"
        return
    fi
    log="$HERE/$dir/selftest.log"
    run="$dir/run-test"
    start=$(date +%s)
    filter=""
    [ -n "$FILTER" ] && filter="-PselftestFilter=$FILTER"
    with=""
    [ -n "$WITH" ] && with="-Pwith=$WITH"
    # a build that dies before the game reports anything (seen on the busy test box: a Gradle daemon or a
    # download that breaks off) is tried once more; a run whose tests started is never repeated
    for attempt in 1 2; do
        rm -rf "$run"
        mkdir -p "$run"
        printf 'soundCategory_master:0.0\npauseOnLostFocus:false\nonboardAccessibility:false\ntutorialStep:none\njoinedFirstServer:true\nrenderDistance:12\n' > "$run/options.txt"
        # with Iris: the test shader pack is installed and chosen, shaders start switched off (the tests turn them on)
        case ",$WITH," in
            *,iris,*) mkdir -p "$run/config" "$run/shaderpacks"; cp "$WORK/$SHADER_PACK" "$run/shaderpacks/"
                      printf 'shaderPack=%s\nenableShaders=false\ndisableUpdateMessage=true\n' "$SHADER_PACK" > "$run/config/iris.properties" ;;
        esac
        # (Neo)Forge's mod-warning screen would block opening the test world
        case "$v" in
            *-forge|*-neoforge) mkdir -p "$run/config"; printf 'showLoadWarnings = false\n' | tee "$run/config/forge-client.toml" > "$run/config/neoforge-client.toml" ;;
        esac
        # NeoForge's dev runs check every GPU call (Blaze3D validation layer, 1.21.5 on); Iris's draws trip it at start-up,
        # a check the released game never makes
        case "$v" in
            *-neoforge) printf 'enableB3DValidationLayer = false\n' >> "$run/config/neoforge-client.toml" ;;
        esac
        : > "$log"
        echo "== $v: building and starting the game client (attempt $attempt, log: $dir/selftest.log)"
        # shellcheck disable=SC2086
        (JAVA_HOME="$JDK25" timeout 2700 $gradle "$task" -Pmc="$v" -PselftestAgent="$AGENT" -PtestRunDir=run-test $filter $with "$JVMARGS" \
            --console=plain > "$log" 2>&1) &
        pid=$!
        tail -n +1 -f "$log" --pid=$pid 2>/dev/null | sed -u -n 's/.*\(SCOPE-TEST .*\)/\1/p; s/.*\(SCOPE TESTS: .*\)/\1/p; s/.*\(SELFTEST .*\)/\1/p'
        wait $pid
        grep -a -q "SCOPE-TEST " "$log" && break
        echo "== $v: attempt $attempt ended before the tests started:"
        grep -a -E -A3 "What went wrong|Exception|Error:|error:" "$log" | head -n 12 | sed 's/^/    | /'
    done
    # a crashed game: its crash report (the throwaway copy and the report go away with it)
    for c in "$run"/crash-reports/*.txt; do
        [ -f "$c" ] || continue
        echo "    crash report $(basename "$c"):"
        grep -a -v -E "^\s*$" "$c" | head -n 70 | cut -c 1-240 | sed 's/^/    # /'
    done
    # the tests' screenshots outlive the throwaway game folder (the test box brings test-shots back)
    for png in "$run"/screenshots/*.png; do
        [ -f "$png" ] || continue
        mkdir -p "$HERE/test-shots"
        cp "$png" "$HERE/test-shots/$v-$(basename "$png")"
    done
    rm -rf "$run"
    secs=$(( $(date +%s) - start ))
    summary=$(sed -n 's/.*SCOPE TESTS: \([0-9]*\) passed, \([0-9]*\) failed, \([0-9]*\) skipped.*/\1 \2 \3/p' "$log" | tail -n 1)
    if [ -z "$summary" ]; then
        echo "$v: NO RESULT after ${secs} s (build or game failed: see $dir/selftest.log)" > "$WORK/results/$v"
        # what the game was doing when it ended (the log goes with the throwaway copy on the test box)
        # the game's own last lines (not Gradle's failure text) and every error / crash line
        sed '/^FAILURE: Build failed/,$d' "$log" | grep -a -v "SCOPE-TEST pass" | tail -n 30 | cut -c 1-240 | sed 's/^/    | /'
        echo "    errors:"
        grep -a -E "Exception|Error|Crash|crash|hs_err|insufficient memory|Killed|exit code|halt" "$log" | grep -a -v "SCOPE-TEST" \
            | tail -n 20 | cut -c 1-240 | sed 's/^/    ! /'
    else
        set -- $summary
        echo "$v: $1 passed, $2 failed, $3 skipped (${secs} s)" > "$WORK/results/$v"
    fi
}

lane()
{
    for v in "$@"; do run_version "$v"; done
}

if [ "$LANES" -ge 2 ]; then
    # the Fabric and the Forge / NeoForge versions are separate Gradle builds: they never share build output
    fabric=""; forge=""
    for v in $VERSIONS; do
        case "$v" in *-forge|*-neoforge) forge="$forge $v" ;; *) fabric="$fabric $v" ;; esac
    done
    # shellcheck disable=SC2086
    lane $fabric &
    p1=$!
    # shellcheck disable=SC2086
    lane $forge &
    p2=$!
    wait $p1 $p2
else
    # shellcheck disable=SC2086
    lane $VERSIONS
fi

TOTAL_P=0; TOTAL_F=0; TOTAL_S=0; BAD=0
for v in $VERSIONS; do
    line=$(cat "$WORK/results/$v" 2>/dev/null || echo "$v: NO RESULT (never ran)")
    echo "$line"
    counts=$(echo "$line" | sed -n 's/^[^:]*: \([0-9]*\) passed, \([0-9]*\) failed, \([0-9]*\) skipped.*/\1 \2 \3/p')
    if [ -z "$counts" ]; then BAD=1; continue; fi
    set -- $counts
    TOTAL_P=$((TOTAL_P + $1)); TOTAL_F=$((TOTAL_F + $2)); TOTAL_S=$((TOTAL_S + $3))
    [ "$2" -gt 0 ] && BAD=1
done
rm -rf "$WORK" "$GRADLE_TMP"
echo "SCOPE TESTS ALL: $TOTAL_P passed, $TOTAL_F failed, $TOTAL_S skipped"
exit $BAD
