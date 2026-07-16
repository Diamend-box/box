# Changelog

All notable changes to **CustomAchievements** are documented here.

## [1.4.0]
### Added
- **`ITEM_OBTAIN` trigger** — count items a player picks up.
- **Match-by-name** for item objectives (craft / consume / obtain): toggle
  "Match by: Custom Name" in the objective editor to match a custom item's
  display name (e.g. *"Compressed Iron Ingots"*) instead of its material.
- **Coordinate/height thresholds** for *Reach a Location*: enter something like
  `Y>319` (optionally `world;Y>=319`) to complete when a single coordinate is
  above/below a value — e.g. reaching the build limit.

## [1.3.0]
### Added
- Secret / hidden achievements (shown as `???` until unlocked).
- Item rewards (drop-in editor menu; items granted on unlock).
- Optional categories with a tab picker (shown only when categories are used).
- `/ca top` completion leaderboard and per-category completion %.
- Action-bar progress feedback (config `progress-feedback`).
- Anti-farm option (`count-player-placed-blocks: false`).
- Duplicate/clone button in the editor.
- AuraSkills current-level sync on join.
- Offline `grant` / `revoke` / `reset`.
### Changed
- Player data now saves off the main thread.

## [1.2.1]
### Changed
- Editor wording: description "append" is now "add a line".

## [1.2.0]
### Added
- `&` / `§` colour codes (incl. `&#hex`) alongside MiniMessage.
- Playtime measured in **hours** (from the server statistic).
- **AuraSkills** `AURASKILLS_LEVEL` trigger (soft dependency).
- Full GUI pickers for triggers and targets (searchable), typed amounts, and an
  in-GUI multi-objective editor.

## [1.1.0]
### Added
- Multiple objectives per achievement (all must be completed).
- `REACH_LOCATION` and `REACH_DIMENSION` triggers (custom dimensions supported).
- **MythicMobs** `MYTHIC_MOB_KILL` trigger (soft dependency).

## [1.0.0]
- Initial release: GUI-based custom achievements, a paginated player menu with
  progress, automatic trigger tracking, rewards, and per-player persistence.
