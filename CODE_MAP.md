# Code map

What lives where in Litematica Turbo, and how to add a Minecraft version. Read it before changing code; update it
in the same change when files move or are added.

## Layout

| Folder | What |
|---|---|
| `core/` | Version-free Java (no Minecraft, no Litematica): config, build threads, section slices, sparse block storage and memory budget, stats, the benchmark's script. Compiled into every version jar; unit tests in `core/src/test` (incl. `HygieneTest`). |
| `shared/<first>-<last>/` | Game-side code several Fabric versions share, written once in package `com.litematicaturbo.shared`. A version uses every folder whose range holds it (both ends included). |
| `shared/1.16.5-1.21.4/` | Among others `BatchRegions`: the batch drawing every render era shares (which layers, when it runs, grouping by region, making a region); an era passes in only how its pieces' data is read back (`BatchRegions.Gpu`). |
| `shared/1.16.5-1.16.5/` | Batch drawing for Minecraft 1.16.5 only (`BatchDraw`: its `Gpu` and drawing, its mixins): the first render era (fixed-function GL). |
| `shared/1.17.1-1.21.4/` | Batch drawing for the direct-GL render era (`BatchDraw`, `BatchPiece`, `BatchPlacement`, two mixins); the smaller ranges inside it hold only what the game changed: `BatchCalls` (Mojang call names and the vertex buffer's GL name, 1.17.1-1.18.2 / 1.19.4-1.21.4), `ChunkOffsetPlacement` (1.17.1-1.21), `ModelViewPlacement` (1.21.4) and `WorldRendererSchematicBatchLayerMixin` (its method signature differs in 1.17.1-1.18.2, 1.19.4-1.20.4, 1.21, 1.21.4). |
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
- **Batch drawing (1.16.5 - 1.21.4):** `BatchBook` (core, no game code) holds the rules: 4 x 4 x 4 piece regions, a region is
  made only after its pieces stayed unchanged 20 frames, one region and 4 MB read-back per frame, unused regions given back;
  `BatchDrawing` (core) the setting check, the switch-off after an error (chat line via `SettingsTicker`) and the draw-call
  counters the tests read. `BatchRegions` (`shared/1.16.5-1.21.4`) is the game-side part written once: layer index, the
  on / off gate (paused for a test, a placement being moved, regions freed once off), the listed pieces grouped by region,
  picking / making a region (read back through the era's `BatchRegions.Gpu`, positions moved to the region's corner,
  one upload). Each era's `BatchDraw` keeps only its `Gpu`, its draw calls and its try / catch. 1.16.5 (`shared/1.16.5-1.16.5`): `WorldRendererSchematicBatchMixin` takes over `renderBlockLayer`
  for the opaque layers and `BatchDraw` reads the pieces back through the array buffer and draws a region with one
  `glMultiDrawArrays`. 1.17.1-1.21.4 (`shared/1.17.1-1.21.4`): `WorldRendererSchematicBatchLayerMixin` calls
  `BatchDraw.begin` at the start of `renderBlockLayer` (regions due are made - each piece's vertex buffer is read back and
  moved to the region's corner - and the pieces a ready region holds leave Litematica's list), `drawRegions` just before
  Litematica unbinds the layer's shader (one `glMultiDrawElementsBaseVertex` per region from its own vertex array and index
  buffer; `BatchPlacement` puts the region in place: chunk-offset uniform, or the model-view matrix in 1.21.4) and
  `WorldRendererSchematicBatchMixin` gives the list back at the end. `ChunkRendererSchematicVboBatchMixin` counts a piece's
  new built data; a piece's GL buffer name is read from the GL state (`BatchCalls`; the audit allows mixins on Litematica
  classes only). See-through layers, a placement being
  moved and any error keep the stock drawing (an error inside `drawRegions` costs that layer's region pieces for one frame).
  A new render era (1.21.10+) adds only its own `BatchDraw` (a `Gpu` + its drawing) and reuses `BatchRegions` (widen its
  folder range) and `BatchBook`.
- **Less detail far away (every version):** `FarDetail` (core) holds the rules (a piece at least half the render distance
  away, 3 pieces at least, is far; off while less than a quarter of Java's memory is free), the setting check, the
  switch-off after an error (chat line via each `SettingsTicker`) and the outline counters the tests read. Every version's
  `WorldRendererSchematic.renderBlockOverlay` asks each listed piece `hasOverlay()` and then its data
  `isOverlayTypeEmpty(type)`, each frame: `WorldRendererSchematicFarMixin` notes the piece at the first call and answers
  "empty" for the OUTLINE type of a far piece (`FarOutlines`, `shared/all`, catches an error). Nothing is rebuilt, so a
  piece coming near gets its lines back in the next frame; filled sides and blocks are untouched. The mixin exists twice
  only because Litematica moved the overlay type: nested in `ChunkRendererSchematicVbo` (`shared/1.16.5-1.20.4`), its own
  class from 1.21 (`shared/1.21-26.3`). Camera and render distance come from Litematica's own fields (`lastCameraX/Z`,
  `renderDistanceChunks`). Setting: "Less detail far away" (`TurboConfig.farDetail`).
- **Moving a placement:** `GhostMove` + `GhostRenderers` keep showing the old chunks shifted until the new ones are built.
- **Complete copy while moving (every version):** Litematica's pieces leave out blocks that already match the world, so
  the shifted old pieces have holes. `CompleteCopy` (core) holds the rules: the byte limit (the smaller of an eighth of Java's
  memory and a quarter of the graphics memory the card reports), when the copy is skipped or dropped (Java memory low, or less
  than 256 MB graphics memory free), the switch-off after an error (chat line via each `SettingsTicker`) and the counters the
  tests read. `CompletePieces` (`shared/1.16.5-26.3`, with `CompletePiece` and `CompleteWorldRenderer`) is the game side: when `GhostMove` starts a move it plans one complete
  piece per ghost holding part of the placement (the ghost's boxes, nearest first), builds them a few at a time on the build
  threads and keeps them while they fit the limit; `GhostMove.blockRenderers()` gives the block layers the finished pieces
  (moved to where the placement is) plus the ghosts they do not cover yet (the overlay still comes from the ghosts). A kept copy
  stays until the placement is deselected, changed or removed (`GhostMove.endAndDropCopy`) and shows at once on the next move;
  `GhostMove.isHoldingWork` waits up to 3 s for its last builds before the real copy changes the schematic world. A complete
  piece is a normal Litematica chunk renderer made by `WorldRendererSchematicCompleteMixin` and marked by
  `ChunkRendererSchematicVboCompleteMixin`: it compares with an all-air world (`EmptyChunkView`), draws no overlay and keeps out
  of the world renderer's block entities. Those two and the camera position (`GhostRenderers.cameraPos`) exist once per naming:
  Yarn `shared/1.16.5-1.21.10`, Mojang `shared/1.21.11-26.3`. On the Mojang-named versions a piece also keeps its graphics
  buffers in a GPU uploader of its own (`CompleteUploads`; the worker, the upload callback and the block layers use it), malilib's box
  class is read through `CompleteBoxes` (`versions/1.21.11` / `shared/26.2-26.3`: it moved package), and because Litematica 0.26
  changes the schematic world during the move itself, `GhostRenderers.holdForCopy` / `isCopyHeld` keep its background threads
  (`PlacementManagerDaemonExecutorMixin`) and the unloading of the chunks the placement left (`SchematicPlacementManagerHoldMixin`)
  waiting while the copy is built. `GpuMemory` is the read-only card query (LWJGL 3; `shared/1.16.5-1.21.4`; from 1.21.10 the
  graphics API reports no memory, so `shared/1.21.10-26.3` answers -1 and only the Java limit counts). The other version-specific
  bits are `TurboRenderer.turbo$clearNeedsUpdate` and `TurboDispatcher.turbo$queueLater` (1.21 - 1.21.10 pass a profiler). Setting: "Complete copy while moving" (`TurboConfig.completeCopy`).
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
