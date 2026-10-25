# Litematica Turbo in-game tests

Scripted tests that run inside a real game client of every version in `versions/` (Fabric, Forge via
Forgematica, NeoForge), against the mod's real code and the real Litematica. Unit tests of the
version-independent core stay in `core/src/test` (`./gradlew build`). `audit.sh` (the plain mixin audit) is
still run by the release as its own check; the `audit/` tests below do the same check in more detail.

Turbo is client-only, so there are no server tests: plain `./ingame-test.sh` and `./ingame-test.sh client`
both run the client tests.

## Running

Game clients never start on the user's PC (they take the mouse). Run on the test box:

```sh
"../../discord-bot/.venv/Scripts/python.exe" ../../discord-bot/testbox.py run . ingame-test.sh client
"../../discord-bot/.venv/Scripts/python.exe" ../../discord-bot/testbox.py run . ingame-test.sh client 1.20.1 26.2-neoforge
```

On the box itself (or wherever a game window may open):

```sh
./ingame-test.sh                              # every folder in versions/
./ingame-test.sh client 1.21.4 1.20.1-forge   # some versions
FILTER=settings/,stats/f3 ./ingame-test.sh    # only tests whose id starts with one of these (comma separated)
FILTER=knownbug/ ./ingame-test.sh             # only the known, not yet fixed mod bugs (left out of every other run)
LANES=1 ./ingame-test.sh                      # one game at a time
```

By default two games run side by side: the Fabric versions (root Gradle build) and the Forge / NeoForge
versions (`forge/` Gradle build) in two lanes, so two builds never share build output. The script takes one
slot of the PC-wide limit (`../../toolchain/test-slot.sh`, 3 runs per PC). A full run takes about 15-20 minutes.

Per version it builds and starts the dev client (`runClient`) with:
- `-PselftestAgent=<jar>`: the test agent (built from `selftest/src` by the script with JDK 8, into
  `build-selftest/`, deleted after). Gradle adds `-javaagent:<jar>`, `-Dlitematicaturbo.selftest=client` and
  `-Dlitematicaturbo.selftest.filter=...` (wiring in `gradle/version.gradle` and `forge/version.gradle`).
  Without `-PselftestAgent` nothing changes: no test code is in the mod or in any release jar.
- `-PtestRunDir=run-test`: a throwaway game folder `versions/<v>/run-test` (options.txt muted, no pause on lost
  focus, no first-start screens), deleted after the run.
- `gradle-run-tag.sh`: gives Gradle's build process a temp folder inside this copy, so the test box's clean-up
  after another run recognises it as this run's (an untagged Gradle process got killed mid-build).

A build that dies before the game reports anything is tried once more (seen on the busy test box). Full game
output: `versions/<v>/selftest.log`. The script mirrors the protocol live and ends with one line per version:

```
1.20.1: 55 passed, 0 failed, 0 skipped (59 s)
1.21.4-neoforge: NO RESULT after 80 s (build or game failed: see versions/1.21.4-neoforge/selftest.log)
SCOPE TESTS ALL: 1210 passed, 0 failed, 0 skipped
```

Exit code 0 only when every version reached its summary and no test failed.

## Output protocol

Printed by the game to its real stdout:

```
SCOPE-TEST total <N>                     first: how many tests will report
SCOPE-TEST pass <id>                     one of these three per test
SCOPE-TEST fail <id> :: <reason>
SCOPE-TEST skip <id> :: <reason>
SCOPE TESTS: <p> passed, <f> failed, <s> skipped     last
```

`SELFTEST harness ...` lines mean the tests never started (the game did not finish loading in 10 minutes, or
the agent crashed); that version then shows `NO RESULT`.

## Layout

`selftest/src/com/litematicaturbo/selftest/` (plain Java 8, no compile-time dependency on Minecraft):

- `Agent` - the `-javaagent` entry. Waits (in its own thread) until Turbo has started and the client shows its
  first screen, runs the tests, prints the protocol, quits the game.
- `Ctx` - the running game: Turbo's version package (`com.litematicaturbo.mcXXXX`, found among the loaded
  classes), the class loader that sees Turbo / Litematica / malilib / Minecraft, the client, `onMain` (run on
  the render thread and wait), `ticks(n)`, reflection helpers. Everything is reached by name through that
  loader, so one compiled jar runs on every version and loader (Yarn and Mojang names alike).
- `SelfTest` - the runner: registration, filter, known bugs, protocol.
- `AuditTests` (`audit/`), `SettingsTests` + `TabButton` (`settings/`), `StatsTests` (`stats/`).

## What is tested

- `audit/` - every patch applies on this version. `audit/mixin/<Mixin>` for each mixin listed in
  `litematica_turbo.mixins.json`: Turbo's guard saw it, its Litematica / malilib target loads, every injection
  point was found, and the loaded target class really carries the patch (each of the mixin's own `turbo$`
  members merged in - read from the mixin's class file - or the accessor interface implemented).
  `config-read`, `config-complete`, `verify-clean` (`TurboMixinGuard.verify` finds nothing, settings tab patches
  fine), `turbo-on` (not switched off as incompatible), `live-renderer-patched` (the world renderer Litematica
  really uses is the patched class).
- `settings/` - the Turbo tab. For each of the 9 options: `save/<key>` (changed through its malilib option as
  the tab does: TurboConfig follows at once, the file has the new value and still every other one, changing it
  back restores it) and `load/<key>` (a file with the value loads into TurboConfig, unset keys stay default, the
  file is rewritten with every option, the tab shows it, showing it saves nothing, the game's own file is not
  touched). Plus `file-has-every-key`, `tab-shows-config`, `load-bad-values` (fallbacks and clamps),
  `int-ranges` (malilib limits), `apply/build-threads` (-1 = stock render thread only, fixed, automatic; the
  renderer reload is requested and applied on the next tick), `apply/master-switch` (off = every Turbo switch
  off, as stock Litematica), `apply/skip-empty-refresh` (refresh, not reload), `apply/ghost-move`, `tab/options`
  (Litematica's config screen: the Turbo tab shows exactly Turbo's options; Litematica's own tabs show none of
  them and are the same before and after) and `tab/button` (the real screen opened in game: the Turbo button
  after Litematica's tabs, clicking it shows Turbo's options and greys it out, clicking Litematica's Generic tab
  goes back).
- `stats/` - the F3 `[Litematica]` line. `counts-builds`, `summary-format`, `skip-share`, `summary-steady`
  (BuildStats), and on Litematica's real debug line (`WorldRendererSchematic.getDebugInfoRenders`, what F3
  shows): `f3-line-turbo-on` (= Litematica's own line + ` | Turbo T:.. Q:..` with the dispatcher's numbers),
  `f3-line-stats-off` and `f3-line-turbo-off` (exactly Litematica's own line), `f3-line-no-dispatcher`. Without
  a schematic world Litematica has no build dispatcher, so the line tests give the live renderer an idle one
  (Litematica's patched class, allocated without starting threads) for the duration of the test.

Every settings test puts TurboConfig, its file and the tab back the way it found them. `BuildStats` is static:
the stats tests add to its counters, so tests compare differences, never absolute values.

## Adding a test for a new feature

1. Add its line to `test-plan.json` (prefix + how many different tests must pass), in the same change.
2. Write the tests in a `<Group>Tests` class (copy `StatsTests`): `tests.add("<group>/<name>", () -> {...})`
   with `SelfTest.check` / `SelfTest.equal`; `throw SelfTest.skip("why")` where it cannot apply on this version
   (never pass silently). Register the class in `SelfTest.run`.
3. Compare Turbo with plain Litematica where possible: Turbo's own switches (`TurboConfig` fields, the tab's
   options) turn parts off in the same game.
4. Read the result back from the game (config, files, Litematica objects, the screen), never just "no
   exception"; reach game classes through `ctx.cls(...)` / `ctx.turbo(...)` and `Ctx.call` / `Ctx.getField`,
   and look names up instead of hard-coding version lists. Anything touching the screen or Litematica's
   renderers goes through `ctx.onMain`.
5. Run it on the test box over every version; `suite_check.py litematica-turbo --only <group>/` is the check.

## Known bugs

A test that shows a reported, not yet fixed mod bug gets an id starting with `knownbug/`
(`SelfTest.KNOWN_BUG`): the normal run leaves it out, `FILTER=knownbug/` runs it. When the bug is fixed, move the
test to its normal id.

None so far.
