# RoboBear — the operator's guide

Everything you need to install RoboBear, point it at your mines, decide what it
pays, and tune it once players get their hands on it.

The [README](README.md) explains *why* the plugin is shaped the way it is. This
document is about *running* it.

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

Cogs are the in-run currency. They are minted at the start, spent in the
workshop, and **destroyed when the run ends**. Nothing carries over. The only
thing that leaves a run is what you put in the payout boxes.

---

## 2. Install it in five minutes

**Requirements:** Paper 1.21.4+ (or a fork of it), Java 21.
**Optional:** MineResetLite (mines), PlaceholderAPI (scoreboard/holo variables).
Neither is required to start.

1. Drop `RoboBear-1.0.0.jar` into `plugins/` and restart.
2. Watch the console. You want to see either a list of mines or this:

   ```
   [RoboBear] No mines were found. Objectives that need one can't be offered.
   ```

   That warning is expected on first boot and tells you step 3 is next.
3. `/rb mines` — confirm the mines and the source.
4. `/rb milestones` — fill in what the ladder pays. **Until you do this, a run
   pays nothing.** The starter tiers ship deliberately empty.
5. Give yourself a pass and run `/rb` to try it.

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

**If that list is empty but MineResetLite is installed**, RoboBear could not
read its internals. It logs the real class and method names it found, once, at
startup — send that to us and use the manual list meanwhile. This integration
is reflective on purpose (several forks share the name and none publish an API),
which makes it adaptable but not guaranteed against your particular build.

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

By default a run costs one name tag named **Robo Pass**.

```
/give <player> minecraft:name_tag[custom_name='{"text":"Robo Pass","color":"gold","italic":false}']
```

Matching is by **plain text, case-insensitively** — colour and formatting are
cosmetic, so anything literally named "Robo Pass" works, whatever made it. Hand
them out from crates, kits, votes, a shop, or by hand.

| You want | Set |
|---|---|
| A different pass item | `run.entry-item.item: DIAMOND` |
| Any item of that type to work | `run.entry-item.name: ""` |
| A reusable key, not a ticket | `run.entry-item.consume: false` |
| Free entry | `run.entry-item.item: ""` |
| A time gate instead of an item | `run.entry-item.item: ""` + `run.cooldown-seconds: 3600` |

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
| `/rb mines` | List the mines and which source they came from |
| `/rb milestones` | Open the payout editor |
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

### 5. Objective types

Turn off `objectives.kill-mobs` if your box has no mob spawns, or it will offer
jobs nobody can do. Trim `objectives.mine-material.materials` to what your
mines actually contain — asking for emerald ore in a coal mine is an
unwinnable round.

### 6. The clock display

`display.mode` defaults to `actionbar`. `bossbar` is far more readable if
nothing else on your server owns the bossbar (see §12).

---

## 9. Placeholders

Requires PlaceholderAPI. All are `%robobear_<name>%`.

**Live run:** `running`, `state`, `round`, `cogs`, `objective`, `progress`,
`target`, `percent`, `time`, `elapsed`, `payouts`

**Career:** `runs`, `best_round`, `total_rounds`, `milestones`,
`deepest_payout`, `cooldown`, `ready`

**Server:** `mines`, `tiers`, `active`

A scoreboard line that only shows during a run:

```yaml
- '%robobear_running%|Round %robobear_round% — %robobear_progress%/%robobear_target% (%robobear_time%)'
```

---

## 10. Files on disk

```
plugins/RoboBear/
├── config.yml        everything above
├── milestones.yml    the payouts (written by the GUI — safe to edit, then /rb reload)
├── mines.yml         manual regions (only read when the source is manual)
└── data/<uuid>.yml   one small file per player
```

Player data saves on quit, on shutdown, and every `storage.autosave-minutes`.
Writes happen off the main thread from a snapshot taken on it.

**Backing up:** copy the folder. **Wiping the season:** delete `data/` while
the server is stopped; payouts and mines survive.

---

## 11. Troubleshooting

| What you see | Why | Fix |
|---|---|---|
| "Nothing is set up for a run yet" | No mines *and* mob objectives off | Set up a mine, or enable `objectives.kill-mobs` |
| `/rb mines` empty, MineResetLite installed | Reflection couldn't read that build | Check startup log for the class dump, use `mines.source: manual` |
| Mining doesn't count | Wrong mine, or blocks the player placed | `/rb mines` for bounds; self-placed blocks never count |
| A run pays nothing | Tiers are empty placeholders | `/rb milestones`, fill the boxes |
| Payout vanished | `delivery: ground` and they didn't pick it up, or someone else did | Working as designed; use `inventory` to soften it |
| Everyone stalls at round 3 | `growth` too steep for your mines | Lower `objectives.*.growth` |
| Runs end on death and players complain | `run.fail-on-death` | That's the point on PvP; set `false` if not |
| Runs vanished after a config change | `/rb reload` ends live runs | Reload when quiet |
| Timer invisible | `display.mode` conflicts with another plugin | Try `bossbar`, or check for actionbar competition |

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
