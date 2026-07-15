# CustomAchievements

A Minecraft **1.21.4** (Paper) plugin that lets server staff create fully
**custom achievements** through an in-game **GUI**, and lets every player browse
a menu of all achievements to see which ones they've **unlocked** and which are
still **locked** (with live progress bars).

---

## Features

- 🎨 **GUI-based editor** – build an achievement without touching a config file.
  Set the icon, name, description, trigger, target, required amount, XP reward,
  reward commands and broadcast toggle, all by clicking.
- 📖 **Player achievement menu** – a paginated book of every achievement showing
  ✔ *Unlocked* / ✖ *Locked* and a progress bar for in-progress goals.
- ⚙️ **Automatic tracking** for many trigger types (mining, building, killing,
  crafting, eating, fishing, deaths, playtime) plus **manual/command** grants.
- 🏆 **Rewards** – grant XP and/or run console commands on unlock.
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

The finished plugin is written to `target/CustomAchievements-1.0.0.jar`.
Drop that jar into your server's `plugins/` folder and restart.

> The build downloads the Paper API from `https://repo.papermc.io`, so the build
> machine needs access to that repository (any normal dev machine or CI runner
> does). No other third-party libraries are shaded in — MiniMessage/Adventure
> ship with Paper.

---

## Commands

Base command: `/achievements` (aliases: `/ca`, `/ach`, `/customachievements`)

| Command | Description | Permission |
| --- | --- | --- |
| `/ca` | Open **your** achievements menu | `customachievements.use` |
| `/ca list` | List achievements in chat | `customachievements.use` |
| `/ca admin` | Open the **management** menu (edit / create) | `customachievements.admin` |
| `/ca create` | Open the editor to build a new achievement | `customachievements.admin` |
| `/ca grant <player> <id>` | Grant an achievement to an online player | `customachievements.admin` |
| `/ca revoke <player> <id>` | Revoke an achievement | `customachievements.admin` |
| `/ca reset <player>` | Reset all of a player's achievements | `customachievements.admin` |
| `/ca reload` | Reload config + achievements from disk | `customachievements.admin` |

### Permissions

| Node | Default | Grants |
| --- | --- | --- |
| `customachievements.use` | everyone | Viewing your own achievements |
| `customachievements.admin` | ops | Creating, editing and granting achievements |

---

## Creating an achievement (GUI walkthrough)

1. Run `/ca admin` and click **+ Create New Achievement** (or run `/ca create`).
2. The **editor** opens. Each item is a field you can click:
   - **Identifier** – the unique key (only editable while creating).
   - **Icon** – click while holding an item to use it, or click empty-handed to
     type a material name.
   - **Display Name** / **Description** – click to type in chat (MiniMessage
     colours supported; use `|` to split the description into multiple lines).
   - **Trigger** – left-click for the next type, right-click for the previous.
   - **Target** – the block / item / entity to match, or `ANY`.
   - **Required Amount** – left/right click ±1, shift-click ±10.
   - **Broadcast on Unlock** – click to toggle.
   - **Reward XP** – left/right click ±10, shift-click ±100.
   - **Reward Commands** – left-click to add one (use `%player%`), right-click to
     clear.
3. Click **Save**. The achievement is written to `achievements.yml` immediately
   and appears in every player's menu.

To edit an existing achievement, open `/ca admin` and click its icon. To delete
one, open it in the editor and **shift-click** the Delete button.

---

## Trigger types

| Trigger | Uses target? | Counts... |
| --- | --- | --- |
| `MANUAL` | – | Only granted by command/API |
| `BLOCK_BREAK` | Material | Blocks broken |
| `BLOCK_PLACE` | Material | Blocks placed |
| `ENTITY_KILL` | EntityType | Mobs killed |
| `ITEM_CRAFT` | Material | Items crafted |
| `ITEM_CONSUME` | Material | Items eaten/drunk |
| `FISH_CAUGHT` | – | Fish reeled in |
| `PLAYER_DEATH` | – | Deaths |
| `PLAYTIME_MINUTES` | – | Minutes played |

`target: ANY` (or a blank target) matches everything for that trigger.

---

## Configuration (`config.yml`)

```yaml
announce-broadcasts: true   # server-wide message on unlock (per-achievement toggle also applies)
play-sound: true            # play the challenge-complete sound
show-title: true            # show an on-screen title
playtime-tracking: true     # enable PLAYTIME_MINUTES achievements
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

On first run four example achievements are created so you have something to look
at (Getting Wood, Diamonds Forever, Monster Hunter, Veteran).

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
