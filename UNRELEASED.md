# Unreleased changes

Collected here until the next release, then used as the GitHub / Modrinth notes.

- Mod author shown in the mod list is now "Useleess".
- 1.21.4: works with Litematica 0.21.2 again (one patch did not fit, so Turbo switched itself off). Needs Litematica 0.21.2 or newer; the game now says so instead of Turbo silently staying off.
- 1.21.4: fixed a crash with Litematica 0.21.2 - 0.21.6 (threaded builds made a render-thread-only call there).
- 1.21 / 1.21.1: now asks for Litematica 0.19.61 or newer (older 0.19.x releases do not fit; Turbo stayed off there).
- All versions: fixed Turbo switching itself off on newer Fabric Loader (0.20+) with the message "1 of N patches do not fit this Litematica version". The patches did fit; the self-check misread a class name.
- All versions: if only the Turbo settings tab cannot attach to a Litematica version, the tab is hidden and all speed features stay on (before, the whole mod switched off).
- Added the mod icon to the jar (Mod Menu showed "broken icon").
