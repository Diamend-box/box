# Changelog

All notable changes to **CustomAchievements** are documented here.

> This plugin was written with AI assistance (Anthropic's Claude).

## [1.13.0]
### Added
- **`ACHIEVEMENT_UNLOCK` — an achievement for earning achievements.** A capstone
  can now require "unlock 20 achievements", or "unlock 20 in Mining" by naming a
  category as its target. The count is read from what the player has actually
  unlocked rather than accumulated as it happens, so it needs no backfill, it
  credits everything earned before the capstone existed, and it follows a revoke
  back down. Capstones stack: unlocking one is itself an unlock.

### Fixed
- **A version that learns to read a new statistic now reaches the players it was
  for.** An objective is marked seeded even when the read comes back empty — so
  when 1.12.0 taught the backfill to total "any block", every player who had
  already joined was shut out of it by a marker set when there was no answer to
  give, and their block totals stayed at zero unless an admin ran
  `/ca backfill <player> redo`. The marker now records which version of the
  reader set it, so gaining an answer re-examines every objective once on the
  next join. Seeding only ever raises progress, so the retry costs nothing.

## [1.12.0]
### Added
- **"Break any 10,000 blocks" now credits the blocks you'd already broken.** An
  objective targeting `ANY` had nothing to seed from, because Minecraft keeps no
  overall "blocks mined" counter — it counts one row per block. Those rows are
  now added together, so a player with 4,000 stone and 1,500 dirt starts at
  5,500 rather than 0. The same goes for placing any block, crafting any item
  and picking up any item. `ITEM_CONSUME` targeting `ANY` is deliberately left
  alone: the "items used" statistic counts blocks placed and tools swung as
  well, so its total isn't the number that objective asks for.

## [1.11.0]
### Added
- **`CUSTOM` trigger — drive achievements from Skript, other plugins, command
  blocks and datapacks.** An objective can now listen for a **key you invent**
  (`boss_kill`, `quest:step3`, …) instead of a Minecraft value, fired with
  `/ca trigger <player> <key> [amount]`. Because it's an ordinary console
  command, anything that can run one can advance an achievement with no API and
  nothing to compile against — including plugins that run reward commands
  (MythicMobs skills, quest and crate plugins, ExecutableItems, Citizens).
  `/ca trigger <player> <key> set <value>` sets an absolute value instead of
  adding, for scripts that already keep their own total. Keys are matched
  case-insensitively, and a target of `ANY` matches every custom key.
- **A small Java API** (`CustomAchievementsAPI`) for plugins that would rather
  call directly than run a command: `trigger`, `set`, `hasCompleted`, `grant`
  and `isAvailable`. Every call is a no-op when the plugin isn't installed, so
  soft-dependants don't have to guard each one.
- **`/ca backfill [player] [redo]` — find out why a statistic didn't show up.**
  Re-runs the seeding for a player and prints, per unfinished objective, the
  statistic value it actually read and whether that was credited, already
  seeded, unreadable or simply zero. Because an objective is marked seeded even
  when the read comes back empty, a first run that found nothing is never
  retried on later joins; `redo` forces a fresh re-seed of every unfinished
  objective, which is how to recover from that. Seeding still never lowers
  progress, so forcing it is safe.

## [1.10.0]
### Added
- **Achievements now credit what you'd already done before they existed.** Add a
  "kill 200 players" achievement to a server where someone already has 150 kills
  and they start at 150/200 rather than 0. On join — and the moment a new
  achievement is saved — every objective with no progress yet is seeded from
  Minecraft's own lifetime statistics: blocks mined and placed, items crafted
  and picked up, mobs and players killed (including whole mob families, summed),
  fish caught and deaths. Objectives the server keeps no statistic for start at
  zero as before: custom item names, a death with a specific cause, `ITEM_HAVE`,
  locations, dimensions, MythicMobs and AuraSkills. Each objective is seeded
  once per player and seeding never lowers progress, so this never
  double-counts — and because that's recorded per player rather than inferred
  from "has no progress yet", testing a new achievement by going and scoring a
  kill doesn't cost you the ones you already had. Toggle with
  `backfill-from-statistics` (default `true`) — note that players may
  immediately complete achievements they'd already earned, rewards included.
- **Deaths can now require a cause.** `PLAYER_DEATH` used to count *any* death;
  its target now narrows how you died, matching against either the damage cause
  (`LAVA`, `FALL`, `DROWNING`, `VOID`, `FREEZE`, …) **or** whatever killed you
  (`CREEPER`, `ZOMBIE`, or a whole family like `#HOSTILE`). Both are checked
  because the damage cause of a creeper kill is only `ENTITY_EXPLOSION` — the
  mob has to be matched separately — and projectiles resolve to whoever fired
  them, so a skeleton's arrow counts as a skeleton. Existing death objectives
  keep counting every death: an unset target (or `ANY`) is still a wildcard.
- **`ITEM_HAVE` trigger — "have X of an item right now".** A live count of the
  player's inventory rather than a running total, so it sees items that arrive
  with no event at all: `/give`, plugin grants, creative mode. Like the other
  item triggers it can match a **custom item name** instead of a material, which
  is the usual way to track a named quest item or currency. It refreshes on
  pickup, on closing a container, and on a periodic sweep that is skipped
  entirely while no achievement uses the trigger.
### Changed
- **`ITEM_OBTAIN` now counts items taken out of containers**, not just items
  picked up off the ground — chests, barrels, furnace output, villager trades
  and loot all count toward it now. Rearranging your own inventory or crafting
  grid still doesn't, and neither does clicking through this plugin's own menus.

## [1.9.0]
### Fixed
- **Unlocking an achievement now shows its whole description, not just its
  name.** The unlock title has room for the name only, and the description
  wasn't shown anywhere at that moment — so the common "flavour text on line
  one, how-to-earn-it on line two" pattern never reached the player when it
  mattered most. The description's lines are now printed in chat under the
  unlock message (toggle with `show-description-on-unlock`, default `true`).
### Added
- **Target groups — aim an objective at a whole family instead of one value.**
  *"Mine 100 logs"* or *"kill 100 hostile mobs"* is now a single objective
  rather than one per wood type or mob. The target picker lists the groups
  first, each showing how many types it covers, and in `achievements.yml`
  they're written with a leading `#` (e.g. `target: '#LOGS'`), mirroring
  Minecraft's own `#minecraft:logs` tag syntax.
  - **Material groups** (for `BLOCK_BREAK`, `BLOCK_PLACE`, `ITEM_CRAFT`,
    `ITEM_CONSUME`, `ITEM_OBTAIN`): **Logs**, **Ores**, **Planks**, **Leaves**,
    **Saplings**, **Flowers**, **Crops**, **Stone Types**, **Dirt & Grass**,
    **Sand & Gravel**, **Wool**, **Terracotta**, **Concrete**, **Glass**,
    **Coral**, **Ice**, **Mushrooms**, **Slabs**, **Stairs**, **Fences**,
    **Doors**.
  - **Mob groups** (for `ENTITY_KILL`): **Hostile Mobs**, **Animals**,
    **Undead**, **Arthropods**, **Illagers & Raiders**, **Bosses**, **Aquatic
    Mobs**, **Nether Mobs**, **End Mobs**.

  Membership is resolved on the running server — from the material's name, and
  from the mob's own Bukkit category (`Enemy`, `Animals`, `Raider`, `Boss`, …)
  where vanilla models one — so blocks and mobs added by later Minecraft
  versions are included automatically. Groups are scoped to the triggers they
  suit, so a mob family on a block objective matches nothing rather than
  everything. Objectives naming a single value are unchanged.
- **`messages.subtitle` option** for the line under the big "Achievement
  Unlocked" title. It defaults to `<name>` (what it always showed) and now also
  accepts `<description>`, so the on-screen popup can read
  `<name> <dark_gray>— <description>` if you want the hint there too.
  `<description>` works in `messages.unlocked` and `messages.broadcast` as well.

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
