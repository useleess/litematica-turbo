# Litematica Turbo

Client add-on that makes Litematica handle huge schematics smoothly. It patches Litematica at load
time with Mixins, so it is installed next to a normal Litematica jar (no fork). Fabric only.

## Versions
One jar per Minecraft version, built into `versions/<mc>/build/libs/`.

| Jar | Minecraft | Litematica | Notes |
|---|---|---|---|
| 1.16.5 | 1.16.4 - 1.16.5 | dev builds 2021 | Litematica already has build threads here; Turbo sets their count |
| 1.17.1 | 1.17.1 | 0.9.x | |
| 1.18.2 | 1.18.2 | 0.11.x | |
| 1.19.4 | 1.19.4 | 0.14.x | |
| 1.20.1 | 1.20.1 | 0.15.x | |
| 1.20.4 | 1.20.3 - 1.20.4 | 0.17.x | |
| 1.21 | 1.21 - 1.21.1 | 0.19.x | |
| 1.21.4 | 1.21.4 | 0.21.x | |
| 1.21.10 | 1.21.9 - 1.21.10 | 0.24.x | |
| 1.21.11 | 1.21.11 | 0.26.x | Mojang names (like 26.x) |
| 26.2 | 26.2 | 0.28.x | unobfuscated Minecraft |
| 26.3 | 26.3 | 0.29.x | |

1.12.2 (Litematica for Ornithe) is not done: different loader tooling and a different Litematica.

## What it does
- **Multithreaded schematic chunk builds.** Litematica builds every schematic chunk on the render
  thread (worker threads commented out or removed). Turbo starts build threads and fixes the
  races that come with them (shared random generators, GL/GPU calls from build code, overlapping
  builds of one chunk, buffer caches not returned, profiler use off-thread, ...).
- **No render-thread rebuilds of nearby chunks** (the stutter when standing in a schematic).
- **Skips empty 16-high slices** of a chunk column when building (cells still in vanilla order).
- **Faster copy of placements into the schematic world** (up to 1.21.10; Litematica 0.26+ copies
  on its own background threads).
- **Ghost move:** while a placement is moved (scrolling, nudging), the already built chunks are
  drawn shifted and rebuilding waits; once the placement has stayed still for `ghostSettleMs`
  the real rebuild runs in the background and replaces the ghosts in one frame. Only for a single
  enabled placement; rotate/mirror/sub-region edits take the normal path.
- F3 stats appended to the `[Litematica]` line.
- **Fail-safe:** if a patch does not fit the installed Litematica (e.g. after a Litematica
  update), Turbo switches itself off for the session (stock behaviour) and says so in chat
  instead of crashing (`TurboMixinGuard`).

Settings: in game in Litematica's config menu, **Turbo** tab (applied live; thread count / on-off
reload the schematic renderers once). Stored in `config/litematica_turbo.properties`.

## Layout
- `core/` - plain Java shared by every version (config, stats, section maths). Unit tests.
- `versions/<mc>/` - one Loom project per Minecraft version with that version's mixins.
- `gradle/version.gradle` - build setup shared by all versions (values in `versions/<mc>/gradle.properties`).

## Build
Gradle runs on JDK 25 (toolchains for JDK 8/17/21/25 are set in `gradle.properties`).
- All versions: `./gradlew build`
- Some: `./gradlew build -Pmc=1.20.4,1.21.4`

## Dev checks
- `./audit.sh <mc>...` - loads every patched class; reports patches that did not apply.
- `./make-bench-world.sh <mc> <jdk>` - creates the flat bench world with the vanilla server.
- `./bench.sh <mc>...` - A/B benchmark (stock, then Turbo) with `main_storage.litematic`;
  results in `versions/<mc>/run/turbo-bench.txt`, screenshots in `run/screenshots/`.
  `-PbenchProfile` also records Minecraft's debug profile of the steady view.
- `./final-check.sh` - audit + benchmark for every version into `final-check.txt`.
- `./new-version.sh <from> <to>` - starts a new version folder as a copy of another.
