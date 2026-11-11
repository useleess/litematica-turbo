# Unreleased changes

Collected here until the next release, then used as the GitHub / Modrinth notes.

### Fixed
- Smooth moving and its memory savings now also work while other schematics are shown (#3). Only the moved
  schematic shifts; the others stay drawn in place. A schematic sharing a 16 x 16 chunk column with another one still
  moves the normal way (one chunk column holds the blocks of both).
