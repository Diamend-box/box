# Changelog

All notable changes to **CustomAchievements** are documented here.

> This plugin was written with AI assistance (Anthropic's Claude).

## [1.9.0]
### Added
- **Material groups for block and item objectives.** An objective can now target
  a whole family of materials instead of a single one, so *"mine 100 logs"* is a
  single objective covering every wood type rather than "100 oak logs". The
  target picker lists the groups first — **Any Logs**, **Any Ores**, **Any
  Planks**, **Any Leaves**, **Any Saplings**, **Any Flowers**, **Any Crops**, any
  **Stone Types**, **Dirt & Grass**, **Sand & Gravel**, **Wool**, **Terracotta**,
  **Concrete**, **Glass**, **Coral**, **Ice**, **Mushrooms**, **Slabs**,
  **Stairs**, **Fences** and **Doors** — each showing how many materials it
  covers. In `achievements.yml` they're written with a leading `#` (e.g.
  `target: '#LOGS'`), mirroring Minecraft's own `#minecraft:logs` tag syntax.
  Groups work with `BLOCK_BREAK`, `BLOCK_PLACE`, `ITEM_CRAFT`, `ITEM_CONSUME` and
  `ITEM_OBTAIN`, and membership is derived from material names on the running
  server, so wood and ore types added by later Minecraft versions are included
  automatically. Objectives naming a single material are unchanged.

## [1.8.3]
### Changed
- **Secret achievement hints now show the full description.** Previously a hidden
  achievement revealed only the *first* line of its description as a hint, so the
  common pattern of "flavour text on line one, how-to-earn-it on line two" hid the
  part that actually tells players what to do. The hint now shows every
  description line (the mechanical objectives and progress stay concealed until
  it's unlocked), so how to earn a secret achievement is clear at a glance.
### Added
- **`secret-hint-lines` option** to control how much of a secret achievement's
  description the hint reveals: `-1` (default) shows the whole description, `0`
  shows none (name only), and a positive number caps it at the first N lines —
  e.g. set `1` to tease with just the flavour line.

## [1.8.2]
### Changed
- **Player data and `achievements.yml` are now saved atomically.** Saves write to
  a temporary file and then atomically rename it into place, so a crash, kill, or
  full disk part-way through a write can no longer leave a corrupt (half-written)
  file where a complete one used to be.
- **New config options are added automatically on upgrade.** On startup any
  options introduced by a newer version are merged into your existing
  `config.yml` (existing values and comments are left untouched), so newly-added
  settings no longer sit silently at their code defaults after an update.
### Fixed
- **Bounded the anti-farm placed-block memory.** When `count-player-placed-blocks`
  is `false`, the set of remembered placed blocks is now capped (oldest entries
  evicted first) so it can't grow without limit on build-heavy servers.
- **Stopped the action-bar progress throttle from leaking.** A player's throttle
  entries are now cleared when they disconnect instead of lingering for the
  server's lifetime.
### Internal
- The MockBukkit test suite no longer silently skips: the join path now treats a
  world's namespaced key (and the play-time statistic) as optional, as they are
  on the mock server, so the player-based behaviour tests actually execute in CI
  instead of aborting.
- Unclaimed item rewards are now persisted in Bukkit's portable serialized-map
  form instead of as a raw `ItemStack` object, so the player-data file no longer
  embeds a server-specific implementation class. Existing saves are still read.

## [1.8.1]
### Changed
- **Action-bar progress now points at the nearest goal.** When a single event
  advanced several achievements at once, the action bar could end up showing
  whichever was processed last — often the one *furthest* from completion. It now
  shows the achievement *closest* to completion (highest average requirement
  progress), so the hint always points at the most achievable next goal.

## [1.8.0]
### Added
- **`/reopen` command** (also `/ca reopen`) — reopens the last CustomAchievements
  menu you had open, so an editor closed by accident can be restored with the
  in-progress draft intact.
- **Rearrange achievements in the GUI** — in the admin manage menu, **Shift-Left**
  moves an achievement up and **Shift-Right** moves it down. The order is saved to
  `achievements.yml` and drives the player menu and `/ca list`.
- **Unclaimed-reward storage** — if a player's inventory is full when they unlock
  an achievement, item rewards are kept in a per-player storage instead of being
  dropped. Collect them with **`/ca claim`** (or `/reopen`); returning players are
  reminded on join. Toggle with `store-overflow-rewards` (defaults to `true`).
- **Secret achievement hints** — hidden achievements now show their name and a
  one-line hint (their first description line) before being unlocked, so players
  have a clue how to earn them. The objectives stay concealed. Toggle with
  `secret-show-hints` (defaults to `true`; set `false` for the old bare `???`).
### Changed
- The `/ca top` leaderboard now **cross-references each player's completions
  against the achievements that still exist**, so achievements deleted since a
  player earned them no longer inflate their total.

## [1.7.0]
### Fixed
- **Off-chat anvil editor input now actually works.** The previous version built
  the anvil with `Bukkit.createInventory(..., ANVIL, ...)`, which produces a
  non-functional anvil: `PrepareAnvilEvent` never fires and the rename text is
  never captured (PaperMC/Paper#9892), so confirming did nothing. It is now
  powered by the bundled **AnvilGUI** library (shaded and relocated), which
  opens a real anvil menu. Type in the rename field and click the result slot to
  confirm.
### Added
- If the anvil can't open on a given server build, the editor now **falls back
  to chat input automatically**, so text entry always works.
### Changed
- `use-anvil-input` still toggles the anvil off (type in chat instead); it now
  drives the AnvilGUI-backed prompt. Defaults to `true`.

## [1.6.0]
### Added
- **PlaceholderAPI support** (soft dependency) — show live, per-player
  achievement info on holograms, scoreboards, signs and chat. Placeholders:
  `%customachievements_completed%`, `_total%`, `_remaining%`, `_percent%`,
  and per-achievement `_status_<id>%`, `_progress_<id>%`, `_percent_<id>%`,
  `_name_<id>%`. Secret achievements read as `???` until unlocked.
### Changed
- **Editor delete is now a two-step confirm** — the Delete button arms on the
  first click ("Click again to DELETE") and only removes the achievement on a
  second click; clicking anything else cancels. Prevents accidental deletion.

## [1.5.1]
### Added
- `/ca info <id>` — print an achievement's details (objectives, rewards,
  category, secret flag) in chat. Reward commands are shown to admins only.

## [1.5.0]
### Added
- **Off-chat editor input** — text fields in the editor now use an **anvil**
  rename box instead of chat, so typed details never enter chat (fixes
  chat-bridge plugins like DiscordSRV relaying them). Toggle with
  `use-anvil-input` (falls back to chat when off).
- **Advancement toast** on unlock (experimental, `advancement-toast: false` by
  default). Uses runtime advancement registration and self-disables on server
  builds that don't allow it — unlocks are never affected.

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
