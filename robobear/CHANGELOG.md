# RoboBear — changelog

What changed in each release, written for the person deciding whether to update.

The release workflow reads this file: the section matching `robobear/pom.xml`'s
version becomes the **What's new** part of that release's notes, and the build
**fails** if there isn't one. A version bump without an entry here is a release
whose page doesn't say what it contains, which is the thing this file exists to
prevent.

Add the new section at the top, headed `## <version>`, before bumping the pom.

---

## 1.1.0

**The challenge now sends mobs after you.** While a round's clock is running,
mobs come for the player — and **only that player can see them or be hurt by
them**. Everyone else is never sent the entity at all: a bystander in the same
mine sees an empty mine and cannot touch them or be touched.

The roster escalates by sending *different* things rather than the same husk
with more hearts. Silverfish and husks from round one, a skeleton at three, a
cave spider at four, a vex that flies through rock and a breeze that knocks you
off your vein at six — and **The Foreman**, one named piglin brute, on milestone
rounds, which turns a payout round into a fight. All of it is editable under
`mobs.roster`.

Nameplates are always on. Glow means "you have to kill this to finish the
round", so they light up on a kill round and are dark on a mining one.

> **Dying to one ends the run**, exactly like any other death. This is a
> deliberate and large increase in difficulty. `mobs.enabled: false` turns the
> whole thing off.

Mobs live only while the clock does — the shop between rounds is a breather —
and are cleared on death, retire, logout, reload and shutdown, with a chunk-load
sweep and `/rb mobs clear` for anything a crash strands.

**Three fixes for rounds that arrived already lost**, all from live play:

- **The safe and the greedy offer could be the same job.** Each was rolled
  without looking at the other. They're now always different in *what* they ask
  for, not just how much.
- **A material job could still name something its mine doesn't contain.** 1.0.5
  fixed the selection but left a fallback that assumed the whole config list
  whenever a mine's contents were unknown — which, on a server where nothing
  reports its composition, was every mine. RoboBear now reads the blocks
  directly, so this no longer depends on the mine plugin exposing anything.
- **"Break 250 blocks" in a mine holding two stacks.** Amounts scaled off the
  round number alone. The same block survey now sizes each mine's stock and
  trims what gets asked for to fit; a mine too thin for even a minimum job is
  passed over rather than set an impossible one.

Kill objectives count any hostile mob, natural or sent, but their *size* is
based on what the challenge can guarantee to deliver — a mine world may have no
natural spawns at all.

**New:** `/rb mobs` shows the roster and how many are alive; `/rb mobs clear`
removes strays.

> **Upgrading from 1.0.x:** `config.yml` is never rewritten once it exists, so
> you won't have the new `mobs:` or `objectives.limits:` blocks. Everything
> works without them on built-in defaults. To tune them, rename `config.yml`,
> restart for a fresh one, and copy your settings across.
>
> Set **`objectives.limits.mine-resets-per-round`** to your round length divided
> by your mine's reset interval. The default `1` assumes both are five minutes;
> leaving it too low makes jobs smaller than they should be.

*1.0.6 was never published — its build failed — and its changes are included
here.*

## 1.0.5

**`/rb quests`**, a new editor for which job types are offered and what each
mine may be asked for.

Fixes material objectives naming a block their mine doesn't have. The generator
picked a mine and a material out of two unrelated hats, so a quartz mine could
be asked for gold ore — an objective nobody can complete, which on a ladder
ends the run. The mine is now picked first and the material comes from that
mine's own composition; a mine with nothing worth asking for is skipped.

Clicking a mine in the editor opens a drop-in box prefilled with its current
answer. Opening one to look at it never pins it — a hand-set list is only stored
when it actually differs from what would have been worked out.

A run also now refuses to start when *no* objective type can be rolled at all,
rather than only when mines and mob objectives are both off. Taking someone's
pass for a run that can't offer them a job is the one failure that costs them
something real.

## 1.0.4

**`/rb upgrades`** — choose which of the six workshop upgrades are on sale. On a
PvP server that's a decision worth making deliberately: Impact Driver hands out
Strength where other people can be hit. The check is on the purchase, not just
the icon, so a shop screen left open while you take something off sale can't
still sell it.

**The run clock stops losing the action bar.** It's one line the whole server
shares with no notion of ownership, so a combat tag or an autosell total would
blank the clock until something wrote again. RoboBear now rewrites it several
times a second, from Paper's end-of-tick hook where available — after other
plugins have had their turn. It's a race that can only be won often, not won
permanently.

## 1.0.3

**Entry passes are stamped, not just named.** The pass was matched by display
name, so anyone with an anvil and a name tag could mint an unlimited supply — on
a PvP server, the entry gate gone. Passes now carry a persistent tag applied
only by `/rb pass give`. Renaming an issued pass keeps it valid; renaming
anything else never makes one. Set `run.entry-item.require-tag: false` if you
handed passes out before upgrading, until that stock is spent.

**`/rb mines edit`** — choose which mines objectives may use. Every mine was
fair game, so on a server where most are rank-gated a run could be lost to an
objective in a mine the player can't enter. Exclusions are what get stored, so
a mine added later joins the pool rather than silently sitting out.

## 1.0.2

**Reads mine bounds out of `serialize()`**, which is what makes obfuscated
builds such as MineResetLite 4.21.2 work. Every field on `Mine` is renamed to a
single letter there, so there is no `minX` to find — but a build still has to
write its mines to YAML and read them back, so the serialised map keeps the
original keys. Obfuscators rename symbols, not string literals.

## 1.0.1

**MineResetLite detection survives forks.** The plugin lookup was an exact name
match, so a fork registering as `MineResetLitePlus` was invisible — with an
empty mine list and no error, because nothing ever got as far as reflecting.

**`/rb mines debug`** reports what the reader can and cannot see, on demand,
and distinguishes the three real failures: wrong name, no mines defined yet,
unknown shape.

## 1.0.0

First release. A Bee Swarm Simulator style Robo Bear Challenge built on the
mines a server already has: an entry pass buys a run and some Cogs, each round
offers a choice of timed jobs, finishing pays Cogs to spend in a workshop, and
clearing a milestone round pays out real items for keeps.
