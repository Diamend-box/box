# CustomAchievements

A Minecraft **1.21.4** (Paper) plugin that lets server staff create fully
**custom achievements** through an in-game **GUI**, and lets every player browse
a menu of all achievements to see which ones they've **unlocked** and which are
still **locked** (with live progress bars).

> ℹ️ **Made with AI.** This plugin was written by an AI assistant (Anthropic's
> Claude) working from a human's requests, and is maintained the same way. It's
> shared here in the interest of transparency — review the code and test it on
> your own server before relying on it in production.

---

## Features

- 🎨 **GUI-based editor** – build an achievement without touching a config file.
  Set the icon, name, description, trigger, target, required amount, XP reward,
  reward commands and broadcast toggle, all by clicking.
- 📖 **Player achievement menu** – a paginated book of every achievement showing
  ✔ *Unlocked* / ✖ *Locked* and a progress bar for in-progress goals.
- ⚙️ **Automatic tracking** for many trigger types (mining, building, killing,
  crafting, eating, fishing, deaths, playtime, reaching **locations** and
  **dimensions** – including custom ones) plus **manual/command** grants.
- 🎯 **Multiple objectives** – an achievement can require several triggers at
  once; it unlocks only when every objective is complete.
- 🧭 **GUI pickers** – choose triggers and targets from searchable, paginated
  menus (no typing IDs from memory).
- 🪵 **Target groups** – aim a whole family instead of one value: "mine 100
  **logs**", "mine 50 **ores**" or "kill 100 **hostile mobs**" in a single
  objective, covering every wood/ore/mob type (including ones added by future
  Minecraft versions).
- ⌨️ **Off-chat text entry** – editor prompts use an anvil GUI so typed values
  never hit chat (keeps them away from chat-bridge plugins like DiscordSRV);
  toggle off in the config to type in chat instead.
- 🎨 **Flexible text** – names/descriptions accept classic `&` colour codes
  *and* MiniMessage, mixed freely.
- 🐉 **MythicMobs & AuraSkills** – optional soft-dependencies for mob-kill and
  skill-level achievements; the plugin runs fine without either.
- 🪧 **PlaceholderAPI** – optional expansion exposing per-player completion and
  progress placeholders for holograms, scoreboards and signs.
- 🕵️ **Secret achievements** – hidden entries reveal only their name and a
  one-line hint until unlocked (or fully hide as `???` if you prefer).
- 🗂️ **Optional categories** – group achievements into tabs (only shown once you
  actually use categories).
- ↕️ **Reorderable in the GUI** – admins shift-click achievements in the manage
  menu to move them up/down; the order is saved and drives every player's menu.
- 🏆 **Rewards** – grant XP, give **items**, and/or run console commands on unlock.
- 📦 **Never lose a reward** – if a player's inventory is full on unlock, item
  rewards go into a claimable storage (`/ca claim`) instead of being dropped.
- ↩️ **`/reopen`** – accidentally closed the editor? Reopen it right where you
  left off, draft intact.
- 📊 **Leaderboard & progress** – `/ca top` (only counts achievements that still
  exist), per-category completion %, and an optional action-bar progress readout.
- 🛡️ **Anti-farm option** – optionally ignore player-placed blocks so
  place-and-break can't farm break achievements.
- 📢 **Unlock feedback** – toast sound, on-screen title, personal message and an
  optional server-wide broadcast.
- 💾 **Per-player persistence** – progress is saved to disk and survives restarts.
- 🔌 **Simple API** – other plugins can grant achievements programmatically.

---

## Requirements

- Java **21**
- A **Paper** (or Paper-compatible, e.g. Purpur) server running **1.21.4**

> The plugin uses the Paper API (Adventure components, MiniMessage, the modern
> `AsyncChatEvent`). It is built against `io.papermc.paper:paper-api:1.21.4`.

---

## Building

```bash
mvn clean package
```

The finished plugin is written to `target/CustomAchievements-1.10.0.jar`.
Drop that jar into your server's `plugins/` folder and restart.

> The build downloads the Paper API from `https://repo.papermc.io` and the
> [AnvilGUI](https://github.com/WesJD/AnvilGUI) library from
> `https://mvn.wesjd.net`, so the build machine needs access to those
> repositories (any normal dev machine or CI runner does). AnvilGUI is shaded
> in (relocated) to power the off-chat anvil editor; MiniMessage/Adventure ship
> with Paper.

---

## Commands

Base command: `/achievements` (aliases: `/ca`, `/ach`, `/customachievements`)

| Command | Description | Permission |
| --- | --- | --- |
| `/ca` | Open **your** achievements menu | `customachievements.use` |
| `/ca list` | List achievements in chat | `customachievements.use` |
| `/ca top` | Completion leaderboard | `customachievements.use` |
| `/ca claim` | Collect reward items that didn't fit in your inventory | `customachievements.use` |
| `/reopen` (or `/ca reopen`) | Reopen the last menu you had open | `customachievements.use` |
| `/ca admin` | Open the **management** menu (edit / create) | `customachievements.admin` |
| `/ca create` | Open the editor to build a new achievement | `customachievements.admin` |
| `/ca grant <player> <id>` | Grant an achievement (online or offline) | `customachievements.admin` |
| `/ca revoke <player> <id>` | Revoke an achievement (online or offline) | `customachievements.admin` |
| `/ca reset <player>` | Reset a player's achievements (online or offline) | `customachievements.admin` |
| `/ca reload` | Reload config + achievements from disk | `customachievements.admin` |

### Permissions

| Node | Default | Grants |
| --- | --- | --- |
| `customachievements.use` | everyone | Viewing your own achievements |
| `customachievements.admin` | ops | Creating, editing and granting achievements |

---

## Creating an achievement (GUI walkthrough)

1. Run `/ca admin` and click **+ Create New Achievement** (or run `/ca create`).
2. The **editor** opens. The top row holds the achievement's presentation and
   rewards; each item is a clickable field:
   - **Identifier** – the unique key (only editable while creating).
   - **Icon** – click while holding an item to use it, or click empty-handed to
     type a material name.
   - **Display Name** – click to type in chat (`&`-codes and MiniMessage).
   - **Description** – left-click to rewrite it (use `|` to split lines),
     right-click to add a line, shift-right-click to remove the last line.
   - **Broadcast on Unlock** – click to toggle.
   - **Reward XP** – click to type the amount.
   - **Reward Commands** – left-click to add one (use `%player%`), right-click to
     clear.
3. Below that is the **Objectives** area. Click **+ Add Objective** (or an
   existing one) to open the objective editor, where you pick:
   - **Trigger** – click to open the **trigger picker** (a menu of all triggers).
   - **Target** – click to open the **target picker**, a paginated, searchable
     grid of the relevant options (materials / entities / skills / dimensions).
     For blocks, items and mobs the list starts with **groups** — "Any Logs",
     "Any Ores", "Any Hostile Mobs", … — so an objective can cover a whole family
     at once (see [Target groups](#target-groups-mine-100-logs-kill-100-hostile-mobs)).
     Special cases: *Reach a Location* captures your current position on
     left-click (or type `world x y z [radius]`); *Kill Mythic Mobs* is typed.
   - **Required Amount** – click to type a number.
   Shift-right-click an objective tile to remove it. An achievement unlocks only
   when **all** its objectives are complete.
4. Click **Save**. The achievement is written to `achievements.yml` immediately
   and appears in every player's menu.

To edit an existing achievement, open `/ca admin` and click its icon. To delete
one, open it in the editor and click the **Delete** button, then click it again
to confirm (a two-step guard so you can't wipe an achievement by accident).

**Reordering:** in the `/ca admin` menu, **shift-left-click** an achievement to
move it up and **shift-right-click** to move it down. The new order is saved to
`achievements.yml` and is the order everyone sees in their menu and `/ca list`.

**Accidentally closed the editor?** Run **`/reopen`** (or `/ca reopen`) to bring
it back exactly where you left off — the in-progress draft is preserved.

---

## Trigger types

| Trigger | Uses target? | Counts... |
| --- | --- | --- |
| `MANUAL` | – | Only granted by command/API |
| `BLOCK_BREAK` | Material | Blocks broken |
| `BLOCK_PLACE` | Material | Blocks placed |
| `ENTITY_KILL` | EntityType | Mobs killed |
| `MYTHIC_MOB_KILL` | MythicMobs internal name | MythicMobs mobs killed |
| `AURASKILLS_LEVEL` | AuraSkills skill (or ANY) | Reach a skill level |
| `ITEM_CRAFT` | Material or custom name | Items crafted |
| `ITEM_CONSUME` | Material or custom name | Items eaten/drunk |
| `ITEM_OBTAIN` | Material or custom name | Items received — running total |
| `ITEM_HAVE` | Material or custom name | Items held **right now** (live count) |
| `FISH_CAUGHT` | – | Fish reeled in |
| `PLAYER_DEATH` | Damage cause, mob, or ANY | Deaths (optionally to a specific cause) |
| `PLAYTIME_HOURS` | – | Hours played (from server statistics) |
| `REACH_LOCATION` | `world;x;y;z;radius` | Completes on entering the radius |
| `REACH_DIMENSION` | World name / key / environment | Times the dimension is entered |

### Counting items: `ITEM_OBTAIN` vs `ITEM_HAVE`

Both match either a **material** or a **custom item name** — flip **"Match by:
Custom Name"** in the objective editor and type the name (e.g. *"Ancient
Coin"*), and the material stops mattering.

They differ in what they count:

- **`ITEM_OBTAIN`** is a **running total** of items received: picked up off the
  ground, taken out of a chest, pulled from a furnace or trade result. It never
  goes down when you spend them. It can't see items handed over by `/give` or a
  plugin, because Minecraft fires no event for those.
- **`ITEM_HAVE`** is a **live count of what's in the inventory**. It catches
  every source — including `/give`, plugin grants and creative mode — but the
  count drops again if you spend, drop or store the items. Once the objective is
  actually completed the achievement is awarded for good.

So "collect 500 diamonds over time" wants `ITEM_OBTAIN`; "hold 64 Ancient Coins
at once" wants `ITEM_HAVE`. `ITEM_HAVE` refreshes on pickup, when you close a
container, and on a short sweep (~10s) that catches the eventless sources; the
sweep is skipped entirely if no achievement uses the trigger.

### Deaths by cause

`PLAYER_DEATH` counts deaths, and its target narrows *how* you died. It matches
against **either** the damage cause **or** whatever killed you, so both styles
work:

| Target | Means |
| --- | --- |
| `ANY` (or unset) | Any death at all |
| `LAVA`, `FALL`, `DROWNING`, `VOID`, `FIRE`, `FREEZE`, … | A specific damage cause |
| `CREEPER`, `ZOMBIE`, `PLAYER`, … | Killed by that mob |
| `#HOSTILE`, `#UNDEAD`, `#BOSSES`, … | Killed by any mob in that family |

Matching the killer separately matters because the damage *cause* of a creeper
kill is just `ENTITY_EXPLOSION` — the mob has to be checked on its own. Arrows
and other projectiles resolve to **whoever fired them**, so a skeleton's arrow
counts as a skeleton kill, not an arrow.

### Credit for work done before the achievement existed

Add a "kill 200 players" achievement to a server where someone already has 150
kills and they start at **150/200**, not 0. On join (and the moment a new
achievement is saved) each objective with no progress yet is seeded from
Minecraft's own lifetime statistics.

Seeded from statistics:

| Objective | Statistic used |
| --- | --- |
| `ENTITY_KILL` a mob / a family / `ANY` | kills of that type, summed for a family, or total mob kills |
| `ENTITY_KILL` targeting `PLAYER` | players killed |
| `BLOCK_BREAK` | blocks mined |
| `BLOCK_PLACE`, `ITEM_CONSUME` | items used |
| `ITEM_CRAFT` | items crafted |
| `ITEM_OBTAIN` | items picked up |
| `FISH_CAUGHT` | fish caught |
| `PLAYER_DEATH` with no cause | total deaths |

Not seeded (Minecraft keeps no statistic for them), so these start at zero:
objectives matching a **custom item name**, `PLAYER_DEATH` with a **specific
cause**, `ITEM_HAVE`, `REACH_LOCATION` / `REACH_DIMENSION`, `MYTHIC_MOB_KILL`,
`AURASKILLS_LEVEL` and `MANUAL`. `PLAYTIME_HOURS` is already read live from the
server's playtime statistic, so it needs no backfill.

An objective is only ever seeded **while it is still at zero** — once it's
ticking, live events own it — so the backfill can run any number of times
without double-counting.

> ⚠️ Players may **immediately complete** achievements they had already earned
> the statistics for, which pays out rewards and fires broadcasts. That's the
> point, but on a long-running server the first join after adding achievements
> can unlock several at once. Set `backfill-from-statistics: false` if you'd
> rather everyone started fresh.

### Multiple objectives

An achievement can have **several objectives** (triggers). It unlocks only once
**all** of them are complete, and each objective tracks its own progress (shown
per-line in the menu). Add/edit objectives from the editor, or in
`achievements.yml` under a `requirements:` list.

### Text formatting

Names and descriptions accept **both** classic colour codes (`&a`, `&l`,
`&#ff8800`) **and** [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
(`<green>`, `<bold>`, `<gradient:…>`) — you can mix them.

### Editor text entry (anvil vs chat)

When the editor asks you to type something (a name, a number, a command…), it
opens a small **anvil GUI**: type into the rename field and click the right-hand
result slot to confirm, or close the menu to cancel. Nothing you type reaches
chat, so chat-bridge plugins (DiscordSRV, etc.) never relay editor input. This
is powered by the bundled [AnvilGUI](https://github.com/WesJD/AnvilGUI) library.

Prefer the old behaviour? Set `use-anvil-input: false` in `config.yml` to type
in chat instead. Either way, if the anvil can't open on your server build the
editor falls back to chat automatically, so it always works.

### Secret achievements

Mark an achievement **Secret** in the editor to keep it a surprise. By default
(`secret-show-hints: true`) a locked secret still shows its **name** and its
**first description line** as a hint, so players know it exists and roughly how
to earn it — but its objectives stay hidden until they unlock it. Prefer them
completely opaque? Set `secret-show-hints: false` and locked secrets render as a
bare obfuscated `???` instead.

### Reward items & the claim storage

Item rewards are added straight to the player's inventory on unlock. If their
inventory is **full**, the leftover items are kept in a per-player **claim
storage** rather than dropped on the ground where they could despawn. Players
collect them with **`/ca claim`** (drag items out, or hit **Claim All**), and are
reminded on join if anything is waiting. Set `store-overflow-rewards: false` to
go back to dropping overflow at the player's feet.

### AuraSkills

If [AuraSkills](https://wiki.aurelium.dev/auraskills) is installed,
`AURASKILLS_LEVEL` objectives complete when a player reaches the required level
in the chosen skill (or ANY skill). Soft dependency — the plugin runs fine
without it.

`target: ANY` (or a blank target) matches everything for that trigger
(except `REACH_LOCATION`, which always needs a concrete location).

### Target groups (mine 100 *logs*, kill 100 *hostile mobs*)

Objectives can target a **whole family** instead of one specific value, so
"mine 100 logs", "mine 50 ores" or "kill 100 hostile mobs" is a single
objective. In the target picker the groups are listed **first**, named
**"Any Logs"**, **"Any Ores"**, **"Any Hostile Mobs"**, and so on — pick one and
you're done. Each shows how many types it currently covers.

In `achievements.yml` a group is written with a leading `#`, mirroring
Minecraft's own `#minecraft:logs` tag syntax:

```yaml
target: '#LOGS'   # any log, of any wood type
amount: 100
```

**Material groups** — for `BLOCK_BREAK`, `BLOCK_PLACE`, `ITEM_CRAFT`,
`ITEM_CONSUME` and `ITEM_OBTAIN`:

| Group | Covers |
| --- | --- |
| `#LOGS` | Every log, stem, hyphae and wood block — all tree types, stripped or not (plus bamboo blocks) |
| `#ORES` | Every ore, including deepslate and nether variants, plus ancient debris |
| `#PLANKS` | Any wooden planks |
| `#LEAVES` | Any tree leaves |
| `#SAPLINGS` | Any sapling or propagule |
| `#FLOWERS` | Any flower, small or tall |
| `#CROPS` | Wheat, carrots, potatoes, beetroot, nether wart, melon, pumpkin, sugar cane, bamboo, cactus, berries, kelp |
| `#STONES` | Naturally generated stone — stone, deepslate, tuff, basalt, netherrack, obsidian … |
| `#DIRT` | Dirt, grass, podzol, mycelium, farmland, mud, soul sand … |
| `#SAND` | Sand, red sand and gravel (incl. suspicious variants) |
| `#WOOL` | Any colour of wool |
| `#TERRACOTTA` | Any terracotta, glazed or plain |
| `#CONCRETE` | Any colour of concrete (powder not included) |
| `#GLASS` | Any glass block or pane, stained or not |
| `#CORAL` | Any coral, coral block or coral fan |
| `#ICE` | Ice, packed ice, blue ice, frosted ice |
| `#MUSHROOMS` | Mushrooms and mushroom blocks |
| `#SLABS` / `#STAIRS` / `#FENCES` / `#DOORS` | Any slab / stairs / fence or gate / door or trapdoor |

**Mob groups** — for `ENTITY_KILL`:

| Group | Covers |
| --- | --- |
| `#HOSTILE` | Anything that attacks you — zombies, creepers, slimes, ghasts, bosses … |
| `#ANIMALS` | Farm and wild animals — cows, pigs, sheep, wolves, horses, bees … |
| `#UNDEAD` | Everything that takes extra damage from Smite — zombies, skeletons, drowned, phantoms, wither … |
| `#ARTHROPODS` | Spiders, cave spiders, silverfish, endermites, bees |
| `#ILLAGERS` | The raid roster — pillagers, vindicators, evokers, ravagers, witches, vexes |
| `#BOSSES` | Ender dragon, wither, warden, elder guardians |
| `#AQUATIC` | Fish, squid, dolphins, axolotls, turtles, guardians, drowned |
| `#NETHER` | Blazes, ghasts, magma cubes, piglins, hoglins, striders, wither skeletons |
| `#END` | Endermen, endermites, shulkers, the ender dragon |

Membership is worked out on the running server — from the material's **name**
for material groups, and from the mob's own **Bukkit category** (`Enemy`,
`Animals`, `Raider`, `Boss`, …) for the mob groups where vanilla models one — so
**blocks and mobs added by future Minecraft versions are picked up
automatically** without a plugin update.

Groups are scoped to the triggers they make sense for: `#HOSTILE` on a
`BLOCK_BREAK` objective (or `#LOGS` on a kill objective) matches nothing rather
than silently matching everything. Objectives that name a single value are
unaffected — `target: OAK_LOG` still means only oak logs.

### Location & dimension targets

- **`REACH_LOCATION`** stores its target as `world;x;y;z;radius` (the editor
  fills this in for you). The achievement completes the moment a player is
  inside that sphere — walking, teleporting, or logging in there all count.
- **`REACH_DIMENSION`** matches the world a player just entered against the
  target in three ways, so **custom dimensions work out of the box**:
  - the world's *name* (`world_nether`, `spawn`, `skyblock_world`, ...)
  - the world's *namespaced key* (`minecraft:the_nether`, `mypack:skylands`, ...
    — this is how datapack/plugin dimensions are addressed)
  - the world's *environment*: `NORMAL`, `NETHER`, `THE_END` or `CUSTOM`
    (`NETHER` matches every nether-type world, however it's named).

### MythicMobs

If [MythicMobs](https://mythiccraft.io/) is installed, `MYTHIC_MOB_KILL`
achievements trigger when a player kills a mob whose **internal name** (the id
used in your MythicMobs `Mobs/*.yml` config, e.g. `SkeletalKnight`) matches the
target. `ANY` matches every MythicMobs kill. The integration is a soft
dependency wired up via reflection — the plugin loads and runs fine without
MythicMobs, and both MythicMobs **5.x** and **4.x** are supported. Regular
`ENTITY_KILL` achievements still count MythicMobs kills by their base entity
type.

### PlaceholderAPI

If [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) is
installed, the plugin registers an expansion so you can show live, per-player
achievement info on holograms, scoreboards, signs and in chat. It's a soft
dependency — the plugin runs fine without it.

| Placeholder | Shows |
|---|---|
| `%customachievements_completed%` | how many the player has unlocked |
| `%customachievements_total%` | number of achievements |
| `%customachievements_remaining%` | not-yet-unlocked count |
| `%customachievements_percent%` | overall completion (0–100) |
| `%customachievements_status_<id>%` | `Unlocked`, `Locked`, or `???` (secret) |
| `%customachievements_progress_<id>%` | e.g. `7/10`, or `Complete` |
| `%customachievements_percent_<id>%` | that achievement's progress (0–100) |
| `%customachievements_name_<id>%` | its display name (coloured; `???` while secret) |

Replace `<id>` with an achievement's identifier, e.g.
`%customachievements_progress_getting_wood%`. This pairs well with a hologram at
spawn: the placeholders update per viewer automatically, so each player sees
their own progress. (Ids may contain underscores — that's fine.)

---

## Configuration (`config.yml`)

```yaml
announce-broadcasts: true   # server-wide message on unlock (per-achievement toggle also applies)
play-sound: true            # play the challenge-complete sound
show-title: true            # show an on-screen title
show-description-on-unlock: true  # print the achievement's description in chat on unlock
advancement-toast: false    # EXPERIMENTAL native advancement-toast pop-up on unlock
use-anvil-input: true        # editor prompts use the off-chat anvil GUI (false = type in chat)
playtime-tracking: true     # enable PLAYTIME_HOURS achievements
backfill-from-statistics: true  # credit work done before an achievement existed
progress-feedback: true     # action-bar progress readout as players advance
secret-show-hints: true     # secret achievements reveal name + 1-line hint (false = bare "???")
store-overflow-rewards: true  # keep reward items for /ca claim when the inventory is full (false = drop)
count-player-placed-blocks: true  # false = don't count breaking blocks you placed
autosave-minutes: 5         # periodic save of online players (0 to disable)

messages:
  prefix: "..."
  unlocked: "<green>You unlocked <name>!"
  broadcast: "<yellow><player></yellow> unlocked <white><name></white>!"
  title: "<gold>Achievement Unlocked"
  subtitle: "<name>"     # the line under the title; try "<name> <dark_gray>— <description>"
```

All message strings use [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
formatting. Placeholders: `<name>` (achievement), `<description>` (its
description, all lines joined onto one) and `<player>` (player name, broadcast
only).

### What a player sees on unlock

The on-screen title has room for exactly two lines — the big **"Achievement
Unlocked"** banner and the subtitle under it — so the achievement's description
is printed in **chat** instead, one line per line, under the unlock message.
That way a description written as "flavour text, then how you earn it" is fully
visible at the moment it's unlocked. Turn it off with
`show-description-on-unlock: false`, or move it on-screen by setting
`messages.subtitle` to include `<description>`.

Reward commands support `%player%` and `%uuid%`.

---

## Data & files

```
plugins/CustomAchievements/
├── config.yml            # general settings & messages
├── achievements.yml      # every achievement definition (edited by the GUI)
└── playerdata/
    └── <uuid>.yml        # each player's completed list + progress
```

On first run six example achievements are created so you have something to look
at (Getting Wood, Diamonds Forever, Monster Hunter, Hot Tourist, Veteran, Well Prepared).

---

## For developers

Grant an achievement from another plugin:

```java
CustomAchievementsPlugin ca =
        (CustomAchievementsPlugin) Bukkit.getPluginManager().getPlugin("CustomAchievements");
Achievement achievement = ca.getAchievementManager().get("diamonds_forever");
if (achievement != null) {
    ca.getAchievementService().grant(player, achievement);
}
```
