# RoboBear — the operator's guide

Everything you need to install RoboBear, point it at your mines, decide what it
pays, and tune it once players get their hands on it.

The [README](README.md) explains *why* the plugin is shaped the way it is. This
document is about *running* it. When you're ready to put it in front of players,
the [playtest script](../docs/robobear-playtest.md) is the checklist for the
first real session — including the numbers to bring back so the balance can be
set from evidence rather than guesswork. The
[changelog](CHANGELOG.md) is what changed in each release, and what to do about
it when upgrading.

---

## Contents

1. [What players actually do](#1-what-players-actually-do)
2. [Install it in five minutes](#2-install-it-in-five-minutes)
3. [Step one: mines](#3-step-one-mines)
4. [Step two: payouts](#4-step-two-payouts)
5. [Step three: passes](#5-step-three-passes)
6. [Command reference](#6-command-reference)
7. [Permissions](#7-permissions)
8. [Tuning, in the order that matters](#8-tuning-in-the-order-that-matters)
9. [Placeholders](#9-placeholders)
10. [Files on disk](#10-files-on-disk)
11. [Troubleshooting](#11-troubleshooting)
12. [If you run the BoxPvP risk spec](#12-if-you-run-the-boxpvp-risk-spec)
13. [A blurb you can paste to players](#13-a-blurb-you-can-paste-to-players)

---

## 1. What players actually do

A **run** is a ladder of timed rounds. It goes:

```
 spend a pass  →  get 10 Cogs  →  ┌─ pick 1 of 2 jobs ────────────┐
                                  │                               │
                                  │   beat the 5-minute clock     │
                                  │            ↓                  │
                                  │   get paid in Cogs            │
                                  │            ↓                  │
                                  │   spend them in the workshop  │
                                  │            ↓                  │
                                  └── next round, harder ─────────┘
                                               ↓
                        clear round 5, 10, 15… → a real payout, kept for good
                                               ↓
                          retire whenever, or miss a clock and lose the rest
```

Three things end a run: the clock runs out, the player dies, or they log out.
**Payouts already taken are never clawed back** — that's the whole tension.
Every round is a fresh bet on "one more".

While a round's clock is running, the challenge sends mobs after the player.
Only that player can see them and only that player can be hurt by them, and
dying to one ends the run like any other death. They're cleared between rounds,
so the workshop is a breather. See §8.6.

Cogs are the in-run currency. They are minted at the start, spent in the
workshop, and **destroyed when the run ends**. Nothing carries over. The only
thing that leaves a run is what you put in the payout boxes.

---

## 2. Install it in five minutes

**Requirements:** Paper 1.21.4+ (or a fork of it), Java 21.
**Optional:** MineResetLite (mines), PlaceholderAPI (scoreboard/holo variables).
Neither is required to start.

1. Drop the `RoboBear` jar into `plugins/` and restart.
2. Watch the console. You want to see either a list of mines or this:

   ```
   [RoboBear] No mines were found. Objectives that need one can't be offered.
   ```

   That warning is expected on first boot and tells you step 3 is next.
3. `/rb mines` — confirm the mines and the source.
4. `/rb mines edit` — pick which of them the challenge may use. Every mine is in
   the pool by default, including the rank-gated ones.
5. `/rb milestones` — fill in what the ladder pays. **Until you do this, a run
   pays nothing.** The starter tiers ship deliberately empty.
6. `/rb pass give` then `/rb` to try it.

That's it. There is no database to set up and no schema to migrate.

---

## 3. Step one: mines

RoboBear needs to know where your mines are so it can say "break 400 blocks in
**quarry**" and know when you did it.

### With MineResetLite (the intended path)

Leave `mines.source: auto` alone. On boot, RoboBear finds MineResetLite, reads
its mine list, and snapshots each mine's name and bounds into its own memory.
The snapshot refreshes every `mines.refresh-seconds` (default 300) and on
`/rb reload`. **The mining hot path never calls MineResetLite** — it tests a
broken block against plain integer bounds.

Nothing to configure. `/rb mines` should show your mines with their bounds:

```
Mines from mineresetlite:
 • quarry   world (100,40,100) → (140,60,140)  (34,461 blocks)
 • iron     world (200,30,200) → (230,50,230)  (14,161 blocks)
```

**If that list is empty but MineResetLite is installed**, run:

```
/rb mines debug
```

It reports exactly what the reader can see: which plugin object it bound to,
its main class, whether a mine collection was found and how big it is, the mine
class name, how the bounds are being read, and a sample of the parsed regions.
The same report goes to the server log.

Common answers it gives:

| Report says | Meaning |
|---|---|
| "Bounds read via: …" | Working. The lines under it are your real mines. |
| "No loaded plugin matches MineResetLite" | Your fork registers under another name — it lists what *is* loaded |
| "Mine collection found: 0 entries" | MineResetLite has no mines defined yet; check `/mrl list`, then `/rb reload` |
| "Could not work out how to read its bounds" | A shape we don't know yet — the key and member dump under it is the fix |

**Which builds are readable.** The plugin is found by exact name and then by a
loose match, so a fork calling itself `MineResetLitePlus` is still picked up.
Bounds are then tried in this order:

1. **Named getters** — `getMinX()`…`getMaxZ()`, or the fields behind them.
2. **A corner pair** — `getMin()`/`getMax()` returning Locations or Vectors.
3. **A nested region object** — either of the above on a `getRegion()`.
4. **The serialised map** — `serialize()`, read by its string keys.

Number 4 is the one that matters on a **premium or obfuscated build** such as
MineResetLite 4.21.2, where every field has been renamed to a single letter and
there is no `minX` left to find. Such a build still has to write its mines to
YAML and load them back, so `serialize()` keeps the original `minX`/`maxZ`/
`world` keys — obfuscators rename symbols, not string literals, because
renaming these would break every saved mine file on the server. RoboBear
resolves those keys once from a real mine, checks they actually hold numbers,
and reads every mine through them.

The integration is reflective on purpose (several forks share the name and none
publish an API), which makes it adaptable but not guaranteed against your
particular build. If yours still isn't read, the debug output ends with the
mine's `serialize()` keys and its full member list — paste that into an issue
and support can be added directly. `mines.source: manual` keeps you running
meanwhile.

### Without it, or as a fallback

Define regions yourself, in game:

```
/rb pos1                 stand at one corner
/rb pos2                 stand at the opposite corner
/rb mine set quarry      saves it
```

Then set `mines.source: manual` in `config.yml` and `/rb reload`. If you skip
that last step the command warns you the region is saved but unused.

`/rb mine delete quarry` removes one. IDs are lowercased and stripped to
`a–z 0–9 _`, so `Big Mine` becomes `big_mine`.

> **Regions are boxes.** A non-rectangular mine should be given bounds that
> enclose it. Blocks broken inside the box but outside the mine still count —
> keep the box tight if that matters to you.

### Choosing which mines the challenge uses

**`/rb mines edit`** — do this before you let anyone play.

RoboBear reads *every* mine your source knows about, and objectives are rolled
across all of them. On a prison or boxpvp server that is usually wrong: most
mines are rank-gated, and being sent to one you can't enter is a round lost to
nothing you did. A server with seventy mines almost certainly wants five or six
in the pool.

The picker is a paged chest, one icon per mine:

- **Green** — in the pool. Objectives may be set here.
- **Grey** — excluded. Click either to flip it.
- **Enable every mine** — back to reading everything.
- **Disable every mine** — shift-click, then switch on the handful you want. This
  is the fast route when the list is long.

Two things it deliberately does *not* do:

- **A mine you switch off does not break a run already in it.** Only the rolling
  of new objectives is filtered; block detection is untouched.
- **A mine nobody has an opinion about is playable.** The exclusions are what get
  stored (`mine-toggles.yml`), so a mine you add in MineResetLite later joins the
  pool instead of silently sitting out.

`/rb mines` shows the same state in chat, with `[off]` against the excluded ones
and a count at the bottom.

> If you switch off **everything**, mining objectives stop being offered at all —
> the picker says so in red, and if mob objectives are off too, runs won't start.

---

## 4. Step two: payouts

This is the part you should spend real time on. **`/rb milestones`** opens the
editor; everything below happens in game, and nothing needs a restart.

### The tier list

Each icon is one milestone round. Gold = it pays something, grey = it doesn't.

| Click | Does |
|---|---|
| **Left-click** a tier | Open its payout box |
| **Right-click** a tier | Rename it (type in chat, or `cancel`) |
| **Shift-right-click** a tier | Delete it |
| **Emerald (+ Add)** | Create a new tier — it asks which round in chat |

A fresh install lays out four empty tiers at rounds 5, 10, 15 and 20. Rename,
delete or add whatever you like; the round number is the only thing that
matters mechanically.

### The payout box

Left-clicking a tier opens a chest. **Put in the items that milestone should
pay.** Real items, dragged from your inventory — enchanted, named, custom-model,
crate keys, whatever your server already makes.

- The **top 45 slots** are yours. The bottom row is locked.
- **Contents save when you close the menu.** There is no save button to forget.
- The help book tells you how the items will be delivered.

Whatever you put in is what comes out — RoboBear has no reward number, no
multiplier and no economy hook. That is deliberate: it means a payout is worth
exactly what your server already says those items are worth.

### Delivery

`rewards.delivery` decides where the items land:

- **`ground`** (default) — dropped at the player's feet with
  `dropItemNaturally`, exactly how a PvP kill pays. The haul is unbanked, in the
  open, and someone else can take it. Finishing a milestone hands the player a
  payday *and* a problem.
- **`inventory`** — straight into their inventory, overflow dropped.

Use `ground` on a PvP server. It is the single setting that makes the ladder
interesting rather than a slot machine.

### Running commands too

Commands aren't editable in the GUI — add them to `milestones.yml` and
`/rb reload`:

```yaml
milestones:
  '10':
    name: 'Silver Cog'
    commands:
      - 'crate give %player% robo 1'
      - 'broadcast %player% cracked round %round%!'
```

`%player%`, `%uuid%` and `%round%` are filled in. Commands run from console. A
command that throws is logged and skipped — it never breaks the payout.

---

## 5. Step three: passes

By default a run costs one name tag named **Robo Pass**. Issue them with:

```
/rb pass give                 → one entry's worth, to yourself
/rb pass give Steve           → to Steve
/rb pass give Steve 10        → ten of them
```

Whatever won't fit in their inventory drops at their feet, and the command
reports how many.

### Passes are stamped, not just named

A pass carries a hidden persistent-data tag applied at the moment it is issued.
Entry checks **that tag**, not the name.

This matters more than it sounds. If a pass were recognised by its display name
— which is how this worked before `1.0.3` — then any player with an anvil, a
name tag and two levels could mint an unlimited supply, and on a PvP server that
is the entry gate gone entirely. There is no way to make a name unforgeable;
there is no way for a player to forge the tag.

- **Renaming an issued pass keeps it valid.** The tag survives anvils, enchanting
  tables, shulker boxes and item frames.
- **Renaming anything else never creates one.** Crafting and grindstones don't
  carry persistent data across.
- A player turned away while holding a lookalike is told *"that one wasn't issued
  by RoboBear"*, so it reads as a rule rather than a bug.

`/rb pass give` is therefore the mint, and it is `robobear.admin` only. Crates,
kits, votes and shops can still hand passes out — point them at the command
rather than at a `/give`.

**Upgrading with passes already in circulation?** Set
`run.entry-item.require-tag: false` to keep matching by name until they're spent,
then set it back to `true`. Leaving it off leaves the exploit open.

| You want | Set |
|---|---|
| A different pass item | `run.entry-item.item: DIAMOND` |
| More than one item per entry | `run.entry-item.amount: 4` |
| A reusable key, not a ticket | `run.entry-item.consume: false` |
| Free entry | `run.entry-item.item: ""` |
| A time gate instead of an item | `run.entry-item.item: ""` + `run.cooldown-seconds: 3600` |
| Old name-matching behaviour | `run.entry-item.require-tag: false` |

**The pass is taken last**, only once entry is certain. A refused start — no
mines, on cooldown, already running — never eats it.

---

## 6. Command reference

`/robobear`, `/rb` and `/robo` are the same command.

### Players

| Command | Does |
|---|---|
| `/rb` | Open the menu. This is the one to teach. |
| `/rb start` | Start a run, or reopen the screen of the one you're in |
| `/rb retire` | Stop here, keep everything already paid |
| `/rb cancel` | Abandon the run, keep everything already paid |
| `/rb stats` | Your runs, deepest round, payouts, fastest run |

`retire` and `cancel` do the same thing to the ladder; `retire` is the polite
one that exists on the workshop screen.

### Staff (`robobear.admin`)

| Command | Does |
|---|---|
| `/rb mines` | List the mines, their source, and which are in the pool |
| `/rb mines edit` | Choose which mines objectives may use |
| `/rb mines debug` | Explain what the MineResetLite reader can and can't see |
| `/rb pass give [player] [n]` | Issue entry passes (defaults to you, one entry's worth) |
| `/rb milestones` | Open the payout editor |
| `/rb upgrades` | Choose which workshop upgrades are on sale |
| `/rb quests` | Choose which job types are offered, and what each mine may be asked for |
| `/rb mobs` | Show the challenge mob roster and how many are alive |
| `/rb mobs clear` | Remove every challenge mob, including strays a crash left behind |
| `/rb pos1` / `/rb pos2` | Select corners for a manual mine |
| `/rb mine set <id>` | Save the selection |
| `/rb mine delete <id>` | Delete a manual mine |
| `/rb stats <player>` | Somebody else's record |
| `/rb reset <player>` | Wipe their record and drop any live run |
| `/rb reload` | Re-read every config from disk |

`/rb reload` **ends all live runs.** It has to — the round they're on may point
at a mine that no longer exists. Reload on a quiet server, not mid-event.

---

## 7. Permissions

| Node | Default | Grants |
|---|---|---|
| `robobear.use` | everyone | Menu, starting runs, own stats |
| `robobear.admin` | op | Payout editor, mine setup, other players' data, reload |

To make the ladder a perk, negate `robobear.use` for default and grant it to a
rank. Everything else derives from those two.

---

## 8. Tuning, in the order that matters

The shipped numbers are a reasonable starting point that **has never been
playtested on a live server**. Expect to change them. In priority order:

### 1. `objectives.*.growth` — how long a run lasts

This single number decides where the ladder ends, because targets grow
`base × growth^(round-1)`. At the default `1.28`, "break N blocks" looks like:

| Round | Gentle offer | Greedy offer |
|---|---|---|
| 1 | 90 | 140 |
| 5 | 240 | 370 |
| 10 | 850 | 1,250 |
| 13 | 1,750 | 2,700 |
| 15 | 2,850 | 4,350 |

A fast player with Haste clears maybe 1,200–1,500 blocks in five minutes, so
**runs realistically die around round 11–14** and the round 15 tier is close to
decorative. That may be exactly what you want — or not:

- **Longer ladders:** `growth: 1.20` puts round 15 at ~1,150. Round 20 becomes
  a genuine achievement instead of a joke.
- **Shorter, punchier runs:** `growth: 1.35`.
- Watch `/rb stats` on your best players for a week and set the top tier just
  past where they actually stall.

### 2. Where you put the milestone rounds

Round 5 should be reachable by a normal player on a normal day, or nobody plays
twice. Everything above it is the gamble.

### 3. `run.round-seconds`

Five minutes is Bee Swarm's number. Shorter makes the ladder about burst speed;
longer makes it about stamina. Note that the Spare Battery upgrade adds 60s per
level *for the rest of the run*, so the effective clock climbs anyway.

### 4. The workshop

`upgrades.cost-growth: 1.6` means level 2 costs 1.6× level 1 and level 3 costs
2.56×. Starting Cogs are 10 and a round pays 5–12, so a player buys roughly one
thing per round early on and has to make choices later.

| Upgrade | Effect | Max | Cost per level |
|---|---|---|---|
| **Overclocked Drill** | Haste | 3 | 8 → 13 → 20 |
| **Servo Legs** | Speed | 3 | 8 → 13 → 20 |
| **Impact Driver** | Strength | 2 | 12 → 19 |
| **Plating** | Resistance | 2 | 12 → 19 |
| **Spare Battery** | +60s to this and every later round | 3 | 10 → 16 → 26 |
| **Scrap Magnet** | +3 Cogs every later round | 3 | 10 → 16 → 26 |

Effects are ambient and particle-free — a run buff shouldn't cost a player their
view. They are cleared when the run ends.

**`/rb upgrades`** picks which of the six are actually on sale. Click one to
take it off sale; shift-click **Close the workshop** to take them all off.

This is worth a look on a PvP server before anyone plays. *Impact Driver* hands
out Strength in a world where other people can be hit, and *Overclocked Drill*
changes how fast a mine empties — both are decisions about your server, not
about RoboBear. Prices stay in `config.yml` under `upgrades`, since they belong
next to the comments that explain them; only the on/off switch is in game.

Two things it deliberately does *not* do, matching the mine picker:

- **Levels already bought keep working** for the rest of that run. Taking an
  upgrade off sale stops it being sold; it doesn't reach into live runs and strip
  what players paid Cogs for.
- **A shop screen left open can't still sell it.** The check is on the purchase,
  not just on the icon.

If you close the workshop entirely, Cogs have nothing to buy — drop
`run.starting-cogs` and `run.cogs-per-round` to match, or the reward for
clearing a round becomes a number that does nothing.

### 5. Objective types

**`/rb quests`** — the three job types, and what each mine may be asked for.

Click a type to stop it being offered. `config.yml` stays the master switch: a
type turned off there can't be switched back on from the screen, and the screen
says so rather than pretending. A type that's on but can't actually be rolled —
no mines in the pool, nothing on the material list — says that too, in red.

| Type | Reads as |
|---|---|
| **Break blocks** | "Break 150 blocks in *quarry*" — anything in the mine counts |
| **Break a material** | "Break 40 × Gold Ore in *goldmine*" — one specific block |
| **Kill mobs** | "Kill 12 hostile mobs" — anywhere, and the challenge sends its own (§8.6) |

**Break a material** used to be the one that could hand out an impossible round:
it picked a mine and a material out of two unrelated hats, so a quartz mine could
be asked for gold. Since `1.0.5` the mine is picked first and the material comes
from *that mine's own composition*; since `1.1.0` that composition is read from
the blocks themselves rather than depending on the mine plugin to expose it. A
mine with nothing worth asking for is skipped rather than sent to.

**No round asks for more than you could get.** The difficulty curve knows the
round number and nothing else, so left alone it will happily ask for 250 blocks
from a mine holding two stacks. RoboBear surveys each mine — a stride of block
reads, widening with the mine so the cost is fixed whatever the size — and trims
what it asks for to that stock × `mine-resets-per-round` × `mine-fraction`. Set
`objectives.limits.mine-resets-per-round` to roughly `run.round-seconds` divided
by your mine's reset interval; at the default five-minute round and a
five-minute reset, that's `1`. A mine too thin to support even
`minimum-amount` is passed over rather than set an impossible job.

Each mine's icon shows roughly how many blocks it holds, so a job that came out
smaller than its round doesn't read as a bug.

**No round offers the same job twice.** The safe and the greedy offer are always
different in *what* they ask for, not just how much. Where a server can genuinely
only build one job, one offer is made rather than two of it.

The lower half of the screen is one icon per mine in the pool, showing exactly
what it may be asked for. Click one to set the list by hand — a drop-in box, same
as the payout editor: put the blocks in, close it, done. Empty means automatic.
Shift-click a corrected mine to put it straight back to automatic.

Two things it deliberately does *not* do:

- **Opening a mine and closing it changes nothing.** A hand-set list is only
  stored when it actually differs from what would have been worked out, so
  looking never pins a mine against later composition edits.
- **A hand-set list ignores `objectives.mine-material.materials`.** Automatic
  narrows the mine's contents to that list; by hand means by hand.

`objectives.mine-material.materials` is still what's *worth* being sent after,
server-wide — leave it empty to switch the type off entirely.

### 6. The mobs the challenge sends

While a round's clock is running, RoboBear sends things after the player. They
are **only visible to that player**, only ever attack that player, and nobody
else can see them, hit them, or be hit by them.

They are real entities, not a client-side illusion — a mob that exists only as
packets sent to one player cannot attack, because targeting, pathfinding and
damage are all server-side. What makes them private is per-player visibility:
everyone who isn't the owner never receives the entity at all.

**What that can't hide.** Sound and particles are positional and aren't tied to
the entity, so a bystander in the same mine will hear a fight and see somebody
taking damage from nothing. That's a limit of the approach, not a bug.

**When they exist.** Spawned a few seconds into a round, cleared the moment it
ends. The shop between rounds is a breather. They're also cleared on death,
retire, logout, `/rb reload` and shutdown, and anything a crash strands is swept
up when its chunk loads or with `/rb mobs clear`.

**Dying to one ends the run**, exactly like any other death — see
`run.fail-on-death`. This is the single biggest thing the feature does to your
server's difficulty. Turn `mobs.enabled` off if you don't want it.

The roster escalates by sending *different* things, not the same husk with more
hearts:

| From round | Name | Base | What it does |
|---|---|---|---|
| 1 | Swarf Mite | Silverfish | Fast, cheap, arrives in numbers |
| 1 | Sledge Unit | Husk | Slow bruiser; doesn't burn in daylight |
| 3 | Bolt Slinger | Skeleton | Ranged — punishes standing still on one vein |
| 4 | Coolant Leak | Cave spider | Poisons, and fits through a 1-block gap |
| 6 | Scrap Drone | Vex | Flies through blocks; you can't wall it out |
| 6 | Pressure Valve | Breeze | Knocks you off your spot rather than killing you |
| milestones | The Foreman | Piglin brute | One only, named, +20 health. Makes a payout round a fight |

Everything in it is editable under `mobs.roster` — `type` is any vanilla entity,
`min-round` is when it unlocks, `weight` is how often it turns up, `elite` means
milestone-only, and `weight: 0` benches an entry without deleting it.

**Nameplates are always on** (`mobs.show-names`). **Glow means "you have to kill
this to finish the round"** — challenge mobs glow on a kill round and are dark on
a mining round, where they're pure hazard and seeing them through rock would take
the teeth out of it. Vanilla mobs never glow, even though they count on a kill
round: they aren't hidden from anyone, so glowing them would light them up for
every player on the server.

They **drop nothing** by default (`mobs.drops`) — a hazard that follows you
around and drops loot is a mob farm with extra steps.

Kill objectives count **any** hostile mob, natural or sent. The *amount* is sized
against what the challenge can guarantee to send, though, since a mine world may
have no natural spawns at all — anything that wanders in just finishes the round
sooner. `objectives.limits.mob-fraction` is the share of that supply a job may
ask for.

Creepers and endermen are deliberately absent. If you add one anyway, RoboBear
refuses its block damage so your mine survives, but it'll still be a nuisance.

`mobs.population` and `mobs.reinforce` control how many and how fast; both get
heavier on a kill round. `mobs.follow.teleport-distance` is how far a mob may
fall behind before it's simply moved to you — mob AI won't path across a mine,
let alone follow someone who ran for it.

### 7. The clock display

`display.mode` defaults to `actionbar`. `bossbar` is far more readable if
nothing else on your server owns the bossbar (see §12).

**If something else keeps covering the clock**, that's `display.actionbar-refresh-ticks`.

The action bar is a single line the whole server shares, and no plugin owns it —
there is no priority to claim, only a race. Whoever wrote it last in a given tick
is what the player sees, so a combat tag or an autosell total will blank the run
clock until something writes again.

RoboBear rewrites it **five times a second** by default, and on Paper does that
write at the very *end* of the tick, after other plugins have taken their turn.
That wins outright against anything scheduled normally.

| Set it to | You get |
|---|---|
| `4` (default) | Five writes a second, at end of tick. A competitor covers the clock for ~0.2s at worst |
| `1` | Every tick. Wins against anything, at one packet per tick per player in a run |
| `0` | Off — once a second from the main clock, as it behaved before `1.0.4` |

On a server whose API lacks the end-of-tick hook, RoboBear falls back to a plain
timer at the same rate and says so once in the log. The clock can then briefly
lose the line; raise the rate or switch to `bossbar` if that bothers you.

The setting is ignored in `bossbar` mode, which isn't contended.

---

## 9. Placeholders

Requires PlaceholderAPI. All are `%robobear_<name>%`.

**Live run:** `running`, `state`, `round`, `cogs`, `objective`, `progress`,
`target`, `percent`, `time`, `elapsed`, `payouts`

**Career:** `runs`, `best_round`, `total_rounds`, `milestones`,
`deepest_payout`, `cooldown`, `ready`

**Server:** `mines` (all of them), `mines_enabled` (in the objective pool),
`tiers`, `active`

A scoreboard line that only shows during a run:

```yaml
- '%robobear_running%|Round %robobear_round% — %robobear_progress%/%robobear_target% (%robobear_time%)'
```

---

## 10. Files on disk

```
plugins/RoboBear/
├── config.yml         everything above
├── milestones.yml     the payouts (written by the GUI — safe to edit, then /rb reload)
├── mines.yml          manual regions (only read when the source is manual)
├── mine-toggles.yml   which mines are excluded from objectives (/rb mines edit)
├── upgrade-toggles.yml  which upgrades aren't sold (/rb upgrades)
├── objective-toggles.yml  which job types aren't offered (/rb quests)
├── mine-materials.yml   hand-set material lists per mine (/rb quests)
└── data/<uuid>.yml    one small file per player
```

Player data saves on quit, on shutdown, and every `storage.autosave-minutes`.
Writes happen off the main thread from a snapshot taken on it.

**Backing up:** copy the folder. **Wiping the season:** delete `data/` while
the server is stopped; payouts and mines survive.

---

## 11. Troubleshooting

| What you see | Why | Fix |
|---|---|---|
| "Nothing is set up for a run yet" | No mines *in the pool* **and** mob objectives off | `/rb mines edit` to switch some on, or enable `objectives.kill-mobs` |
| Players sent to mines they can't enter | Every mine is in the pool by default | `/rb mines edit` — disable all, switch on the ones everyone can reach |
| "You need 1× Robo Pass" while holding one | It was renamed rather than issued | `/rb pass give <player>`; only issued passes carry the tag |
| Passes stopped working after upgrading to 1.0.3 | They predate the tag | `run.entry-item.require-tag: false` until they're spent, then back to `true` |
| `/rb mines` empty, MineResetLite installed | Fork under another name, no mines defined, or a shape we can't read | **`/rb mines debug`** — it names which of the three it is |
| `/rb mines` empty on a premium/obfuscated MineResetLite | Its fields are renamed, so there is no `minX` to find | Needs 1.0.2 or later, which reads the bounds out of `serialize()` instead |
| Mining doesn't count | Wrong mine, or blocks the player placed | `/rb mines` for bounds; self-placed blocks never count |
| A run pays nothing | Tiers are empty placeholders | `/rb milestones`, fill the boxes |
| Payout vanished | `delivery: ground` and they didn't pick it up, or someone else did | Working as designed; use `inventory` to soften it |
| Everyone stalls at round 3 | `growth` too steep for your mines | Lower `objectives.*.growth` |
| Runs end on death and players complain | `run.fail-on-death` | That's the point on PvP; set `false` if not |
| Runs vanished after a config change | `/rb reload` ends live runs | Reload when quiet |
| Timer invisible | `display.mode` conflicts with another plugin | Try `bossbar`, or check for actionbar competition |
| Clock flickers or gets covered | Another plugin writes the same action bar line | `display.actionbar-refresh-ticks: 1`, or switch to `bossbar` |
| An upgrade you don't want players buying | The workshop sells all six by default | `/rb upgrades` — click it off sale |
| "Break 30 × Gold Ore in *quartz*" — a mine that has none | Before `1.0.5` the mine and the material were rolled independently | Update; the material now comes from that mine's composition |
| A material job names filler nobody should mine | The mine's composition includes it and it's on the config list | `/rb quests` → click the mine → set its blocks by hand |
| Material jobs never appear | No mine's contents overlap `objectives.mine-material.materials` | `/rb quests` — the type icon says which of the two it is |
| Still "Deepslate Iron Ore in *quartz*" on 1.0.5 | Nothing could read that mine, so it fell back to the whole config list | Update to `1.1.0`, which reads the blocks directly; check `mines.sample-blocks` isn't `0` |
| The safe and greedy offers were the same job | Before `1.1.0` each offer was rolled without looking at the other | Update; offers are now always different in what they ask for |
| "Break 250 blocks" in a mine that holds 128 | Before `1.1.0` amounts scaled off the round number alone | Update, then set `objectives.limits.mine-resets-per-round` to match your reset interval |
| Jobs are smaller than the round suggests | The clamp found the mine thinner than the curve wanted | Working as designed — `/rb quests` shows what each mine holds; raise `mine-fraction` if you want it tighter |
| Something invisible is attacking a player | A challenge mob — only its owner can see it | Expected during a round. `/rb mobs` to check, `/rb mobs clear` if one is stranded |
| Bystanders hear fighting from nothing | Sound is positional and can't be hidden with the entity | Not fixable; `mobs.enabled: false` if it bothers you |
| Challenge mobs die instantly at noon | Should not happen — they're sunproof | Report it; check nothing else is setting them alight |
| A mine grew holes after adding a mob | You added a creeper or enderman to the roster | RoboBear blocks their block damage; remove them from `mobs.roster` anyway |
| Kill rounds ask for fewer than the curve says | Sized to what the challenge can actually send | Working as designed — raise `objectives.limits.mob-fraction` or `mobs.population` |

**Logging out mid-run** ends it (`run.fail-on-quit: true`). Without that,
logging out is a free undo on a losing clock.

---

## 12. If you run the BoxPvP risk spec

RoboBear was built against `docs/risk-banking-spec.md` and the defaults respect
it. If you change these, change them knowingly:

- **`display.mode: actionbar`** — §5 reserves the bossbar for the deposit fee
  rate. Only move the clock to the bossbar if you aren't running that system.
- **`rewards.delivery: ground`** — §11 pays kills onto the floor and principle 3
  forbids value arriving pre-secured. Whitelisted ore paid this way lands
  unbanked and at risk under §9 with no special-casing.
- **Cogs are not a currency** (§18). They exist only inside a run and are
  destroyed with it. Do not add a way to convert or carry them out.
- **The self-placed guard is run-scoped and in-memory** — §17 wants exactly one
  persistent placed-block tracker, and that's BoxCore's `PlacedBlocks`, not this.

One honest tension: objectives are absolute amounts, which rubs against
principle 6's preference for relative thresholds. They scale with the round
rather than sitting at a fixed bar, but a player with heavy mining perks still
climbs further than one without. Worth watching in playtest.

---

## 13. A blurb you can paste to players

> **Robo Bear Challenge** — `/rb`
>
> Spend a Robo Pass and Robo Bear puts you to work. Pick one of two jobs, beat
> the clock, and get paid in **Cogs** you can spend on drills, speed and armour
> right there in the workshop. Then he offers you a harder one.
>
> Clear round 5, 10, 15 and he pays you for real — and you keep it, whatever
> happens next. Miss a clock, die, or log out and the run is over.
>
> Your gear drops where you stand. So does your payout. Walk away rich or push
> one more round.

---

*Questions, or a MineResetLite build RoboBear can't read? Open an issue with the
startup log — the class dump in it is exactly what's needed to add support.*
