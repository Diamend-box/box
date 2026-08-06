# Changelog — BoxCore

All notable changes to **BoxCore** are documented here.

> This plugin was written with AI assistance (Anthropic's Claude).

Versions below are reconstructed from the module's own history: each one is a
feature wave, not a published release.

## [1.4.0]
### Added
- **Fast travel** — destinations you unlock by walking into them, rather than a
  list of warps handed out up front.
- `/fasttravel`, with `/fastravel` and `/ft` as aliases.
- An in-game editor for destinations, so they can be placed and renamed without
  touching a config file.
### Fixed
- The loose ends named by the module audit.

## [1.3.0]
### Changed
- **The compressor is now a personal compactor** — it belongs to the player
  rather than to a block, which is what people expected it to be.
### Added
- An in-game editor for compaction recipes.
- Unlocks, gates and an on/off toggle for the compactor, with a front end for
  all three.
### Fixed
- Null-guarded the "nothing opened" assertion.

## [1.2.0]
### Added
- **The boosts module** — player and global multipliers.
- A boosts menu, a live countdown and an expiry warning.
### Changed
- Placed-block flags are kept in the chunk rather than in memory, so they
  survive a restart and stop growing without bound.
### Fixed
- Schedule fields are read without `getOrDefault` on a wildcard map.

## [1.1.0]
### Added
- **The compressor**: an auto-compressor, per-ore custom compressed items, and
  a shared ore-value accessor other modules can read.
- `/box give` for compressed ore.
- Colour codes documented in the skins config.
### Fixed
- Expanding reads the held stack before emptying it.
- Items are placed one at a time instead of trusting stack merging.
- Inventory slots are written one at a time rather than wholesale.
- Tests report the real cause when a command throws.

## [1.0.0]
Initial release: a modular utility plugin for the Box PvP server.

### Added
- **Skill trees** and **collections**, with a finite, visible point economy.
- Perks retuned for a boxpvp server, and the trees grown to 44 nodes.
### Fixed
- Node levels are no longer lost on save; the timer refreshes only potions.
- A collection's paid-tier marker never moves backwards.
- Profile writes are atomic and never read around a queued one.
- Attribute prefix-stripping no longer mangles perk names.
- Perk loot is kept out of collection progress.
- The playtime collection is capped at 100 hours.
