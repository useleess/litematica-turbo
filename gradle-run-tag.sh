# Sourced by audit.sh and ingame-test.sh (in the repo folder): sets JVMARGS, one Gradle argument that gives
# Gradle's build process this copy's folder in its command line. The test box's clean-up after ANOTHER run stops
# every toolchain Java started during that run whose command line names no run folder: an untagged Gradle
# process was killed mid-build ("Gradle build daemon disappeared unexpectedly"). Gradle hands -D options to its
# build process later, not on its command line, so the tag is its temp folder (java.io.tmpdir), inside this copy.
# Same JVM options as gradle.properties otherwise. A folder path with spaces (not the test box) gets no tag.
GRADLE_TMP="$(pwd -W 2>/dev/null || pwd)/.gradle-tmp"
JVMARGS="-Dlitematicaturbo.untagged=true"
case "$GRADLE_TMP" in
    *" "*) ;;
    *) mkdir -p "$GRADLE_TMP"
       JVMARGS="-Dorg.gradle.jvmargs=$(sed -n 's/^org.gradle.jvmargs *= *//p' gradle.properties) -Djava.io.tmpdir=$GRADLE_TMP" ;;
esac
