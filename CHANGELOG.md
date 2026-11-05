# Litematica Turbo changelog

## 0.5.0 (beta) - 2026-11-05

### New

- Huge schematics that are mostly empty (for example 2000 x 300 x 2000 with scattered builds) now take memory only where they hold blocks, and a file that cannot fit in the memory Minecraft has shows a red message instead of running the game out of memory. Switch it off with "Compact storage for huge schematics" in the Turbo tab.

- Minecraft 1.16.5 - 1.21.4 (Fabric, Forge and NeoForge): the schematic is drawn with far fewer draw calls, because the built pieces of a 4 x 4 x 4 block of pieces are drawn together; the picture is the same. Switch it off with "Batch drawing" in the Turbo tab. If it ever hits an error it turns itself off and tells you in chat.

- Schematic pieces far away (at least half your render distance) are drawn without their outline lines, which there are only a blur but cost as much as near ones; filled sides and blocks stay. Switch it off with "Less detail far away" in the Turbo tab; it also turns itself off while Minecraft is low on memory.

- Moving a placement now shows a complete copy of it (Minecraft 1.16.5 - 26.3, Fabric, Forge and NeoForge): it is built in the background from every block, nearest first, so the moved view has no holes where the world already had the same blocks. It uses graphics memory within a limit and is dropped first when memory gets tight. Switch it off with "Complete copy while moving" in the Turbo tab; if it ever hits an error it turns itself off and tells you in chat.

- Turbo now runs next to Sodium and Iris (Fabric, 1.16.5 - 26.3) and is tested with them, also with a shader pack on. With Iris shaders on, batch drawing pauses by itself (shaders draw the schematic differently), tells you in chat and comes back when shaders are off.

### Fixes

- Fixed a crash on Minecraft 1.21.11 and 26.x when chests, signs and other block entities of a schematic were drawn while Turbo rebuilt them (also when switching layer views, translucent or fluids). (#1)
- Fixed a schematic chunk now and then staying invisible on Minecraft 1.21.11 and 26.x when Litematica finished loading it at the moment the visible chunks were listed (a Litematica timing issue Turbo now covers).
- Fixed batch drawing turning itself off now and then (Minecraft 1.16.5 - 1.21.4, seen with Sodium) when Litematica dropped a piece's graphics buffer at the wrong moment: that piece is now drawn the normal way and batch drawing stays on.
- Fixed a rare crash or broken schematic pieces on Minecraft 1.21 - 1.21.10 when switching Iris shaders on or off (or reloading with F3+A) while the schematic was still being built.
- Fixed a crash on Forge 1.16.5 - 1.18.2 when a schematic placement replaced chests or other block entities.
- If Turbo's fast placement copying ever hits an error, it now turns itself off for the rest of the session and tells you in chat instead of crashing the game; placements then work the normal Litematica way.
- Fixed a rare crash on Minecraft 1.21.10 and 1.21.11 with Iris shaders on ("Missing elements in vertex") when a schematic chunk was built just as the world was drawn or shaders were switched, and schematic chunks built in the wrong format for the shaders. If a chunk build ever hits an error on a build thread, Turbo now drops that build, builds the chunk again, tells you once in chat and writes the details to the log instead of crashing the game.
- Fixed schematics drawn with broken block faces for a whole session on Minecraft 1.20.1 and 1.20.4 with Sodium and Iris: Turbo's build threads could make Sodium mix up its vertex formats when the first schematic was built.

## 0.4.3 - 2026-10-24

- Maintenance release: no gameplay changes. The Modrinth page links to the workshop site
  (https://useleess.github.io/workshop), and releases now go through the same checked push process
  as the other mods.

## 0.4.2 (beta) - 2026-10-22

- Fixed: Turbo could switch itself off with "1 of N patches do not fit this Litematica version" even
  though everything fit (happened with newer Fabric Loader versions, e.g. 0.20).
- If only the Turbo settings tab cannot attach to a Litematica version, the tab is hidden and all
  speed features stay on (before, the whole mod switched off).
- 1.21.4: works with Litematica 0.21.2 and newer again; fixed a crash with Litematica 0.21.2 - 0.21.6.
- 1.21 / 1.21.1: needs Litematica 0.19.61 or newer (older 0.19.x releases do not fit).
- Mod icon added; author shown as Useleess.
