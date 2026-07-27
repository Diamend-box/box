# BoxPvP — Risk, Banking & Loss Spec

**Platform:** Paper 1.21.4, Minehut free plan.

**Status:** v5. Supersedes all previous versions. Earlier drafts contained coins, a sell step,
carry-capacity upgrades, box depletion, schematic refills and per-player build caps — **none of
those exist.** Discard older copies.

**Changes in v5:**

- **Cargo containment.** Whitelisted ore and compressed units cannot be moved into any *persistent*
  inventory. A chest was a free bank — dump, die losing nothing, retrieve. Closed at the transfer,
  not by banning containers. §3.
- **Manual drops stay legal** so teammates can trade, with a ~60s despawn on player-dropped
  whitelisted ore. Death drops keep the normal timer. §3.
- **Reseal decay removed outright**, not deferred. v3's trigger was circular and v4's proximity
  version killed legitimate tactical walls. §12, §18.
- **Clamp hooks revised.** `InventoryCloseEvent` is dropped — containment makes it redundant — but
  drop and craft are added, because both remove ore with no container involved. §4.
- **Three questions opened**: workstations vs. containers, the raw→ingot path, and whether the fee
  clock resets on entering spawn. §3, §19.

**Changes in v4** (retained for history):

- **Exposure clock pauses while combat-tagged** instead of accruing. A full reversal of the v3
  clause; it fixes an inversion where being attacked made banking *cheaper*. §5, §10.
- **Banking remains blocked while combat-tagged.** The v3 rule stands; the objection to it was
  withdrawn. §4, §5.
- **Survival clock gains an activity gate**, and does **not** pause during combat. §11.
- **No building restrictions.** Compressed output is a **custom non-placeable item**. §3, §12.
- **Collections anti-farm unified with the wipe tracker.** §14, §17.
- **Wipe mechanics specified**: no drops, batched, clears on zone rotation. §12, §17.

---

## 0. Design principles

Every rule below serves one of these. If an instruction appears to violate one, flag it rather than
implementing it.

1. **Losing minutes is possible; losing hours is not.** A death costs cargo, never progression. One
   bad fight must never end someone's account.
2. **No safe way to make progress.** Spawn is safe but unproductive. Everything productive happens
   where you can be killed.
3. **No purchasing power may arrive already-safe.** Anything a player can spend — ore, kill payouts,
   event prizes, crate contents — arrives as at-risk cargo that still has to be banked.
   **Scope:** this governs spendable value only. **Progression is explicitly exempt** — collections,
   skill nodes and unlocks are permanently safe by design, because that is what principle 1
   protects. Do not apply this rule to progression tracks.
4. **Shape behaviour with cost, never a hard block.** Timid play stays legal and pays for the
   privilege. A player told "no" logs off; a player charged more makes a choice. This also rules out
   restrictions that bind inexperienced players hardest while experienced players route around them.
5. **Prefer a counter to a restriction.** When a tactic is too strong, add an answer to it rather
   than forbidding it. A counter is content — it has a skill ceiling, it gives ore somewhere to go,
   and it turns a stalemate into a fight. A restriction produces nothing and mostly punishes new
   players.
6. **Progression-neutral by construction.** No mechanic keyed to an absolute resource amount. Mining
   rate is not constant, so any fixed threshold inverts as tiers ship — trivial for a maxed player,
   punishing for a new one.
7. **A tradeoff only exists if it is visible.** If the player has to guess the cost, it is not a
   decision, it is a tax.

---

## 1. World model

**The box** is a bedrock chamber. The shell is unbreakable — no dig-out, no escape, no external
approach. PvP is live throughout.

**Resource cubes** sit inside the chamber. Players mine them; blocks regenerate, so cubes never
deplete. There is no depletion race, no refill event, no schematic paste.

**Cover:** none natural. Flat open floor with cubes as the only terrain. Player-placed structures
are the sole cover, and are governed by §12.

**Spawn** is a safe regear point outside the box. Nothing productive happens there.

**Keep in mind while building:** ore is infinite, so ore is not the scarce resource — *time spent
exposed* is. A haul's entire value is the risk taken to hold it. The banking system is not a feature
of the economy; it **is** the economy.

---

## 2. Ore is the currency

No coins, no tokens, no second currency of any kind.

- Ore is spent directly on upgrades and skill nodes.
- The same mining action increments **collections** (§14), which are permanently loss-proof.
- One action feeds a lossy track and a safe track simultaneously. This is intentional and is what
  makes drop-on-death survivable.

**Only banked ore is spendable.** Unbanked ore is inert — it cannot buy upgrades, cannot unlock
nodes, cannot be used for anything.

This is load-bearing. If ore could be spent unbanked, buying an upgrade would itself be a cash-out:
at-risk ore into permanent progress with no fee, no channel, no zone. Making banking mandatory means
the fee is unavoidable, and carrying ore back to spawn to dodge it accomplishes nothing.

**Where upgrades are bought:** anywhere, at any time, via command or menu. Safe under principle 3
because the fee has already been paid at deposit. There is no reason to force a walk to spawn for a
transaction that carries no risk either way.

**Ore-typed upgrade costs.** Upgrades should require *specific materials*, not a generic quantity. A
node costing three different ore types forces movement around the chamber instead of letting a
player park on one cube. This is the cheapest available tool for keeping a small population
colliding — use it deliberately when pricing the tree.

---

## 3. Ore value table and ore-equivalents

**Required, and referenced by four other sections.** Removing the sell step removed the only place
ore had a number attached, but the need did not go away: §6 apportions one fee percentage across a
mixed-material deposit, §6 rounds in value terms, §8 gates announcements on deposit size, and §9's
drop floor is defined in mining-value terms.

Define one internal per-ore value weight table in config and have every section read from it.
Without this, three systems will each invent their own and disagree.

### Ore-equivalents

**Track everything in ore-equivalents, not materials.** One compressed iron unit counts as nine iron
ore against the same key.

This exists because the compressor (§15) converts material A into material B inside the player's
inventory. If banking tracked raw materials, compressing 64 banked iron ore into 7 compressed units
would drive `carried[IRON]` to 1, fire the clamp, and silently destroy 63 ore of paid-for protection
— with no symptom until the player dies holding what they thought was safe.

In ore-equivalents, compression is invisible to the banking system, `carried − banked` stays correct
in every compression state, §9's drop math works on mixed compressed/raw inventories for free, and
§15's rule that compressed units drop like raw ore falls out automatically instead of needing
separate handling.

**Custom items count.** Compressed output is a custom item (§15). The ore-equivalent lookup must
resolve it — the counting path reads the custom identity first and falls back to the vanilla
material, never the reverse.

### Cargo containment — the only storage rule

Whitelisted ore and compressed units **cannot be moved into any inventory other than the player's
own.** Blocks chests, ender chests, shulkers (placed or held), furnaces, hoppers, chest-carrying
entities, item frames. Cancel the transfer; no message spam beyond a brief actionbar note.

Without this, a chest is a free bank — dump unbanked ore, die losing nothing, retrieve after
respawn. No zone, no channel, no fee. Ender chests survive the wipe, and shulkers are the portable
version.

**`carried` must recurse into nested inventories anyway** as a backstop — one missed transfer path
reopens the hole.

**Manual drops are allowed** so teammates can trade, but player-dropped whitelisted ore despawns in
~60s. Death drops keep the normal timer so vultures get their window. This falls out of the event
split for free: the short timer is set in `PlayerDropItemEvent`, and §9's drops are spawned by
`dropItemNaturally` without passing through it.

> **OPEN — workstations are not containers.** "Any inventory other than the player's own" as written
> also blocks the crafting table, smithing table, enchanting table, anvil and grindstone — so no
> enchanting with lapis and no netherite upgrades. The property that matters is **persistence, not
> ownership**: a container holds ore while the player is offline, a workstation returns it on close.
> Restating the rule as *"any inventory that persists when the player walks away"* carves out
> workstations and still blocks every route listed above.
>
> **That reformulation does not settle furnaces**, which persist and so stay blocked — correctly, a
> furnace is a stash. So: **what is the intended raw-ore → ingot path?** If the custom compressor
> (§15) replaces smelting, this is a non-issue and should be stated. If vanilla furnaces are the
> path, containment breaks the core loop and needs a session-bound smelting station instead.

**The placement audit still applies.** Compressed output stays a custom non-placeable item, and no
whitelist entry may be a placeable vanilla block — in practice **ancient debris** plus **any ore
block obtainable intact via Silk Touch**. Placing a block drops `carried` without touching `banked`,
which would both stash value outside the risk system and silently destroy paid-for protection.
Assert the audit in a unit test so a later whitelist edit cannot reopen it.

---

## 4. Banking

Banking is an **action**, not a building. It marks ore as protected.

- Usable only inside an active bank zone (§8).
- Channel with duration scaling to deposit size (§7).
- **Breaks on player-caused damage only.** Fall damage and environmental chip damage must not cancel
  it.
- Also breaks on leaving the zone.
- **Cannot be started while combat-tagged** (§10). To bank, you must break contact first — see §5.
- **Cannot be started while immunity is active** (§16).
- On completion: fee deducted in ore and destroyed, remainder protected, exposure clock reset.

### Partial deposit interface

- **Holding a stack + sneak-use** → banks that stack only.
- **Empty hand + sneak-use** → banks everything.

**It must not be a GUI.** The v3 justification for that was "it has to work under fire," which the
tag block makes false — you can never be under fire while depositing. The interface stays anyway,
for two reasons that still hold: it has no moving parts, and it makes the §7 channel preview
coherent, since the duration resolves from what is in the player's hand.

Note that partial deposits are now **convenience, not an emergency valve** (§5). They are still
worth having — bank one valuable stack, keep mining with the rest — and the time anchor still makes
cherry-picking pointless, since the clock resets regardless of deposit size.

### Tracking — do not tag items

PDC-tagging banked items breaks on vanilla stack merging: bank 30 iron, mine 20 more, they merge
into a stack of 50, and the flag either spreads to unbanked ore or is lost. Players find this within
a day.

**Track banked quantities in ore-equivalents in player data:**

```
banked = { IRON: 288, GOLD: 108, ... }   // ore-equivalents
```

On death, the droppable amount per key is `carried − banked`, with `carried` also computed in
ore-equivalents across raw and compressed forms.

### The clamp — hook events, do not poll

`banked` must be clamped to `min(banked, carried)` whenever banked ore can leave the inventory.
**A periodic check has an exploit window by construction** — and so does a hook list that misses a
route, because the hole is the window between removal and re-acquisition, not the state at death.
Clamping lazily at death does not work: bank 288, remove it, mine 288 fresh, die, and
`min(288, 288)` protects ore that was never paid for. The clamp must fire *at removal*.

Hook the events:

- On purchase — must decrement `banked` directly at the point of spend
- On death
- `PlayerDropItemEvent` — §3 legalises manual drops, and a drop removes ore with no container
  involved
- `CraftItemEvent` — nine banked ingots become one iron block in the player's own 2×2 grid. The
  block has no ore-equivalent, because §3's audit keeps placeable vanilla blocks off the whitelist,
  so `carried` falls and `banked` does not. Uncraft afterwards and the cycle closes

`InventoryCloseEvent` is **no longer needed**: under §3's containment, ore cannot reach a container
in the first place.

**The trap is inherent to the clamp, so surface it rather than trimming hooks.** Any route that
reduces `carried` destroys paid-for protection silently — handing a stack to a teammate does it just
as surely as closing a chest did. Removing a hook does not remove the trap, it converts it into an
exploit. Fix it where it belongs: an actionbar line whenever protection is reduced
(`Protection reduced to N`), so the loss is never invisible.

Compression is already safe here and needs no hook, because §3 tracks everything in ore-equivalents
— see the note there.

Persist banked quantities across logout.

---

## 5. The deposit fee

**Anchor: accumulated exposure time since last deposit.**

| Exposure accrued | Fee |
|---|---|
| 0–2 min | 25% |
| 8+ min | 5% |
| between | linear |

All four values in config. **Current rate displayed continuously on the bossbar** — the bossbar is
reserved for this and nothing else.

- Fee is paid **in ore and destroyed**, not redistributed. Primary sink.
- Multiplied by the zone's own risk rate (§8), **with the product clamped** to a configured maximum.
  Otherwise a new player banking early at the starter zone eats the worst bracket *and* the worst
  zone rate compounding, and both penalties land hardest on the people least able to absorb them
  (principle 4).
- **Reset to zero on any deposit**, including partial.
- **Reset to zero on death.** Otherwise dying grants a cheap banking window on the next haul.

### Accrual rule — this is not a timestamp delta

**The clock is an accumulated counter incremented by a scheduled task, never a stored-timestamp
subtraction.** §17 requires time-based state to be reconstructed from a stored timestamp at startup
because free servers sleep — if the fee clock used that pattern, then bank → log out → log in → mine
one stack → bank at 5%, permanently.

**Accrue only when all three hold:**

- Player is outside spawn, **and**
- Player has broken a whitelisted block within the last ~30 seconds, **and**
- Player is **not** combat-tagged.

The mining condition stops a player sealing into a pocket and idling to earn the discount.

> **OPEN — should the clock reset on entering spawn?** Today it stalls there, which leaves one
> laundering route open now that §3 has closed chests. A teammate parked one step outside spawn,
> breaking a single whitelisted block every 30 seconds, reaches the 5% tier at no risk and banks
> hauls handed over by drop; ducking inside costs nothing because the clock is only paused.
> Resetting on spawn entry — alongside the existing deposit and death triggers — forces that mule to
> stand in the open for the full eight minutes holding a fortune, which is §11's bounty target,
> i.e. content rather than an exploit. **Cost: a player who steps into spawn mid-trip loses their
> tier.** That is defensible, since entering spawn *is* ending your exposure, and it does not touch
> the ordinary loop as long as bank zones sit outside spawn (§8) — confirm that before adopting.

### Combat pauses the clock — reversal of the v3 rule

**v3 accrued while combat-tagged. That was backwards and must not be reintroduced.**

The clock accrued during fights *and* §4 blocks banking while tagged. A player jumped at 2 minutes
and chased for three more accrued the whole time, broke contact, and banked the entire haul at a
better rate than the player who mined quietly and banked at 2 minutes. Being attacked was a
discount, and there was never a reason to bank early — pressure was a reason to wait.

**Frozen while tagged, the section's original claim becomes true again by a different route.** Eight
minutes of quiet mining reaches 5%; eight minutes with four of them contested sits near 15%.
Contested time simply does not count toward the discount, so a dangerous trip banks at a worse rate
than a quiet one with no special-case code.

- **Pause, never reset.** §5 has two reset triggers — deposit and death — and a freeze is neither.
  Being pinned for two minutes leaves the clock exactly where it was.
- The old rule's stated reason (a purely mining-gated clock stops accruing when a player flees,
  penalising them when exposure is highest) is answered by pause-not-reset: fleeing costs the
  opportunity to advance, not progress already made.

This also **dissolves the slap-farm hole** — under v3, two players trading one hit every 15 seconds
outside spawn accrued the discount at no risk. Under a freeze, doing that stalls both their clocks.

### What replaced the anti-denial valve

v3 claimed a camped player could bank a sliver in ~2 seconds at 5% and eat 25% on the remainder, so
pressure was bought off at a bad rate. **The tag block removes that**: under pressure you cannot
split a deposit at all, so you never pay the split cost. That paragraph described a mechanic that
does not exist and has been deleted.

**The cost of being camped now lands in two places instead:**

1. **Death risk.** You are holding the whole haul, and you cannot cash any of it out until you break
   contact.
2. **A frozen clock.** Sustained harassment holds your fee where it is. Someone willing to hit you
   once every fifteen seconds cannot stop you banking, but they can keep you at 25% for as long as
   they are willing to stand in the open next to a zone that broadcasts — and be vultured for it.

That second one is the soft version of denial: a cost, not a block (principle 4), with an answer
available (principle 5 — shield, disengage, §12 cover, or kill them), and it emerges from the freeze
rule rather than needing code of its own.

**Watch in playtest:** during the 0–8 minute ramp there is now a mild incentive to disengage rather
than fight, since fighting stalls the discount. Bounded — at cap, combat costs nothing — and §11's
kill reward pulls the other way. Do not pre-emptively mechanic around it.

---

## 6. Fee rounding

Rounding is not a detail here — partial deposits make any error trivially repeatable.

- Round **down** and a one-item deposit is free (25% of 1 → 0), spammable indefinitely.
- Round **up per material** and protecting 3 high-value ore costs 1 of them.

**Correct handling:**

1. Compute the deposit's total value once, in value terms, using §3's table.
2. Apply the fee rate to that total.
3. **Round up on the total.**
4. **Destroy from the lowest-value material present**, working upward if the lowest is insufficient.

Mixed hauls then pay their fee in cheap ore rather than expensive ore, and single-item deposits
self-punish.

---

## 7. Channel duration

Scales with deposit size. Sliver ≈ 2s, large deposit ≈ 8s. Both ends in config.

**This is not the counterweight to hoarding.** Duration scales linearly with size, so total seconds
spent channeling a given volume is roughly constant however the player splits it.

The real deterrent against a huge haul is §9: a big haul means you carried a big haul the whole
trip, and death takes a percentage of it rather than of a sliver.

**Tune the fee spread and the drop percentage. Leave the channel curve alone.**

---

## 8. Bank zones

- **Regions only. No buildings.** Defined by coordinates.
- Small, flat, **on open floor away from the resource cubes** — cubes are cover, and a zone tucked
  against one defeats the point.
- Inside the box. PvP fully enabled. **Not safe zones** — no immunity, no bubble.
- **One fixed zone**, higher fee, always available — a reliable fallback and a landmark players can
  name.
- **3–4 rotating candidate locations**, one active at a time, rotating every 10–15 minutes. **The
  next location is not announced until it opens.** Two predictable zones can be camped by a
  coordinated group; unannounced rotation among several cannot.
- **Per-zone risk rate:** safer/closer zones take a higher cut, deeper zones a lower one. Players
  pick their own risk tier.
- **Entry feedback:** title card on entry plus a compass or waypoint pointer. With no structure,
  this is the only visual anchor a new player gets. (Not the bossbar — that belongs to the fee rate.)
- **On rotation, clear player-placed blocks inside the new zone's no-build radius** (§12), or the
  first zone of every cycle can open pre-fortified.

### Visible tell during channeling

Particle column plus actionbar countdown at the deposit site. This is the point, not decoration: a
player cashing out is broadcasting a jackpot location, which manufactures a contested fight several
times an hour with no scheduling.

**Server-wide announcements gated on deposit size.** With a known fixed zone and a currently-active
rotating one, an announcement conveys timing rather than location — and if every deposit pings,
players tune it out within a day. Only hauls worth intercepting light up.

---

## 9. Death and loss

- **Only unbanked ore drops. Nothing else, ever.** Tools, gear, banked ore and all progression are
  permanently safe.
- **~60% of the unbanked ore drops at the death site**; the remainder evaporates. The evaporated
  portion is a second sink and stops exit-camping from being strictly more profitable than mining.
- **Items drop to the ground** via `dropItemNaturally` so third parties can vulture them.
  Deliberate: the killer inherits the haul, the exposure and the bank run, and a big enough robbery
  makes *them* the next target. Never route a payout anywhere pre-secured (principle 3).
- **Drop floor, ramped — not a cliff.** 0% at the floor, scaling to full at roughly 3× the floor. A
  hard cliff lets competent players hug the threshold and opt out of the risk system permanently.
- **Whitelisted ore only** counts toward the drop calculation, so junk-stuffing does nothing.
- **Combat logging counts as a death** and drops normally.
- **Environmental death while combat-tagged credits the last attacker.** Otherwise self-deletion is
  the standard way to deny a kill.
- **Repeat-kill guard:** sharply diminishing payout for repeat kills of the same victim within
  ~20 minutes.

### `PlayerDeathEvent` — correct sequence

With `keepInventory` true the drops list is never processed, so clearing it does nothing. The order
is:

1. `setKeepInventory(true)`, `setKeepLevel(true)`
2. Compute the droppable pool from `carried − banked` in ore-equivalents over whitelisted materials
3. **Remove those items from the player's inventory directly**
4. Spawn them with `dropItemNaturally`

---

## 10. Combat tag

Referenced by §4, §5, §9 and §12, so it needs one canonical definition.

- **Applied only when a player deals damage to another player, and only when damage actually
  lands.** Not proximity, not line of sight, not a missed swing.
- Duration ~15s, refreshed on each hit, both attacker and victim.
- While tagged: **cannot start a deposit** (§4), **exposure clock is frozen** (§5), logging out
  counts as a death (§9).

**The damage-landed requirement keeps its rule and loses its reason.** v3 justified it with "a
sealed player could hold a tag and earn the fee discount while invulnerable" — under §5's freeze,
holding a tag now *costs* you, so that scenario is dead. Keep the rule anyway, for the inverted
reason: without it, a fake tag would let a player freeze **someone else's** clock, and block their
banking, without ever committing to a fight. Same rule, opposite rationale — annotated so a later
pass doesn't see a void justification and delete it.

---

## 11. PvP kill reward

**The gap this closes:** with banking as a pure protection flag, killing a player who has already
banked pays nothing, so PvP collapses into hunting loaded miners only.

**Survival clock.** A second, separate clock: accumulated time outside spawn since the player's last
**player-caused** death.

- Banking does not reduce it. Only dying to another player resets it.
- **Environmental and self-inflicted deaths do not reset it.** Otherwise a fully-banked player with
  a high clock suicides for free to shed the bounty on their head.
- Accumulated counter, same treatment as §5 — never a timestamp delta.

### Activity gate — the hole §5 was fixed for was still open here

v3 accrued this on "time outside spawn" alone, with no activity condition. That let a player park an
alt in a corner for an hour, return, kill it and collect a capped bounty at zero risk; §9's
20-minute repeat guard never bites because the cycle is hourly.

**Accrue only while outside spawn and having broken a whitelisted block within the last ~30 seconds
or being combat-tagged.**

**This clock does not pause during combat** — unlike §5's. The two clocks answer different
questions. §5 measures time spent producing, and a fight is not production. §11 measures time
survived, and a fight is the riskiest possible way to survive; freezing it would make the players
hardest to kill worth the least.

**On a kill:** spawn ore on the ground at the death site, scaled by the victim's survival clock
against a configured cap, subject to §9's repeat-kill guard.

Why this form:

- No new currency, so §18 holds. The reward is ore.
- **Principle 3 satisfied by construction** — it lands as items on the floor, unbanked, in a PvP
  zone. The killer inherits a haul, an exposure clock and a bank run, and third parties can vulture
  it exactly like §9 loot.
- Killing a banked player now pays, because their survival clock is high even when their deposit
  clock is zero.
- Alt-farming pays almost nothing without any alt detection, since a fed alt's survival clock resets
  on every death — and with the activity gate, a parked alt never accrues in the first place.
- The hunt points the right way: the most valuable target is whoever has been alive longest, who is
  usually also the strongest player. Streaks self-correct without a bounty system.

Ore is infinite (§1), so printing some is not inflationary in the usual sense, and the §5 sink still
applies — the killer pays a fee to keep any of it.

**Make the bounty visible** (principle 7). "The hunt points the right way" only works if players can
see where it points, and right now the clock is invisible to the carrier and to everyone hunting
them. Above a threshold, give it a tell — glow, particle, or a scoreboard line; **not** the bossbar,
which belongs to the fee rate. The victim gets a visible reason to play carefully, hunters get a
target, and no value changes hands, so principle 3 is untouched.

**Explicitly do not** pay this reward as a fee discount or a clock reduction. That is value arriving
pre-secured and fails principle 3 even though it is not an item.

---

## 12. Player building

**No per-player block caps. No build restrictions on defensive play.** Building is unrestricted
inside the box except at the two locations in "Access protection" below.

A wall that breaks in two seconds is not safety, it is notice — it tells you someone is coming. Even
a walled-off resource cube is breakable, visible, and costs a group thirty minutes of work that the
wipe erases; that is a raid target, which is content. A per-player cap, meanwhile, is a hard block
that binds inexperienced players hardest while experienced players route around it — principle 4,
the same reason the minimum deposit was rejected.

Unrestricted building also gives a coverless arena the thing it was missing: **territory.**
Landmarks, chokepoints, and a reason to return to a spot. It partially repairs §16's footrace
problem, since a chased player finally has something to break line of sight with — and it is what
makes §5's "break contact, then bank" rule achievable in practice.

**No restriction is needed on placing valuables.** See §3 — compressed output is a custom
non-placeable item and ordinary whitelist entries are items already, so the placement laundering
route closes at the whitelist rather than with a build rule.

### Build wipe cycle

- **Server-wide wipe of all player-placed blocks every 20–30 minutes** (config).
- **Announced at 60 seconds.**
- **The wipe removes blocks without dropping items.** A chamber-wide wipe that dropped would spawn
  thousands of entities on a free plan and hand out free blocks.
- **Removal is batched across ticks**, never one bulk operation. Tens of thousands of block updates
  in a single tick is a worse stall than anything §17 is written to avoid.
- Gives the half hour a shape: build, contest, teardown. Recovers some of the scheduled-event rhythm
  lost when box depletion was cut.

### Access protection — the only building restrictions

These are **location rules, not player limits** — they bind everyone identically and exist because
both would lock players out of a mandatory system with no available counter-play:

- **No-build radius around the active bank zone** (a radius, not just the zone itself — otherwise it
  gets ringed). Cleared on rotation, per §8.
- **No-build radius around box entrances**, or a group can wall people out of the game entirely.

### Reseal decay — removed

**Cut in v5, not deferred.** Both versions failed, in different ways. v3 decayed blocks placed while
combat-tagged, which was circular: §10 applies the tag only when damage lands, a sealed turtle
cannot be damaged, so the pre-seal never decayed and an attacker chewing cobble never generated the
tag that would make it decay. v4's proximity trigger worked but caught every tactical wall thrown up
in a fight, which is legitimate play the spec spent §12 protecting. **A stopgap that misfires on
normal play is worse than the gap it covers.**

The narrow problem it addressed is real and remains open: placement is instant and breaking takes a
second or two, so an attacker can never out-break a resealer. **The correct answer is §13** — a
counter, not a timer (principle 5) — and §19 already puts the suppression consumable first in the
build order.

**Partial cover in the meantime, stated precisely.** §5 accrues only while the player has broken a
whitelisted block in the last ~30 seconds, so a sealed player's fee rate stalls — turtling costs
them the thing they went out to earn. **This only bites below the cap.** A player already at eight
minutes and carrying a fortune has no further use for the clock, so sealing costs them nothing at
all — and that is exactly the player worth sealing against. Do not read the stall as a substitute
for §13.

> **Optional, if the top-end case needs cover before §13 ships:** decay the *clock*, not the blocks.
> After ~60s outside spawn with no accrual and no combat tag, the fee tier drifts back toward 25%.
> Combat continues to **stall** rather than decay, so fighting and repositioning stay safe; sitting
> sealed does not. No block tracking, no build rule, and it also reaches AFK-parked players and mule
> relays. **The cost is real:** a player who breaks contact and runs a long way to a zone loses tier
> for doing what §5 tells them to do, so the grace window has to be generous or the drift slow.
> Not adopted — recorded as the cheap option if bunkering proves worse than expected in playtest.

---

## 13. Counter-play — the real answer to bunkering

Principle 5 in practice. These replace restrictions rather than supplementing them.

**Design constraint:** any counter that is merely *faster breaking* loses by construction, because
the turtle answers it by placing another block. Counters must remove more per action than can be
replaced, ignore the wall, or attack the placement itself.

### Priority 1 — Suppression consumable *(build first; nothing else covers bunkering since v5)*

A thrown grenade or stackable lingering potion that **prevents block placement in a small radius for
a few seconds.**

Targets the loop directly rather than its output: the turtle can still mine out, they just cannot
reseal while it is active. Consumable, so it is a genuine ore sink, and it drops on death like any
other cargo.

**No-op inside §12's no-build radii** — otherwise it becomes a bank-zone grief with no counter.

### Priority 2 — Area breaking

A charged pickaxe swing breaking 3×3. Removes more per action than can be replaced. Natural fit as a
Mining branch node (§14) rather than a consumable.

### Priority 3 — Blink / short teleport *(optional)*

Ignores the wall entirely — the bunker stops being a barrier and becomes a box the attacker is now
inside. Most fun, hardest to balance on a map where escape is already strong (§16). Ship last, if at
all.

### Rules for all counter items

- **Consumable or ore-gated**, never a free ability — countering a bunker should cost something, so
  the turtle's stall retains value.
- **They are cargo.** They sit in the inventory and drop on death like ore. Principle 3.

---

## 14. Collections and skill tree

- **Increment on blocks mined**, not on ore banked or spent. This is what makes them loss-proof and
  is why a drop-on-death economy is survivable. Exempt from principle 3 by §0.3.
- Collections gate skill nodes; **ore pays for them.** Two keys, so a single-ore grinder cannot
  beeline a branch.
- **Gate cube or area access by collection tier rather than by wealth.** Access earned through
  mining variety rather than purchase is the cleanest anti-pay-to-win statement available, and worth
  advertising.
- **Dead-collection problem:** once players outgrow the starter cubes, low-tier collections stall.
  Seed a small percentage of low-tier ore into higher cubes so every collection keeps trickling.

**Tree shape:** three branches — Mining / Hauling / Combat — roughly 25–30 nodes total. More is
unreadable in a GUI. Respec on a cooldown so nobody respecs mid-fight. Area breaking (§13) lives in
Mining.

### Anti-farm cache must be replaced, not reused

`CollectionListener` remembers player-placed blocks so place-and-break cannot farm a collection, but
at 250,000 entries it **clears the entire set** (`CollectionListener.java:101`) rather than evicting.
Every placed block in the world becomes "not placed" simultaneously, and place-and-break works on all
of them until the cache refills. Under per-player build caps that ceiling was unreachable; with
unrestricted building on a chamber-sized arena it is not. The keys are `world:x,y,z` strings, which
is also tens of megabytes at that size.

**Point collections at §17's wipe tracker instead and delete the bounded set.** One structure, no
eviction, packed longs instead of strings, and the wipe clears it on a cycle so it never grows
without bound.

---

## 15. Auto-compressor

Unlocked **per ore type via that ore's collection tier.**

It is a **capacity** tool: it lets a player stay out longer before inventory space forces them back.
Keep it bounded — roughly three tiers, ~4× effective capacity, not open-ended.

**Output is a custom, non-placeable item.** This is load-bearing beyond flavour: it is what closes
the placement laundering route in §3 without any building restriction. Do not replace it with vanilla
storage blocks.

**Compressed units drop on death exactly like raw ore.** Under §3's ore-equivalents this requires no
special handling; it falls out of the tracking model. Do not add a rule making compressed material
safe — that would be a second protection route bypassing the bank entirely, a free cash-out with no
zone, channel or fee. **The compressor buys time in the box, never safety.**

Toggleable (`/compress off`) so players choose their own exposure profile.

**Wording note:** §18 rejects *carry-capacity upgrades* as a system other mechanics may read from.
The compressor is not that — nothing in the fee, drop or clock math reads a capacity value. These are
not in conflict.

---

## 16. Consequences of a coverless arena

**Movement speed is the dominant stat.** With little natural cover, escaping is close to a pure
footrace and chases end only when someone runs out of upgrades. The escape-related nodes need a
**hard ceiling**, not a soft curve. §12's persistent structures partially mitigate this, but the
ceiling is still required — and note that §4's tag block now makes disengagement *mandatory* before
banking, so escape strength has a second lever on it that did not exist in earlier drafts.

**Entering the box is the most dangerous moment in the loop.** Provide **multiple entrances** spread
around the shell, plus brief immunity on entry.

**Immunity rules — all three required:**

- Breaks the instant the player attacks.
- **Breaks on any offensive assist**, not only direct damage — an immune player is otherwise an
  uninterruptible body-blocker for a teammate.
- **Breaks on entering a bank zone**, and banking cannot be started while immune (§4). An immune
  player cannot take damage, so an immune player cannot have a deposit interrupted — if a zone is
  reachable within the immunity window, banking becomes free and uninterruptible, deleting §8
  entirely for anyone who plans a route.

---

## 17. Performance — Minehut free plan

Regenerating cubes mean `BlockBreakEvent` fires at very high volume. That path must be lean.

- **No storage writes in `BlockBreakEvent`.** Collections counters live in memory; flush on quit plus
  a periodic timer.
- Cube regeneration lightweight per-block, never a schematic operation.
- **Zone checks** on a scheduled task or throttled move handler — never per-tick per-player.
- **Zone rotation timing computed from a stored timestamp at startup.** Free servers sleep when
  empty, so scheduled tasks do not survive; anything wall-clock-based must be reconstructed on boot.
- **Exposure and survival clocks are the exception** — they are accumulated counters incremented by
  a task, *not* reconstructed from timestamps, for the reason in §5.
- **One placed-block tracker, unbounded by design.** Packed longs in a chunk-keyed map, never
  `Location` objects. It serves two consumers: the wipe (§12) and the collections anti-farm (§14).
  *(v5: reseal decay was the third; it is gone.)* Cleared on wipe, which is what keeps it bounded in
  practice.
- **Wipe removal batched across ticks** and drop-free (§12).

---

## 18. Explicitly rejected

Do not reintroduce. Each was considered and cut for a stated reason.

| Rejected | Why |
|---|---|
| **Coins / any second currency** | Ore is the currency. A separate balance creates value that has already cleared the risk system. |
| **A sell step** | Nothing to sell for. Ore is spent directly. |
| **Box depletion, refill timers, schematic paste** | Cubes regenerate. Scarcity is time, not ore. |
| **Minimum deposit amount** | Doesn't produce sustained exposure — carry oscillates 0→N→0 and the early part of every trip sits under the drop floor. Inverts with progression, and hard-blocking a cash-out pushes players offline rather than back into the box. |
| **Banking cooldown** | Creates dead time. Makes the game slower rather than riskier; boredom loses players faster than deaths do. |
| **Per-player build caps** | Principle 4. Binds inexperienced players hardest; experienced players route around it. Replaced by §13 counter-play. |
| **Restricting which blocks players may place** | Unnecessary once compressed output is a custom non-placeable item. Handled at the whitelist instead (§3). |
| **Carry-capacity upgrades as a readable system** | Not being built. Nothing in fee, drop or clock math may depend on a capacity value. |
| **Safe compressed blocks** | A second protection route bypassing the bank. |
| **Fee keyed to fraction of inventory** | Depends on a capacity system that doesn't exist, and is junk-stuffable. |
| **Fee clock as a stored-timestamp delta** | Ticks while the server sleeps; bank → relog → bank at 5% permanently. |
| **Fee clock accruing during combat** | v3 rule. Combined with §4's tag block it made being attacked a *discount* — pressure became a reason to wait rather than bank. Replaced by a freeze (§5). |
| **Storing unbanked ore in any container** | A free bank with no zone, channel or fee. Closed at the transfer, not by banning containers — chests stay useful for gear, and workstations are not containers (§3). |
| **Reseal decay (any trigger)** | v3's combat-tag version was circular; v4's proximity version killed legitimate tactical walls. Cut in v5. §5's clock stalling while sealed covers the case *below the fee cap only* — it is not a substitute for §13, which remains the answer. |
| **Kill reward as a fee discount or clock reduction** | Value arriving pre-secured. Principle 3. |

---

## 19. Standing note

**Push back with reasoning rather than implementing something you think is wrong.** Review passes
have now caught six ship-breaking errors across three revisions. That has been worth far more than
compliance — including the passes where the objection was wrong and got withdrawn.

**All numbers are playtest starting points.** The values most worth testing first are the fee spread
(25%/5%) and the death drop percentage (60%) — that is where player behaviour actually lives. Third
on the list is whether §5's freeze makes players avoid fights during the ramp.

**When proposing anything new**, check it against principle 3 (no *spendable* value arrives
pre-secured) and principle 5 (prefer a counter to a restriction), and say so explicitly in the
proposal.

**Build order suggestion:** §13's suppression consumable is the highest-leverage item on the list.
v5 cut §12's stopgap outright, so bunkering is now uncountered until this ships — that is a known,
accepted gap, not an oversight.

### Open questions

- **§3 — workstations vs. containers.** Restate containment as "any inventory that persists when the
  player walks away," so enchanting and smithing keep working? Nothing else in the spec depends on
  the ownership phrasing.
- **§3 — the raw-ore → ingot path.** Does the §15 compressor replace smelting entirely? If vanilla
  furnaces are the path, containment blocks the core loop and needs a session-bound station instead.
  **This one blocks implementation of §3.**
- **§5 — reset the fee clock on entering spawn?** Closes the mule-relay route left open once §3
  closed chests. Depends on bank zones sitting outside spawn.
- **§12 — clock decay**, if bunkering proves worse than expected before §13 lands. Recorded as
  optional, not adopted.
