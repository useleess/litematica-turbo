# Code map

What lives where in Litematica Turbo, and how to add a Minecraft version. Read it before changing code; update it
in the same change when files move or are added.

## Layout

| Folder | What |
|---|---|
| `core/` | Version-free Java (no Minecraft, no Litematica): config, build threads, section slices, sparse block storage and memory budget, stats, the benchmark's script. Compiled into every version jar; unit tests in `core/src/test` (incl. `HygieneTest`). |
| `shared/<first>-<last>/` | Game-side code several Fabric versions share, written once in package `com.litematicaturbo.shared`. A version uses every folder whose range holds it (both ends included). |
| `shared/all/` | Game-side code every version uses (entry point, start-up, settings tab, ghost moving, mixin guard, the mixins that never changed). |
| `versions/<mc>/` | The thin per-version layer of a Fabric version: only what differs there, plus `fabric.mod.json`, `litematica_turbo.mixins.json`, `lang/en_us.json`, `gradle.properties` (Minecraft / Litematica / MaLiLib versions). |
| `versions/<mc>-forge`, `-neoforge` | Forge / NeoForge builds (for Forgematica): only the loader entry point, `mods.toml` and (where Forge's own classes need a patch) `litematica_turbo.forge.mixins.json`; all other code comes from the Fabric folder named by `fabric_source` in their `gradle.properties`. |
| `forge/` | The separate Gradle build for those (Architectury Loom can't share a build with Fabric Loom). `forge/version.gradle` = their shared setup. |
| `gradle/` | `version.gradle` (setup of every Fabric version: dependencies, dev-client switches), `shared-sources.gradle` (copies `shared/` into a version). |
| `selftest/` | The in-game test agent (a `-javaagent` jar, never in a release jar). Test groups `*Tests.java`, runner `SelfTest`. |
| `icon/`, `gallery/` | Mod icon (+ `make-icon.py`, which draws it), store screenshots. |

## How `shared/` gets into a version

`gradle/shared-sources.gradle` copies each matching `shared/` folder into `build/generated/turbo-shared`, renamed to
the version's own package `com.litematicaturbo.mc<digits>` (e.g. `mc12111`), and compiles it with `versions/<mc>/src`.
A class may exist only once among the folders a version uses (the copy fails otherwise). The Forge / NeoForge
builds get the same code through their `fabric_source` folder.

## Main flows

- **Start-up:** `LitematicaTurbo` (Fabric) / `LitematicaTurboForge` / `LitematicaTurboNeoForge` -> `TurboStartup.init`
  (config, logger, bench, audit). `TurboMixinGuard` (mixin plugin) skips optional patches whose target is missing.
- **Faster builds:** schematic chunk builds go to Turbo's build threads (`BuildThreads`, `BuildWorkers` /
  `TurboBuildThread`, the `ChunkRenderDispatcher*` / `ChunkRenderWorker*` mixins); `EmptySections` + `SectionSlices` +
  `BoxCellIterator` skip empty 16-high sections; `RebuildQueue` moves near rebuilds off the render thread.
- **Placing:** `SchematicCopy` + `SchematicPlacingUtils*Mixin` (faster copy into the schematic world; a call that
  throws switches the copy off for the session, `SettingsTicker` tells the player), `PlacementQueue` /
  `SchematicPlacementManagerQueueMixin` (catch-up budget per tick). Forge 1.16.5 - 1.18.2 only: `ModelDataManagerMixin`
  (listed in `versions/<mc>-forge/.../litematica_turbo.forge.mixins.json`) stops Forge throwing when a block entity of
  the schematic world is removed.
- **Huge schematics:** `CompactBlocks` (all) + `LitematicaBlockStateContainerMixin` give Litematica's block container a
  `SparseBitArray` (16 x 16 x 16 sections from `SparseIntArray` in core, only where blocks are) instead of the dense packed
  array when the array is big and mostly empty; the palette growing keeps it sparse; saving builds the packed array again.
  `LitematicaSchematicReadMixin` (`shared/1.16.5-1.21.4` File, `shared/1.21.10-26.3` Path) refuses a file whose unpacked
  size (`MemoryBudget`) cannot fit, with a red translated message. `CompactStorage` (core) holds the policy and the
  switch-off after an error (chat line via each `SettingsTicker`). Setting: "Compact storage" (`TurboConfig.compactStorage`).
- **Moving a placement:** `GhostMove` + `GhostRenderers` keep showing the old chunks shifted until the new ones are built.
- **Settings:** `TurboConfig` (core) holds the options; `TurboSettingsTab` + `TurboOptions` + `GuiConfigsMixin` add
  the Turbo tab to Litematica's config screen; `SettingsTicker` applies changes each tick. Option names are
  translation keys `config.name.<option>` in `lang/en_us.json`.
- **F3 stats:** `BuildStats` (core) + `WorldRendererSchematicStatsMixin`.
- **Dev benchmark:** `TurboBenchRun` (core) drives it; `BenchBase` (all) + `BenchHost` (Yarn / Mojang names) +
  per-version `TurboBench` give it the game calls. Started with `-PbenchSchematic` (`bench.sh`).

## Scripts (repo root)

- `ingame-test.sh` - in-game client tests per version (the release tester runs it); `audit.sh` - loads every patched
  class per version; `gradle-run-tag.sh` - helper both use.
- `bench.sh`, `make-bench-world.sh`, `final-check.sh` - dev benchmark (stock vs Turbo); `range-check.py` - audits
  older Litematica releases inside each version's allowed range; `new-version.sh` - copies a version folder.

## How to add a new Minecraft version

1. `./new-version.sh <nearest-mc> <new-mc>` (copies the thin layer, renames the package); fix `gradle.properties`.
2. Add it to `allVersions` in `settings.gradle` and to the list in `final-check.sh` (`ingame-test.sh` and
   `audit.sh` take every folder in `versions/`).
3. Widen the `shared/` folder names whose code still fits the new version (e.g. `1.21.11-26.3` -> `1.21.11-26.4`);
   where the game or Litematica changed, put the new variant in `versions/<new-mc>/` (or a new range folder).
4. List its mixins in `litematica_turbo.mixins.json`; run `audit.sh <new-mc>` and `ingame-test.sh <new-mc>`.
5. Forge / NeoForge: add `versions/<new-mc>-neoforge` with `fabric_source = <new-mc>` and list it in `forge/settings.gradle`.
