**Litematica Turbo** is a client-side add-on that makes [Litematica](https://modrinth.com/mod/litematica) smooth with big schematics. Install it next to Litematica - nothing else changes, all your settings and schematics stay the same.

## What it fixes
Litematica builds every schematic chunk on the render thread, so loading a big schematic, flying around it or moving it freezes the game for up to a second at a time. Turbo moves that work off the render thread:

- **Multithreaded chunk builds** - schematic chunks are built in the background on several threads.
- **No stutter near the schematic** - chunks next to you are rebuilt in the background too.
- **Smooth placement moving** - while you scroll or nudge a placement, the already built schematic is shown moved along; it is rebuilt once you stop, and swapped in within one frame.
- **Complete copy while moving** (1.16.5 - 1.20.4) - the moved picture is built in the background from every block, so it has no holes where the world already had the same blocks; it stays within a memory limit and is dropped first when memory gets tight.
- **Skips empty space** when building chunks.
- **F3 stats** on the `[Litematica]` line.

## Measured (big storage build, 1.08 million blocks)
| | Litematica | with Turbo |
|---|---|---|
| Moving the placement | 4-6 FPS | 140-300 FPS |
| Worst frame while loading | 500-1200 ms | 25-190 ms |
| Stutters (>50 ms) flying through | 13-52 | 0-5 |
| FPS standing still | same | same |

## Settings
Litematica config menu → **Turbo** tab. Build thread count, empty-space skipping, smooth moving and more; changes apply right away.

## Safe
If a Litematica update ever breaks one of Turbo's patches, Turbo switches itself off for that session (Litematica works exactly as normal) and tells you in chat - it does not crash your game.

## Versions
One file per Minecraft version - pick the one for your Minecraft version and loader. Client only - not needed on servers.
- **Fabric**, Minecraft 1.16.4 - 26.3: requires Litematica (and its MaLiLib).
- **Forge** (1.16.5 - 1.20.1) and **NeoForge** (1.20.4 - 26.2): requires [Forgematica](https://modrinth.com/mod/forgematica) (and its MaFgLib).

Beta: please report any problem with your Minecraft and Litematica version.

**See what's being worked on next:** [Useless Mods Workshop](https://useleess.github.io/workshop)
