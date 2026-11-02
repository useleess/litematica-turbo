# Litematica Turbo in-game tests

Scripted tests that run inside a real game client of every version in `versions/` (Fabric, Forge via
Forgematica, NeoForge), against the mod's real code and the real Litematica. Unit tests of the
version-independent core stay in `core/src/test` (`./gradlew build`). `audit.sh` (the plain mixin audit) is
still run by the release as its own check; the `audit/` tests below do the same check in more detail.

Turbo is client-only, so there are no server tests: plain `./ingame-test.sh` and `./ingame-test.sh client`
both run the client tests.

## Running

Run on the test box (where tests run and all test rules: the workspace `CLAUDE.md`):

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
./ingame-test.sh client 1.20.1 filter=build/,near/   # filter=... as an argument: the same as FILTER, for the test box runner
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

### Sodium and Iris (`sodium/`)

Runs started with `with=sodium` or `with=sodium,iris` load those mods next to Turbo (every version that has them;
the others answer `0 passed, 0 failed, 0 skipped` with the reason). `gradle/with-mods.gradle` holds the version table
(per version folder the newest Sodium / Iris built for it, Fabric and NeoForge, downloaded from Modrinth for that run
only, with their nested libraries). With Iris the script downloads the shader pack Miniature Shader (MIT, Modrinth) into
the throwaway run folder and chooses it in `config/iris.properties` with shaders off; the tests switch shaders on and
off through Iris's own API, and then make Litematica's renderers anew, as on joining a world with shaders on.

```sh
"../../discord-bot/.venv/Scripts/python.exe" ../../discord-bot/testbox.py run . ingame-test.sh client with=sodium,iris filter=sodium/ 1.16.5 1.20.4 1.21.4 1.21.10 1.21.11 26.3
"../../discord-bot/.venv/Scripts/python.exe" ../../discord-bot/testbox.py run . ingame-test.sh client with=sodium filter=build/,draw/,far/,copy/,move/ 1.21.4
```

Without the mods every `sodium/` test skips with the reason. With them: the schematic is drawn (outlines counted, draw
calls on batch eras, the game's own screenshot full of colour), each speed-up passes the body of its own group's test,
no GL error line in the game log, no Turbo feature switched itself off (state and chat), screenshots (`sodium.png`,
`sodium-iris-shaders.png`, brought back in `test-shots/`), shaders on and off again, switching shaders mid-game does to
the schematic exactly what it does to plain Litematica (`shaders-toggle-like-stock`), and batch drawing pausing while
shaders are on (`shaders-pause-batch-drawing`, batch eras).

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
- The in-world groups (they leave the title screen for a real test world, so they are registered last):
  `BuildTests` (`build/`), `NearTests` (`near/`), `EmptyTests` (`empty/`), on top of
  `WorldTests` (the usual captures and the comparison of two), `Fixture` (the test world, the test schematics, the
  captures), `Game` (opening the world, the player, the block registry; yarn / Mojang names alike),
  `Digest` (the description of a chunk build), `WorldFiles` (writes level.dat, .litematic and the NBT for them).

## What is tested

- `audit/` - every patch applies on this version. `audit/mixin/<Mixin>` for each mixin listed in
  `litematica_turbo.mixins.json`: Turbo's guard saw it, its Litematica / malilib target loads, every injection
  point was found, and the loaded target class really carries the patch (each of the mixin's own `turbo$`
  members merged in - read from the mixin's class file - or the accessor interface implemented).
  `config-read`, `config-complete`, `verify-clean` (`TurboMixinGuard.verify` finds nothing, settings tab patches
  fine), `turbo-on` (not switched off as incompatible), `live-renderer-patched` (the world renderer Litematica
  really uses is the patched class).
- `settings/` - the Turbo tab. For each option: `save/<key>` (changed through its malilib option as
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

### The in-world groups (`build/`, `near/`, `empty/`)

The promise they test: Turbo changes WHEN and WHERE schematic chunks are built, never WHAT is shown.

How a run goes (`Fixture`, built once on first use, the same for every version):

1. `Game.join`: writes a minimal superflat creative level.dat into `run-test/saves/turbo-test` (`WorldFiles`,
   1.16.5 format, every newer game upgrades it; nothing is shipped or committed) and opens it from the title screen
   (`startIntegratedServer` before 1.19, `IntegratedServerLoader.start` / `WorldOpenFlows.openWorld` after, found by
   return type). The script also sets render distance 12 and silences (Neo)Forge's mod-warning screen.
2. Test schematics, written as `.litematic` files from the running game's block registry and placed with Litematica's
   own placement manager, in chunk zones in front of the player (who looks along +Z): `N` near (3 x 3 chunks starting one chunk in front of the player, since Litematica builds only chunks in the view), `A` every block state without a block entity, `B` every state with one,
   `S` sparse (full world height, a handful of blocks at the very bottom / top / on section borders, most chunks and
   sections empty), `T` translucent and transparent blocks, `F` fluids (every level, waterlogged), `E` blocks along
   the chunk borders.
3. A capture = Turbo configured one way (`TurboConfig`: master switch, `buildThreads`, `skipEmptySections`,
   `asyncNearRebuilds`), the schematic renderers reloaded the way Turbo's own tab does (so every chunk is built again),
   waited until every chunk of every zone has a finished build and nothing is queued for a second, then every chunk's
   build result described by `Digest` plus Turbo's `BuildStats` counters. Reference = Turbo's master switch off
   (plain Litematica). Captures are kept, tests only compare them.
4. `Digest` = what the build produced, read by reflection from Litematica's chunk data: used block layers and overlay
   types, block entities with positions, translucency sort data (a position per quad), buffer sizes, vertex-buffer
   index counts / draw modes. Times, GPU handles and locks are left out; buffer contents are never read (a closed
   native buffer must not be touched), so "same geometry" means same quads / vertices counts, sort positions, layers
   and block entities, not a byte compare of the vertex data.

- `build/` - plain Litematica twice agrees (`baseline-repeatable`, the digest itself is stable), the zones have
  geometry (`geometry-exists`, else every comparison would pass on nothing), Turbo equals plain Litematica per zone
  (`all-states-same`, `block-entities-same`, `translucent-same`, `fluids-same`, `sparse-same`, `region-edges-same`,
  `near-set-same`, `overlay-same`), for 1 / 2 / 4 / 8 / 16 / automatic build threads (`threads-<n>-same`,
  `threads-auto-same`), thread counts agree with each other and a parallel build twice is the same
  (`threads-agree`, `repeat-parallel-stable`), nothing missed (`no-chunk-missed`) or built twice (`built-once`: at
  least one build per chunk, never more than plain Litematica needed + 2), the setting is applied
  (`thread-count-applied`), the render thread does less (`render-thread-time-drops`, skips where plain Litematica
  already builds off the render thread).
- `near/` - the chunks within 32 blocks of the player (`zones-are-near-and-far` checks the zones sit on the right
  side of Litematica's own near rule) built in the background and on the render thread equal plain Litematica's
  (`built-same-as-stock`, `async-off-same`, `far-chunks-same`); edits made in the schematic world next to the player
  (`Fixture`'s world is edited with setBlockState + Litematica's own refresh call, then put back): `edit-shows-up`,
  `edit-same-as-stock` (the background rebuild equals plain Litematica's rebuild of the edited world),
  `edit-leaves-other-chunks-alone`, `edit-border-both-chunks`, `many-edits-same-as-stock`, `edit-removed-restores`,
  and who builds it: `rebuild-off-render-thread` (near rebuilds on: the render thread built nothing),
  `async-off-builds-on-render-thread` (off: it did).
- `memory/` - a test-made region of 512 x 128 x 512 with twenty small clusters of blocks is read into Litematica's block
  container (counted in bytes, never timed): `sparse/far-fewer-bytes-than-dense` (sparse sections hold at most a twentieth
  of the dense array; the setting off stores it densely), `sparse/same-blocks-as-dense` (every block and a sample of air
  equal, saving gives the packed array it was read from), `sparse/edits-and-palette-growth-like-dense` (set blocks until
  the palette widens), `place/same-blocks-as-plain-litematica` (a lone placement of a sparse-stored file leaves the same
  blocks in the schematic world), `budget/too-big-file-gets-a-translated-message` (pretended low memory: nothing loaded,
  refusal counted, no crash; the setting off loads it), `safety/error-switches-off-and-loads-dense` (a fault in the sparse
  code: dense and unchanged, chat line, stays off), `messages/translated`.
- `draw/` - batch drawing, Minecraft 1.16.5 - 1.21.4 (Fabric, Forge, NeoForge); 1.21.10 and up skip each test with "nothing to batch (Litematica already draws a layer with one multi-draw)";
  Counted, never timed: `fewer-calls-per-frame` (draw calls per frame of
  the schematic with the setting on are at most half of those with it off, same game, same camera; read from
  `BatchDrawing`'s counters), `same-picture` (the frame read back from the game's main frame buffer with regions ready equals the
  frame drawn piece by piece, up to the pixels the plain drawing itself varies by between two frames), `changed-block-shows-up`
  (a block edited inside a ready region is visible, and the picture again equals the plain one), `setting-off-draws-piece-by-piece`
  (region buffers freed, none made), `error-switches-off-and-draws-piece-by-piece` (a fault in the batch code: off for the
  session, chat line, region buffers freed, same picture), `messages-translated`.
- `far/` - less detail far away, every version. Counted, never timed (`FarDetail`'s outline counters over about a second
  of frames, same game, same camera): `outlines-left-out-far-away` (far pieces draw no outline lines with the setting on
  and some with it off), `near-outlines-unchanged` (the near pieces' share of the outline draws is the same with the
  setting on and off), `low-memory-switches-it-off` (pretended low memory: every outline drawn, back once memory is fine),
  `error-switches-off-and-draws-every-outline` (a fault in the check: off for the session, chat line, every outline
  drawn), `messages-translated`. The test world's zones B, F and E (9 - 11 chunks ahead, render distance 12) are far,
  zone N is near.
- `copy/` - the complete copy while a placement moves, every version.
  Counted, never timed, on the lone test placement with a long settle time and a world that matches every second block of it:
  `drawn-pieces-hold-every-block` (the blocks of the pieces the block layers draw = the schematic's non-air blocks, no old
  piece left in the list), `off-keeps-the-holes` (setting off: no complete piece, the old pieces hold fewer blocks than the
  schematic), `pieces-built-nearest-first-and-kept` (pieces planned, all kept within the limit, one copy made),
  `deselected-frees-the-pieces` (deselecting drops the copy, every piece's graphics buffers deleted), `low-java-memory-builds-none`
  and `low-graphics-memory-builds-none` (pretended low memory: no copy, the old pieces shown), `memory-getting-tight-drops-the-copy`,
  `error-switches-off-with-a-chat-line` (a fault in the copy: off for the session, chat line, moving goes on with the old pieces),
  `screenshot-while-moving` (one picture, saved in the game folder's `screenshots/` for the reviewer), `messages-translated`.
- `place/` - a lone test placement (32 x 6 x 32, random blocks, one gold marker block) is added; the blocks in the schematic
  world (read back around the placement's origin) are exactly plain Litematica's, unrotated, rotated 90 / 180 / 270,
  mirrored, rotated + mirrored, and with a second placement laid over the first (`same-as-stock-*`, and `two-placements-overlapping-plain` / `-turbo`: every cell of two overlapping placements holds the block of one of them); the gold marker
  is where the schematic has it (`marker-block-arrives`) and moves when the placement is rotated / mirrored
  (`rotation-turns-the-build`, `mirror-flips-the-build`).
  `move/rotated-while-moving-same-as-stock` and `move/mirrored-while-moving-same-as-stock`: rotating / mirroring a
  placement that is being moved ends the shifted view and gives plain Litematica's result. The step before the turn is
  chosen so the turned placement ends in front of the player (in view for any window shape); a check before the runs
  fails with `TEST ENVIRONMENT` when too few of its chunks are in the camera's view.
- `compat/` - with a Litematica display option changed (translucent blocks, fluids, layer view single / above / below)
  every chunk Turbo builds equals plain Litematica's, and the option really changes what is built; Turbo's work never
  changes any of Litematica's own options (`litematica-options-untouched`).
- `empty/` - skipping empty 16-high sections changes nothing built (`same-geometry-*`, `same-geometry-everything`,
  also with Litematica's "extra block" overlay on: `world-extra-overlay-same`), empty chunks stay empty and the
  extreme sections (bottom / top of the world) are kept (`empty-chunks-no-geometry`, `extreme-sections-kept`), and
  the skipped work really is skipped: `skips-empty-sections`, `skipped-share-high`, `skip-off-skips-nothing`,
  `master-off-skips-nothing` (the `BuildStats` section counters).

A full in-world run is a few minutes per version (about 20 reloads of the schematic renderers).

Every settings test puts TurboConfig, its file and the tab back the way it found them. `BuildStats` is static:
the stats tests add to its counters, so tests compare differences, never absolute values.

## Feature list (what a player gets -> its tests)

For checking a rewrite feature by feature: every user-visible feature (README, Modrinth page, settings tab) and the
test id prefixes that show it works. All of them run on every version in `versions/`. `FILTER=<prefix>` runs one row.

| Feature (what the player sees / gets) | Test ids |
|---|---|
| Big schematics build on several background threads, picture identical to plain Litematica | `build/` (`threads-<n>-same`, `threads-auto-same`, `threads-agree`, `repeat-parallel-stable`, `no-chunk-missed`, `built-once`, `thread-count-applied`, `render-thread-time-drops`) |
| All block kinds look the same (every state, block entities, translucent, fluids, chunk borders, overlay) | `build/all-states-same`, `block-entities-same`, `translucent-same`, `fluids-same`, `sparse-same`, `region-edges-same`, `overlay-same` |
| Chests, signs and other block entities drawn while Turbo rebuilds their chunks never crash the game, also while layer views, translucent, fluids or a renderer reload change | `render/` (`draw-block-entities-while-rebuilt`) |
| No stutter standing in the schematic: nearby chunks rebuilt in the background, edits show up | `near/` |
| Empty space skipped when building, nothing visible lost | `empty/` |
| Adding / rotating / mirroring / overlapping placements puts the same blocks into the world, faster | `place/` |
| Huge, mostly empty schematics in little memory, a message for a file that cannot fit | `memory/` |
| Batch drawing: far fewer draw calls, the same picture, a failing batch switches itself off (1.16.5 - 1.21.4; newer versions skip) | `draw/` |
| Less detail far away: far pieces drawn without outline lines, near ones unchanged, off while memory is low, a failing check switches off | `far/` |
| Complete copy while moving: no holes in the moved view, kept within the memory limits, freed when deselected, a failing copy switches off | `copy/` |
| Smooth placement moving: ghost shifted, rebuilt once stopped, swapped in one frame, same end result | `move/` |
| F3 `[Litematica]` line with Turbo numbers, can be switched off | `stats/` |
| Fail-safe: a patch that does not fit switches Turbo off with a chat message, no crash | `safety/` |
| Settings tab in Litematica's config menu: every option saves, loads, applies at once; file format | `settings/` |
| Every patch applies on this Litematica version (compatibility) | `audit/` |
| Litematica's own options (translucent, fluids, layer view) still give the same picture; Turbo leaves Litematica's options alone | `compat/` |
| Works next to Sodium and Iris (shaders on and off); batch drawing pauses while shaders are on | `sodium/` (only in runs with `with=sodium[,iris]`; skip otherwise) |
| Test world opens without a pop-up in the way | `harness/popup/` |
| Known, not yet fixed mod bugs | `knownbug/` (see "Known bugs") |

Untested (and why):
- Absolute speed numbers of the Modrinth page (FPS while moving, worst frame, stutter count): they depend on the
  PC and its GPU; the box tests the relative promise instead (`build/render-thread-time-drops`, `move/no-rebuild-per-step`,
  `near/rebuild-off-render-thread`).
- How the F3 line is drawn on screen (font, position): the tests read the line's text, not the pixels.
- Forgematica / MaFgLib specific behaviour beyond what the same tests show on the Forge / NeoForge versions.
- Dev-only tools (`bench.sh`, `audit.sh`): not player features.

## Adding a test for a new feature

1. Add its line to `test-plan.json` (prefix + how many different tests must pass), in the same change.
2. Write the tests in a `<Group>Tests` class (copy `StatsTests`; for something that needs a real world and
   schematics copy `EmptyTests` and use `Fixture` captures: a new test schematic = a new zone in `Fixture`): `tests.add("<group>/<name>", () -> {...})`
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

- `knownbug/move/fixed-budget-final-view` - with a fixed catch-up budget the finished view after a move is not exactly plain
  Litematica's (older helper's finding).
- `knownbug/move/near-chunks-keep-block-buffers` - after a move across a chunk border the chunks in front of the player hold no
  uploaded block buffers on 1.19.4 / 1.20.1 while the build result is the same (older helper's finding).

- `knownbug/flaky/safety/litematica-builds-as-stock` (flaky test, parked): a plain-Litematica-against-plain-Litematica build
  of one chunk (-3,8) differed by a face at a border in the full all-version runs, twice in a row, but never in the
  filtered runs; run `FILTER=knownbug/` to see it.
- `1.17.1-forge` full run: the game ended without a result once in three full runs (box load; passed alone, 148 tests).

Seen once, not reproduced: `compat/layer-all-below-same-as-stock` failed in a full 1.21.4 run with plain Litematica's own
`IllegalStateException: Missing elements in vertex` (it happens while plain Litematica rebuilds a layer view; Turbo is off at
that moment). The test now tries the plain capture once more; a second failure is reported.

## Notes on how the tests deal with the platforms

- Test-box note (found 2026-10-28): a game window on the box can have its camera turned by the cursor moving while other
  windows are tiled, which emptied the view and made every later capture wait its 100 s (a 1.21.4 run ended `NO RESULT`
  after 2702 s). `Fixture.aim()` now points the player along +Z on every poll of `awaitIdle`.
- Litematica 0.24 (Minecraft 1.21.9 / 1.21.10): plain Litematica has its build worker threads commented out, so with Turbo off
  it never builds the queued chunks while a test waits (the 2702 s `NO RESULT` of 1.21.10, and every capture came back empty).
  There "plain Litematica" is Turbo with ONE build thread and everything else off (`Fixture.plainHasNoWorkers`), the same
  build code. Also on those versions `SchematicWorldRefresher.updateAll()` schedules nothing after a renderer reload, so the
  harness asks for the test chunks by name once the renderers stand (`Fixture.reload`).
- Forge 1.17.1 dies on a floating comparator that is removed again in the schematic world (Forge's own
  `ModelDataManager`): the all-states zone leaves comparators out there.
- The player is put into spectator mode the moment the world opens (some versions have no ground at the spawn and a falling
  survival player died of "fell out of the world", leaving the death screen over every later test).
- Two placements over the same cells: which one ends up in a cell is not fixed (plain Litematica included), so
  `place/two-placements-overlapping-*` checks that every cell holds the block of one of the two placements.
- A moved + rotated / mirrored placement: the views built while the placement and its neighbour chunks were still being
  worked on can keep or drop one face at a chunk border (plain Litematica as well, differently from run to run), so
  `move/rotated-` / `mirrored-while-moving-same-as-stock` check the placed blocks, then compare plain Litematica's and
  Turbo's views after a full rebuild of the final world. The other `move/same-as-stock-*` scenarios compare the moved
  views directly and build once more before a difference counts.
