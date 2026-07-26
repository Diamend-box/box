# Working plan — Wed (day 5/14) → Saturday

State this morning: the **core game is built and CI-green** on
`claude/dark-sea-world-concept-fuf0h6`. Sea, zones, islands (incl. castle +
Core nests), loot, mobs, boats + full naval combat, protection, PvP zoning,
the extraction loop, Soulwake Compass, and Chronon bounties are all done and
proven by CI. Nothing ships until you're back and say so. (Full history is in
`ROADMAP.md`; the original v1 spec is `PLAN.md`. This file is just the
short-term working plan.)

**Budget reality:** ~6% of your weekly limit left, resets Saturday night.
Correction to an earlier version of this file: **my building spends your limit
too**, not just your messages. So the economy is — batch the decisions, then
send one short "go" that unlocks a large block of work, rather than a
back-and-forth. Design thinking on your side is the cheap part; my tool use is
the expensive part.

**Fastest way to use this:** read §1 (what I can build without you) and §2
(what you can decide on your own), then send **one** message picking what you
want. Examples that each launch hours of my work: `"build all of §1"` ·
`"drydock + kill feed + leaderboard, skip the rest"` · `"do §1 and use my
vault answers: seal block, no timer, resets yes"`. I build in sequence,
self-check CI, and report — no further messages needed per item.

---

## §1. Solo-buildable NOW (I build + CI-prove, zero server, no return needed)

Each is pre-specced so a one-word approve is enough. Tweak any spec in your
reply and I'll follow it.

### 1a. Naval base island — the "Naxian Drydock" (10th shape) — **PARKED**
> Your call, day 5: *"the naval base could be added later but it's not
> necessary at the moment."* Left here as a full spec for whenever it comes
> back up; nothing below it depends on it.
A rare, drowned Naxian naval yard: half-sunk stone slipways, a cracked
dry-dock basin, a rusted gantry crane, and the ribcage of an unfinished
warship. A destination landmark and a natural PvP arena.
- **Rarity:** weight ~3 in rings **3–4** only (rare, like the castle); 0
  elsewhere. Stays out of the Trench so it doesn't crowd the nests.
- **Size:** ~55–65 blocks (bigger than a normal island, smaller than the
  castle's ~75).
- **Chests:** 2–3, hidden and foot-reachable (crane housing, under a slipway,
  in the ship's hold) — locked by the existing global reachability test.
- **Mobs:** moderate garrison from the normal ring pool.
- **Loot:** ships as a *rich plain island* first (no new loot seam). If you
  later want it to lean toward naval loot (boat tokens / naval ammo), that's a
  small follow-up — say so and I add the seam.
- Real code: new `DemoShape` subclass + generator + rarity + tests. The
  meatiest item here.

### 1b. Sea kill feed
Zone-flavored server-wide broadcasts when a player drowns/sinks another —
"X was dragged under in the Sunless Trench by Y." Rivalry fuel; reuses the
bounty broadcast plumbing. Small.

### 1c. "A Core stirs" reset broadcast
On each sea reset, announce the Trench's Core nests server-wide so everyone
has a shared target that cycle. Tiny hook in the reset path. Trivial.

### 1d. `/ds top` leaderboard
Persistent counts — Cores slain, boats sunk, deepest ring reached — with a
small stats store (survives restart). Status = retention. Medium.

### 1e. Zone-flavored death & entry lines
Distinct messages per ring on entry and on death. Pure config polish. Trivial.

### 1f. Login / daily Chronon stipend
A small first-join (and optional daily) Chronon drip so new players can start
the loop. Config + a join listener. Trivial.

**My recommended batch for the most value:** 1a + 1b + 1d — the drydock
landmark, the kill feed, and the leaderboard change how a boxpvp server
*feels* most. 1c/1e/1f are cheap add-ons on top.

---

## §2. Decide on your own (no server, no cost — just think, then one message)

Parked because they need a design call, not a build. My recommendation is
baked in so you can approve in a word.

### 2a. Vault-cracking (#9) — make the castle's true vaults *earned*
- **What opens it?** → A **breakable "vault seal" block** (e.g. crying
  obsidian) the protection listener whitelists, so cracking = breaking that
  ONE block (not mining the whole island). Simple, readable, no new UI.
- **Timed / contested?** → **No timer.** The seal breaks in N hits; PvP makes
  it contested naturally.
- **Resets on the soft-cycle?** → **Yes.** The soft reset re-pastes the
  island, so the seal returns each cycle.
- Scope: the castle's 2 true vaults (and optionally the Core chambers).
- Approve with `"vault-cracking: seal block, no timer, resets yes"` (or change
  any of the three) and I build it.

### 2b. Foot PvP — the SHAPE (exact numbers wait for your return + gear)
You can't set numbers till you're home with your custom gear, but you can
pre-decide the shape so return-day is fast:
- Custom DarkSea weapons beat enchanted netherite by a **small** margin
  (~10–15% DPS, current), a **bigger** one, or **match** it?
- Vironic glass-cannon (+dmg, leather defense) stays a **hard either/or** vs
  the netherite tank (current), or softer?
- Is the armor-piercing Stinger a **keeper** as the anti-tank answer?
- No build now — just bank the direction.

---

## §3. Blocked till you're back — do NOT spend budget here now

- **Foot PvP numbers** — need your custom gear in front of you.
- **Refugee trader** (Chronon shop) — needs the hand-built main island to host
  it.
- **Schematic island builds** — need FAWE updated + WorldEdit on the server
  (see `SCHEMATICS.md`).
- **Naval live-feel knobs** — harpoon velocity-carry (0.6) and hullpiercer
  drop rate; both need live play to judge.

---

## §4. Return-day checklist (mirror of ROADMAP, one place)
1. Upgrade Minehut to 6 GB. 2. Update **FAWE**. 3. (optional) MythicMobs 5.x.
4. Download newest **DarkSea jar** from the latest green Actions run → replace
in `plugins/`. 5. Apply the 6 GB profile, restart. 6. (if using the Mythic
pack) copy `mythicmobs-pack/` in, restart. 7. `/ds reset full confirm`.
8. The timed sea reset ships **enabled** (6h) — turn it off while you set up.

---

## §5. If you go quiet
Default build order for §1: **1a → 1b → 1d → 1c → 1e → 1f.** I'll build,
CI-verify each, and keep the branch green. Redirect any time. Everything stays
on this branch; nothing merges or ships until you're back and say so.
