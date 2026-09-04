# RoboBear

A **Paper 1.21.4** plugin that rebuilds Bee Swarm Simulator's **Robo Bear
Challenge** on top of the mines a boxpvp server already has: a ladder of timed
rounds, a choice of job at the top of each one, a run-only currency to spend on
upgrades between them, and payouts at milestone rounds.

> ℹ️ **Made with AI.** Written by an AI assistant (Anthropic's Claude) from a
> human's design, and maintained the same way. Review it and test it on your own
> server before trusting it in production — a timed mode changes how players use
> the map, and the shipped numbers are a starting point, not gospel.

> This is a separate project from *CustomAchievements*, *AntiCheat*, *BoxCore*
> and *Spyglass* in the same repository: different module (`robobear/`),
> different package (`com.diamend.robobear`), different purpose.

📖 **Setting it up on a server? Read the [operator's guide](GUIDE.md).** This
README explains why the plugin is shaped the way it is; the guide walks through
installing it, pointing it at your mines, filling in the payouts and tuning the
numbers once players get hold of it. **Upgrading?** The
[changelog](CHANGELOG.md) has what changed in each release.

---

## What it is

In Bee Swarm Simulator you talk to Robo Bear, spend a **Robo Pass**, and get
dropped into a run: you're handed some **Cogs**, shown **two quests** and pick
one, draft a few bees, spend Cogs on upgrades, and then have **five minutes** to
finish the quest while machines harass you. Clear five rounds and you get a
Bronze **Cog Amulet**; ten and it's Silver.

RoboBear keeps that structure and swaps the nouns for ones a Minecraft server
already has:

| Bee Swarm | RoboBear |
|---|---|
| Robo Pass | A configurable entry item (default a named `Robo Pass` name tag) |
| Fields | Your mines, read straight out of **MineResetLite** |
| Collecting pollen | Breaking blocks in a named mine |
| Mechsquitos | Hostile mobs |
| Cogs | Cogs — earned and spent inside one run, gone when it ends |
| Buffs and drives | Six upgrades bought from the workshop between rounds |
| Cog Amulet tiers | Milestone payouts, filled in by staff from a drop-in menu |
| 5-minute quest timer | A per-round clock, 5 minutes by default |

**What is not recreated:** the bee draft. RoboBear has no hive to pick three
bees out of, so a run's loadout is the upgrades you buy rather than the bees you
draw. Everything else about the shape — the pass, the free Cogs, the choice of
two, the free reroll, the clock, the shop between rounds, the milestone tiers —
is the game's.

---

## Why it suits a boxpvp server

Beyond the theme, a run is a **voluntary way to turn the risk dial up**, which
is the one thing the [risk spec](../docs/risk-banking-spec.md) says the server is
about:

- You commit to a clock, so you can't break off to bank in the middle of a round.
- Objectives point at whichever mine they point at, so the ladder moves you
  around the chamber instead of letting you park on one cube.
- A payout lands **on the floor, unbanked**, exactly the way §11 pays a PvP kill.
  Finishing a milestone hands you a payday *and* a problem, and anyone watching
  can see it happen.
- The decision the mode is built around is "go again or retire" — bank what
  you've got, or reach one round further for the next tier.

Two of the spec's harder rules shaped the design rather than being worked
around:

- **§18 rejects any second currency.** Cogs are not one. They are minted at the
  start of a run, spent in the same run's workshop, and destroyed when it ends.
  Nothing can bank, trade or carry them out, and no purchasable value is priced
  in them.
- **Principle 3: no purchasing power arrives already-safe.** Payouts are real
  items chosen by staff and dropped at the player's feet. Put whitelisted ore in
  a tier and it lands unbanked and at risk under §9 with no special-casing from
  this plugin.

The bossbar is also left alone by default — §5 reserves it for the deposit fee
rate, so the run clock lives on the actionbar unless you turn that around.

---

## Requirements

- Java **21**
- **Paper** (or Paper-compatible) **1.21.4**
- Optional: **MineResetLite** (for automatic mine detection)
- Optional: **PlaceholderAPI**

---

## Building

The build lives in CI. `paper-api` and `placeholderapi` come from repositories
the development sandbox can't reach, so the jar is produced by
`.github/workflows/robobear.yml` on every push and uploaded as the
**`RoboBear-jar`** artifact. That artifact is the download.

To build it yourself on a machine with normal network access:

```bash
cd robobear
mvn clean package
```

The jar lands in `robobear/target/RoboBear-1.1.0.jar`. Drop it into `plugins/`
and restart.

---

## The run

1. **`/rb`** opens Robo Bear's menu: what entry costs, what the ladder pays, and
   how you've done.
2. **Start** takes the entry pass and hands over the starting Cogs.
3. **Pick a job.** Two are offered. The first is the gentler one; the second is
   harder and pays more Cogs. One free reroll per run rolls a new pair. *The
   clock is stopped while you choose.*
4. **Beat the clock.** Break the blocks or kill the mobs before the round timer
   runs out. Progress and time show on the actionbar. The challenge sends mobs
   after you while it runs — only you can see them, only you can be hurt by
   them, and one of them killing you ends the run.
5. **Get paid** in Cogs, and the **workshop** opens.
6. **Spend, then decide.** Buy upgrades, then either take the next round —
   harder, worth more — or **retire** and keep everything you've been paid.
7. **Clearing a milestone round** pays out its tier for keeps. A run that dies at
   round nine still keeps what round five paid.

A run ends when a clock runs out, when you die, or when you log out. All three
are deliberate: each would otherwise be a free way to stop a losing clock.

### The workshop

| Upgrade | What it does | Max |
|---|---|---|
| **Overclocked Drill** | Haste for the rest of the run | 3 |
| **Servo Legs** | Speed for the rest of the run | 3 |
| **Impact Driver** | Strength for the rest of the run | 2 |
| **Plating** | Resistance for the rest of the run | 2 |
| **Spare Battery** | Adds time to this round and every one after | 3 |
| **Scrap Magnet** | Every round from now on pays more Cogs | 3 |

Each level costs more than the last, so nobody maxes one thing on the first
visit. A battery bought mid-round is felt in *that* round, not the next.

---

## Commands

Base command: `/robobear` (aliases `/rb`, `/robo`)

| Command | Description | Permission |
|---|---|---|
| `/rb` | Open the menu | `robobear.use` |
| `/rb start` | Start a run, or get back to the one you're in | `robobear.use` |
| `/rb retire` | Stop here and keep what you've been paid | `robobear.use` |
| `/rb cancel` | Abandon the run | `robobear.use` |
| `/rb stats [player]` | Runs, deepest round and payouts | `robobear.use` |
| `/rb mines` | List the mines RoboBear can see | `robobear.admin` |
| `/rb mines edit` | Choose which mines objectives may use | `robobear.admin` |
| `/rb mines debug` | Explain what the MineResetLite reader can see | `robobear.admin` |
| `/rb quests` | Choose which job types are offered, and what each mine may be asked for | `robobear.admin` |
| `/rb upgrades` | Choose which workshop upgrades are on sale | `robobear.admin` |
| `/rb mobs [clear]` | Show the challenge mob roster, or clear strays | `robobear.admin` |
| `/rb pass give [player] [n]` | Issue entry passes | `robobear.admin` |
| `/rb milestones` | Edit the milestone payouts | `robobear.admin` |
| `/rb pos1` / `/rb pos2` | Select a corner for a manual mine | `robobear.admin` |
| `/rb mine set <id>` | Save the selection as a mine | `robobear.admin` |
| `/rb mine delete <id>` | Delete a manual mine | `robobear.admin` |
| `/rb reset <player>` | Wipe a player's RoboBear data | `robobear.admin` |
| `/rb reload` | Re-read every config | `robobear.admin` |

`/rb stats <player>` and `/rb reset <player>` work on anyone the server has seen
before.

### Permissions

| Node | Default | Grants |
|---|---|---|
| `robobear.use` | everyone | The menu and running the ladder |
| `robobear.admin` | op | Editing payouts and mines, and player data |

---

## Where the mines come from

RoboBear does not define regions of its own if it doesn't have to. It reads
**MineResetLite**'s mines directly, so a cube you've already drawn is a job it
can set.

The integration is **reflection-based and soft**, for two reasons: MineResetLite
isn't published to any Maven repository this project can build against, and
several forks of it are in circulation under the same plugin name. Compiling
against one would pin your server to that fork. Instead the provider tries a
list of plausible shapes — a getter, then a field, then a corner-pair accessor —
caches whatever worked, and if none of it fits it logs the class's actual method
names **once** and tells you to switch to the manual source. You get an
actionable report rather than an empty menu.

Nothing about this is on a hot path. The mine list is flattened into plain
integer boxes on enable, on `/rb reload` and on a slow timer; a block break is
then a map lookup and six comparisons.

```yaml
mines:
  source: auto   # auto | mineresetlite | manual
```

**Without MineResetLite**, build regions in game — no file editing, no restart:

```
/rb pos1          # stand at one corner
/rb pos2          # stand at the opposite corner
/rb mine set quarry
```

and set `mines.source: manual`.

---

## Milestone payouts

`/rb milestones` opens the ladder. Each tier is a round number, a name and a box
you drop items into:

- **Left-click** a tier to edit what it pays — a plain inventory you put real
  items into. Contents are saved when you close it.
- **Right-click** to rename it.
- **Shift-right-click** to delete it.
- **+ Add a payout** asks which round should pay.

Items rather than a number, deliberately: RoboBear never invents a reward
currency, so a payout can be anything your server can make — raw ore, a named
consumable, a crate key, a BoxCore compactor. A tier can also run console
commands (`%player%`, `%uuid%`, `%round%`).

A fresh install lays out four empty tiers at rounds 5, 10, 15 and 20 so there's
something to click. **Until you fill them in, a run pays nothing** — the menu
says so on every empty tier, and so does the startup log.

Delivery is set once, for everything:

```yaml
rewards:
  delivery: ground   # ground | inventory
```

`ground` drops the payout at the player's feet with `dropItemNaturally`, which is
how §11 pays a kill.

---

## Configuration

Everything hand-editable lives in `config.yml`, which the plugin never rewrites.
`milestones.yml` and `mines.yml` are the plugin's files, written by the menus —
the same split BoxCore draws around `compactor.yml`.

The knobs worth knowing about:

```yaml
run:
  entry-item:
    item: NAME_TAG          # "" for free entry
    name: "&6Robo Pass"     # blank accepts any name tag
    consume: true
  round-seconds: 300        # the game's five minutes
  starting-cogs: 10
  free-rerolls: 1
  objectives-offered: 2
  max-rounds: 0             # 0 = the ladder only ends when you miss a clock
  cooldown-seconds: 0       # 0 = the pass is the only gate

objectives:
  mine-blocks:    { enabled: true, base-amount: 120, growth: 1.28 }
  mine-material:  { enabled: true, base-amount: 40,  growth: 1.25, materials: [ ... ] }
  kill-mobs:      { enabled: true, base-amount: 10,  growth: 1.22 }
```

Amounts scale as `base × growth^(round-1)`, then by the difficulty of the
particular offer, and are rounded to something that reads like a target rather
than a calculation. Turn `kill-mobs` off on a server whose box has no mobs in
it.

The entry item is **taken only once the run is certain to start**, so a refused
entry never eats the pass — the same rule BoxCore applies to its respec token.

---

## Anti-farm

Blocks a player places during their own run never count toward it. The record is
**run-scoped**: positions are packed one per `long`, held in memory for the few
minutes a run lasts, and dropped when it ends. Reading a position also removes
it, so a spot freed by a mine reset isn't refused forever by a stale flag.

This is deliberately **not** a persistent placed-block tracker. The risk spec
(§17) says there should only ever be one of those on the server, and that one is
BoxCore's `PlacedBlocks`. If the two ever need to agree, RoboBear should read
BoxCore's flags rather than grow a second set of its own.

---

## Placeholders

With PlaceholderAPI installed:

| Placeholder | Value |
|---|---|
| `%robobear_running%` | `true` while the player is in a run |
| `%robobear_state%` | `choosing`, `running` or `shopping` |
| `%robobear_round%` | The round they're on |
| `%robobear_cogs%` | Cogs in hand this run |
| `%robobear_objective%` | What this round is asking for |
| `%robobear_progress%` / `%robobear_target%` | Counted so far, and wanted |
| `%robobear_percent%` | Of that, the percent done |
| `%robobear_time%` | Time left on the round, e.g. `2:30` |
| `%robobear_elapsed%` | How long the run has been going |
| `%robobear_payouts%` | Milestone payouts taken this run |
| `%robobear_runs%` | Runs finished, all time |
| `%robobear_best_round%` | Deepest round ever cleared |
| `%robobear_total_rounds%` | Rounds cleared, all time |
| `%robobear_milestones%` | Milestone payouts taken, all time |
| `%robobear_deepest_payout%` | The name of the deepest one |
| `%robobear_cooldown%` / `%robobear_ready%` | Time until another run, and whether it's ready |
| `%robobear_mines%` / `%robobear_tiers%` / `%robobear_active%` | Server-wide counts |

---

## Storage

```
plugins/RoboBear/
├── config.yml         # settings & messages (yours; never rewritten)
├── milestones.yml     # payout tiers (the plugin's; written by the menu)
├── mines.yml          # manual regions, if you use them
└── playerdata/
    └── <uuid>.yml     # runs, deepest round, payouts taken
```

Runs themselves are never written down. A run has a clock, and a clock that
survives a logout isn't a clock.

Profiles are snapshotted on the main thread and written on a background thread;
saved on quit, on shutdown and on a timer.

---

## Performance

The risk spec (§17) is blunt about `BlockBreakEvent` firing at very high volume
on a server with regenerating cubes, so that path is written for it:

- The first thing the handler does is ask whether this player has a run at all —
  one map lookup — and return.
- No disk access, no reflection and no allocation on the path.
- The mine list is a pre-flattened snapshot of integer boxes, grouped by world.
- The clock ticks once a second, not once a tick, and does nothing at all when
  no runs are live.

---

## Testing

```bash
cd robobear
mvn test
```

The run clock, Cog arithmetic, position packing and the player record are tested
pure. The plugin lifecycle, the manual mine source and the entry-item rules run
against a real Paper API via MockBukkit (which downloads a server
implementation, so those need network access). CI additionally boots a headless
Paper 1.21.4, loads the jar and asserts the plugin enables cleanly and answers
its commands.

---

## Limitations & honesty

- **The MineResetLite integration is written blind.** It could not be tested
  against the real plugin from the development sandbox, so it is defensive by
  design and self-diagnosing on failure. If it doesn't fit your fork, the
  startup log names the class and its methods — that report is enough to fix it
  in one edit, and `mines.source: manual` keeps you running meanwhile.
- **The shipped numbers are unplaytested.** Base amounts, growth rates, Cog
  payouts and upgrade costs are a starting point sized by eye. Expect to retune
  `objectives.*.growth` first — it decides how many rounds a good player gets.
- **Material objectives are only as good as two lists.** A mine can be asked for
  what it actually contains, narrowed to `objectives.mine-material.materials`.
  That default list is ores, so a mine of pure filler is skipped rather than
  made unwinnable — but widen the list and filler becomes fair game. `/rb quests`
  shows what each mine resolved to and lets you correct the ones that are wrong.
- **Mine composition is sampled, not counted.** A stride of block reads across
  each region, scaled up to its volume — so a mine that is half mined-out when
  the survey runs looks half-sized, and a rare ore the stride never lands on is
  treated as *unknown* rather than absent. Good enough to stop the challenge
  asking for what isn't there; not an inventory. Chunks that aren't loaded are
  skipped, so a mine nobody visits keeps whatever was last read.
- **Challenge mobs can't be hidden completely.** Only their owner is sent the
  entity, so nobody else renders them or can touch them — but sound and
  particles are positional packets and aren't entity-scoped. A bystander in the
  same mine will hear a fight and see someone taking damage from nothing.
- **They consume the mob cap.** They're real entities, because a packet-only mob
  can't attack anything. A busy ladder will suppress some natural spawns in that
  world while it runs.
- **Dying to a challenge mob ends the run.** That is a deliberate, and large,
  increase in difficulty over 1.0.x. `mobs.enabled: false` turns it off.
- **The bee draft isn't recreated**, as above. If you want a loadout step, the
  natural place is a third phase between the shop and the round.
- **Objectives are absolute amounts**, which sits awkwardly with the risk spec's
  principle 6 ("no mechanic keyed to an absolute resource amount"). The mitigation
  is that they scale with the round rather than being fixed thresholds — but a
  player with heavy mining perks will climb further than one without, and that is
  a real interaction with BoxCore's skill trees that wants watching in playtest.
