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

The finished plugin is written to `target/CustomAchievements-1.8.1.jar`.
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
| `ITEM_CRAFT` | Material | Items crafted |
| `ITEM_CONSUME` | Material | Items eaten/drunk |
| `FISH_CAUGHT` | – | Fish reeled in |
| `PLAYER_DEATH` | – | Deaths |
| `PLAYTIME_HOURS` | – | Hours played (from server statistics) |
| `REACH_LOCATION` | `world;x;y;z;radius` | Completes on entering the radius |
| `REACH_DIMENSION` | World name / key / environment | Times the dimension is entered |

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
advancement-toast: false    # EXPERIMENTAL native advancement-toast pop-up on unlock
use-anvil-input: true        # editor prompts use the off-chat anvil GUI (false = type in chat)
playtime-tracking: true     # enable PLAYTIME_HOURS achievements
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
```

All message strings use [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
formatting. Placeholders: `<name>` (achievement), `<player>` (player name).

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
