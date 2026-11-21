# Unreleased changes

Collected here until the next release, then used as the GitHub / Modrinth notes.

### Fixed
- Smooth moving and its memory savings now also work while other schematics are shown (#3). Only the moved
  schematic shifts; the others stay drawn in place. A schematic sharing a 16 x 16 chunk column with another one still
  moves the normal way (one chunk column holds the blocks of both).
- Big schematics with lots of glass, water or other see-through blocks use much less memory (#4). Litematica kept a copy
  of every piece's see-through faces for sorting them again as you move, though only pieces near you are ever sorted
  again. Now only pieces within about 50 blocks keep that copy; farther ones drop it and are rebuilt, sorted as before,
  when you come near. New setting "Less memory for see-through sorting" (on by default).
