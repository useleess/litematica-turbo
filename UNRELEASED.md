# Unreleased changes

Collected here until the next release, then used as the GitHub / Modrinth notes.

### New

- Huge schematics that are mostly empty (for example 2000 x 300 x 2000 with scattered builds) now take memory only where they hold blocks, and a file that cannot fit in the memory Minecraft has shows a red message instead of running the game out of memory. Switch it off with "Compact storage for huge schematics" in the Turbo tab.

### Fixes

- Fixed a crash on Minecraft 1.21.11 and 26.x when chests, signs and other block entities of a schematic were drawn while Turbo rebuilt them (also when switching layer views, translucent or fluids).
- Fixed a crash on Forge 1.16.5 - 1.18.2 when a schematic placement replaced chests or other block entities.
- If Turbo's fast placement copying ever hits an error, it now turns itself off for the rest of the session and tells you in chat instead of crashing the game; placements then work the normal Litematica way.
