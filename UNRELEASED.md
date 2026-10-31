# Unreleased changes

Collected here until the next release, then used as the GitHub / Modrinth notes.

### Fixes

- Fixed a crash on Minecraft 1.21.11 and 26.x when chests, signs and other block entities of a schematic were drawn while Turbo rebuilt them (also when switching layer views, translucent or fluids).
- Fixed a crash on Forge 1.16.5 - 1.18.2 when a schematic placement replaced chests or other block entities.
- If Turbo's fast placement copying ever hits an error, it now turns itself off for the rest of the session and tells you in chat instead of crashing the game; placements then work the normal Litematica way.
