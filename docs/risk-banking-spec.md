# BoxPvP — Risk, Banking & Loss Spec

**Platform:** Paper 1.21.4, Minehut free plan.

**Status: v7 — official, condensed.** No rules changed in this pass; the explanation was cut down to
the decisions. Build against this document. Earlier drafts contained coins, a sell step,
carry-capacity upgrades, box depletion, schematic refills, per-player build caps, ore as a crafting
ingredient, vanilla furnaces and a reseal decay rule. **None of those exist.** Discard older copies.

**All numbers are starting points for playtest**, especially the fee spread (25%/5%) and the death
drop percentage (60%).

---

## What this is

**Semi keep-inventory.** You keep everything when you die — gear, tools, levels, collections, skill
nodes — except ore you have not banked yet. Ore is the only currency, and it is only spendable once
banked. Banking happens at a zone inside the PvP box, takes a few seconds, and costs a fee that
starts at 25% and falls to 5% the longer you have been out mining. Die holding unbanked ore and most
of it drops on the floor for whoever killed you. That is the whole loop: mine, decide when the fee is
low enough to be worth the walk, survive the walk.

---

## 0. Design principles

1. **Losing minutes is possible; losing hours is not.** A death costs cargo, never progression.
2. **No safe way to make progress.** Spawn is safe but unproductive.
3. **No purchasing power arrives already-safe.** Ore, kill payouts, event prizes and crate contents
   all arrive as at-risk cargo. **Progression is exempt** — collections and skill nodes are
   permanently safe by design.
4. **Shape behaviour with cost, never a hard block.** Timid play stays legal and pays for it.
5. **Prefer a counter to a restriction.** A counter is content; a restriction produces nothing.
6. **No mechanic keyed to an absolute resource amount.** Fixed thresholds invert as tiers ship.
7. **A tradeoff only exists if it is visible.**

---

## 1. World model

- **The box** is a bedrock chamber with an unbreakable shell. PvP is live throughout.
- **Resource cubes** inside regenerate, so they never deplete. No refill event, no schematic paste.
- **No natural cover.** Player-placed structures are the only cover (§12).
- **Spawn** is a safe regear point outside the box. Nothing productive happens there.

Ore is infinite, so ore is not the scarce resource — *time spent exposed* is.

---

## 2. Ore is the currency

- No coins, tokens or second currency.
- Ore is spent directly on gear, upgrades and skill nodes.
- The same mining action increments **collections** (§14), which are loss-proof. One action feeds a
  lossy track and a safe track at once — that is what makes drop-on-death survivable.
- **Only banked ore is spendable.** Unbanked ore is inert.

**Ore is never a crafting ingredient.** Gear, upgrades and §13's consumables are bought from a menu
with banked ore, purchasable anywhere at any time, including while combat-tagged. Nothing needs a
special case for crafting, smithing, anvil or enchanting tables. *Letting players craft gear from ore
would be a free cash-out into permanently safe value.*

**Accepted exceptions:** enchanting takes lapis and anvil repair takes diamonds. Both run on banked
ore at §3's 1:1 spend. Anvil repair with ingots costs nothing extra (ingots are post-bank material).

**Price upgrades in specific materials, not generic quantity.** A node costing three ore types forces
movement around the chamber instead of letting a player park on one cube.

---

## 3. Ore value, ore-equivalents and containment

### Value

**One raw whitelisted item = one ore-equivalent, every material, no exceptions.** A diamond and a
lump of coal both count 1. There is no weight table and no config knob for one. There is no sell step
and gear is priced in banked ore, so materials have no distinguishable worth to encode.

§6, §8 and §9 all read this. **Route them through one shared accessor**, not three inline counts —
the value is uniform but the *counting* is not (compression, custom items and the whitelist check
live behind it).

### Ore-equivalents

**Track ore-equivalents, not materials.** One compressed iron unit counts as nine iron against the
same key. *Without this, compressing 64 banked iron would drop `carried[IRON]` to 7, fire the clamp,
and silently destroy 63 ore of paid-for protection.*

**Custom items count.** The lookup reads the custom identity first and falls back to the vanilla
material, never the reverse.

### Containment

**Unbanked whitelisted ore and compressed units cannot move into any inventory other than the
player's own.** Blocks chests, ender chests, shulkers, hoppers, chest-carrying entities, item frames.
Cancel the transfer, brief actionbar note, no spam. *Otherwise a chest is a free bank.*

**Banked ore may leave, and leaving spends its protection 1:1.** A transfer of N ore-equivalents out
is permitted only while `banked ≥ N`, and **decrements `banked` by N**. *Without the decrement, one
fee protects 100 in a chest and 100 on your person.*

**`carried` must recurse into nested inventories** as a backstop.

**Boundaries:**

- The player's own **2×2 crafting grid is their own inventory.** Unbanked ore may sit there and
  `carried` must count it. *Otherwise staging a craft looks like removal and fires the clamp.*
- A crafting table's **3×3 grid is not.** Unbanked ore cannot enter it; banked ore entering spends
  protection 1:1 at the transfer hook, so every 3×3 storage-block recipe is already accounted for.
- The live 2×2 case is quartz — four nether quartz to a quartz block — which is why §4 hooks
  `CraftItemEvent`.

### Smelting

**The whitelist is raw forms only:** raw iron/gold/copper, diamond, emerald, coal, lapis, redstone,
quartz, netherite scrap. Ingots and other smelted output are not whitelisted, carry no
ore-equivalent, do not drop on death, and are not contained. *The input already paid its fee and 1:1
spend consumed its protection; charging the output again would mean paying twice to end up worse.*

**Furnaces are disabled server-wide.** The smelting NPC is the sole route from ore to ingots, so its
two rules are load-bearing and have no fallback:

- **Banked-ore input only.** *Unbanked input is a fee-free conversion into post-bank material.*
- **It must never hold ore across a logout.** Consumed and returned in the same interaction — no
  queue, no pending slot. *If it stores, it is a free bank and the rest of §3 is decoration.*

**Manual drops are allowed** so teammates can trade, but player-dropped whitelisted ore despawns in
~60s. Set the short timer in `PlayerDropItemEvent`; §9's drops bypass it via `dropItemNaturally`.

### Placement audit

Placing a block drops `carried` without touching `banked`. Compressed output is a custom
non-placeable item and every ordinary whitelist entry is already an item, so this is a one-time
audit: **no whitelist entry may be a placeable vanilla block.** In practice that means ancient debris
and any ore block obtainable intact via Silk Touch. **Assert it in a unit test.**

---

## 4. Banking

Banking is an **action**, not a building. It marks ore as protected.

- Usable only inside an active bank zone (§8).
- Channel duration scales with deposit size (§7).
- **Breaks on player-caused damage only.** Not fall damage, not environmental chip damage.
- Also breaks on leaving the zone.
- **Cannot start while combat-tagged** (§10) or **while immunity is active** (§16).
- On completion: fee deducted in ore and destroyed, remainder protected, exposure clock reset.

**Partial deposits:** holding a stack + sneak-use banks that stack; empty hand + sneak-use banks
everything. Not a GUI. Convenience, not an emergency valve — the tag block means you are never under
fire while depositing.

### Tracking — do not tag items

PDC tags break on vanilla stack merging. Track quantities in player data instead:

```
banked = { IRON: 288, GOLD: 108, ... }   // ore-equivalents
```

Droppable per key is `carried − banked`. Persist across logout.

### The clamp — hook events, do not poll

`banked` must be clamped to `min(banked, carried)` **at the moment of removal**, not on a timer and
not lazily at death. *Bank 288, remove it, mine 288 fresh, die — `min(288, 288)` protects ore that
never paid.*

Hook:

- **On purchase** — decrement directly at the point of spend
- **On death**
- **`PlayerDropItemEvent`** — manual drops remove ore with no container involved
- **`CraftItemEvent`** — the quartz case in the player's own 2×2, which no transfer hook sees
- **On transfer out** — `InventoryClickEvent`, `InventoryDragEvent`, `InventoryMoveItemEvent`. Same
  hook as §3's containment, doing double duty: cancel if unbanked, decrement if banked.

`InventoryCloseEvent` is the wrong event — it fires too late to tell how much moved and in which
direction, and §3 needs the decision at the transfer in order to cancel it.

**Any route that reduces `carried` destroys paid-for protection**, including handing a stack to a
teammate. Removing a hook converts that trap into an exploit rather than fixing it. Surface it
instead: actionbar `Protection reduced to N` whenever it happens.

---

## 5. The deposit fee

**Anchor: accumulated exposure time since last deposit.**

| Exposure accrued | Fee |
|---|---|
| 0–2 min | 25% |
| 8+ min | 5% |
| between | linear |

- All four values in config. **Rate displayed continuously on the bossbar**, which is reserved for
  this and nothing else.
- Fee is **paid in ore and destroyed**. Primary sink.
- Multiplied by the zone's risk rate (§8), **product clamped** to a configured maximum. *Otherwise a
  new player eats the worst bracket and the worst zone rate compounding.*
- **Reset to zero on any deposit** (including partial), **on death**, and **on entering spawn**.

**The clock is an accumulated counter incremented by a scheduled task, never a stored-timestamp
delta.** *A timestamp delta ticks while the server sleeps: bank → log out → log in → bank at 5%,
permanently.*

**Accrue only when all three hold:** outside spawn, **and** broke a whitelisted block in the last
~30s, **and** not combat-tagged. *The mining condition stops a player sealing in and idling for the
discount.*

**Combat pauses the clock — pause, never reset.** *Accruing during a fight made being attacked a
discount: pressure became a reason to wait rather than bank.* Frozen, the table means what it says —
eight quiet minutes reach 5%, eight minutes with four contested sit near 15%. This also dissolves the
slap-farm hole, since two players trading hits stall both their clocks.

**Spawn resets the clock.** *Pausing left a mule relay open: B idles outside spawn breaking one block
every 30s, reaches 5% at zero risk, takes A's haul and banks it, ducking into spawn whenever
threatened.* Under a reset B has to stand in the open for eight minutes holding a fortune, which
makes them §11's bounty target. **Cost, accepted:** a chased player who escapes into spawn loses
their tier. **Requires bank zones outside spawn** (§8) — confirmed.

**Denial is real and intended.** The tag lasts ~15s and refreshes on every hit, so a player willing
to land one hit every fifteen seconds **can stop you banking indefinitely.** That is a block, not a
rate penalty. It is correct because landing repeated hits is a fight — the attacker stays on you, in
the open, next to a broadcasting zone, taking every counter-attack and every third party. The answer
is to kill or escape them. Principle 4 governs what the *system* forbids, not what another player
imposes.

**Watch in playtest:** during the 0–8 minute ramp there is a mild incentive to disengage rather than
fight. Bounded (at cap, combat costs nothing) and §11 pulls the other way. Do not pre-empt it.

---

## 6. Fee rounding

Partial deposits make any rounding error trivially repeatable. Round down and a one-item deposit is
free and spammable; round up per material and a mixed deposit is overcharged once per type it
contains.

1. Count the deposit's total ore-equivalents.
2. Apply the fee rate to that total.
3. **Round up on the total.**
4. **Take the same percentage off every material in the deposit** — a 10% fee takes 10% of the iron,
   10% of the coal and 10% of the diamonds.

**Mechanics:** target per material is `fee × (that material's share of the deposit)`, allocated by
largest remainder so the destroyed total equals the fee exactly. **Show what was destroyed** in the
deposit summary.

**The pool is the deposit, never the player's holdings.** *With banked ore in the pool, depositing 10
iron while holding 1,000 banked would source the fee from ore already protected, and the optimal play
becomes never holding banked ore.* The fee is a cut of the transaction, taken before the material
becomes protected.

**Tiny deposits are the remaining edge.** One diamond rounds up to a whole diamond — a 100% fee.
Accepted: self-punishing tiny deposits are what stop round-down spam, and nothing is gated behind
that diamond since gear is priced in the menu.

---

## 7. Channel duration

Scales with deposit size, sliver ≈ 2s to large ≈ 8s, both ends in config.

**This is not the counterweight to hoarding** — duration scales linearly, so total seconds for a
given volume is roughly constant however it is split. The deterrent against a huge haul is §9. Tune
the fee spread and the drop percentage; leave the channel curve alone.

---

## 8. Bank zones

- **Regions only, no buildings.** Defined by coordinates.
- Small, flat, **on open floor away from the resource cubes** — cubes are cover.
- Inside the box, PvP fully enabled. **Not safe zones.**
- **Outside spawn**, which §5's reset depends on.
- **One fixed zone**, higher fee, always available — a fallback and a landmark players can name.
- **3–4 rotating locations**, one active at a time, rotating every 10–15 min. **The next location is
  not announced until it opens.** *Predictable zones get camped by coordinated groups.*
- **Per-zone risk rate:** safer zones take a higher cut, deeper zones a lower one.
- **Entry feedback:** title card plus compass/waypoint pointer. Not the bossbar.
- **On rotation, clear player-placed blocks inside the new zone's no-build radius** (§12), or the
  first zone of every cycle opens pre-fortified.

**Visible tell while channeling:** particle column plus actionbar countdown. This is the point, not
decoration — a player cashing out broadcasts a jackpot location, which manufactures contested fights
with no scheduling. **Server-wide announcements gated on deposit size**, so only hauls worth
intercepting light up.

---

## 9. Death and loss

- **Only unbanked ore drops. Nothing else, ever.** Tools, gear, banked ore and progression are safe.
- **~60% of unbanked ore drops at the death site**; the remainder evaporates as a second sink.
- **Drops to the ground** via `dropItemNaturally` so third parties can vulture them. The killer
  inherits the haul, the exposure and the bank run.
- **Drop floor, ramped:** 0% at the floor scaling to full at ~3× the floor. *A hard cliff lets
  players hug the threshold and opt out of the risk system permanently.*
- **Whitelisted ore only** counts, so junk-stuffing does nothing.
- **Combat logging counts as a death** and drops normally.
- **Environmental death while tagged credits the last attacker.** *Otherwise self-deletion denies the
  kill.*
- **Repeat-kill guard:** sharply diminishing payout for repeat kills of the same victim within ~20min.

**`PlayerDeathEvent` sequence.** With `keepInventory` true the drops list is never processed, so
clearing it does nothing:

1. `setKeepInventory(true)`, `setKeepLevel(true)`
2. Compute the droppable pool from `carried − banked` over whitelisted materials
3. **Remove those items directly, from every inventory step 2 counted** — main inventory, hotbar,
   offhand, and the player's own 2×2 grid
4. Spawn them with `dropItemNaturally`

**Derive steps 2 and 3 from one list of inventory surfaces**, not two written out separately. *If
counting includes the 2×2 grid and removal does not, the player keeps the difference — a free stash
slot that survives death.* Vanilla drops the crafting grid on death, but `keepInventory` suppresses
that, so the grid is only emptied if step 3 empties it.

---

## 10. Combat tag

- **Applied only when a player deals damage to another player, and only when damage lands.** Not
  proximity, not line of sight, not a missed swing. *Otherwise a fake tag freezes someone else's
  clock and blocks their banking without committing to a fight.*
- Duration ~15s, refreshed on each hit, on both attacker and victim.
- While tagged: cannot start a deposit (§4), exposure clock frozen (§5), logout counts as a death (§9).

---

## 11. PvP kill reward

**The gap:** with banking as a pure protection flag, killing an already-banked player pays nothing,
so PvP collapses into hunting loaded miners only.

**Survival clock:** accumulated time outside spawn since the player's last **player-caused** death.

- Banking does not reduce it. Only dying to another player resets it.
- **Environmental and self-inflicted deaths do not reset it.** *Otherwise a banked player suicides to
  shed the bounty.*
- Accumulated counter, same as §5 — never a timestamp delta.
- **Activity gate:** accrue only while outside spawn **and** having broken a whitelisted block in the
  last ~30s **or** being combat-tagged. *Otherwise a parked alt accrues for an hour at zero risk.*
- **This clock does not pause during combat**, unlike §5's. §5 measures production and a fight is not
  production; §11 measures survival and a fight is the riskiest way to survive.

**On a kill:** spawn ore on the ground at the death site, scaled by the victim's survival clock
against a configured cap, subject to §9's repeat-kill guard. It lands unbanked in a PvP zone, so the
killer inherits a haul, a clock and a bank run, and third parties can vulture it.

**Make the bounty visible** above a threshold — glow, particle or scoreboard line, not the bossbar.
The hunt only points the right way if players can see where it points.

**Never pay this as a fee discount or clock reduction.** That is value arriving pre-secured.

---

## 12. Player building

**Unrestricted inside the box**, except at the two locations below. No per-player block caps.

A wall that breaks in two seconds is not safety, it is notice. A walled-off cube is a raid target,
which is content. Unrestricted building also gives a coverless arena **territory** — landmarks,
chokepoints, and the line-of-sight breaks that make §5's "break contact, then bank" achievable.

**No restriction is needed on placing valuables** — the laundering route closes at §3's whitelist
audit, not with a build rule.

**Build wipe cycle:**

- Server-wide wipe of all player-placed blocks every 20–30 min (config), **announced at 60 seconds**.
- **Removes blocks without dropping items.** *A chamber-wide drop spawns thousands of entities.*
- **Batched across ticks**, never one bulk operation.

**Access protection — the only building restrictions.** Location rules, not player limits:

- **No-build radius around the active bank zone** (a radius, or it gets ringed). Cleared on rotation.
- **No-build radius around box entrances**, or a group walls people out of the game.

**Bunkering is uncountered until §13 ships, and that is accepted.** There is no reseal decay rule —
the combat-tag version was circular (a sealed player cannot be damaged) and the proximity version
killed legitimate tactical walls. §5's clock stalls while sealed, but **only below the fee cap**: a
player already at eight minutes holding a fortune loses nothing by sealing, and that is exactly the
player worth sealing against. §13 is the answer, which is why it is first in the build order.

---

## 13. Counter-play

**Design constraint:** any counter that is merely *faster breaking* loses, because the turtle answers
it by placing another block. Counters must remove more per action than can be replaced, ignore the
wall, or attack the placement itself.

1. **Suppression consumable** *(build first)* — thrown item that **prevents block placement in a
   small radius for a few seconds.** Targets the loop, not its output: the turtle can mine out but
   cannot reseal. **No-op inside §12's no-build radii**, or it becomes a bank-zone grief.
2. **Area breaking** — charged pickaxe swing breaking 3×3. Fits as a Mining branch node (§14).
3. **Blink / short teleport** *(optional)* — ignores the wall entirely. Hardest to balance on a map
   where escape is already strong (§16). Ship last, if at all.

**All counter items are bought with banked ore** from §2's menu, never crafted and never free, and
**they are cargo** — they sit in the inventory and drop on death like ore.

---

## 14. Collections and skill tree

- **Increment on blocks mined**, not on ore banked or spent. This is what makes them loss-proof.
- Collections gate skill nodes; **ore pays for them.** Two keys, so a single-ore grinder cannot
  beeline a branch.
- **Gate cube or area access by collection tier rather than by wealth.** Cleanest anti-pay-to-win
  statement available.
- **Dead-collection problem:** seed a small percentage of low-tier ore into higher cubes so every
  collection keeps trickling.
- **Tree shape:** three branches — Mining / Hauling / Combat — roughly 25–30 nodes. Respec on a
  cooldown so nobody respecs mid-fight.

**Anti-farm cache must be replaced, not reused.** `CollectionListener` remembers player-placed blocks
so place-and-break cannot farm a collection, but at 250,000 entries it **clears the entire set**
(`CollectionListener.java:101`) rather than evicting — every placed block becomes "not placed" at
once. Keys are `world:x,y,z` strings, tens of megabytes at that size. **Point collections at §17's
wipe tracker and delete the bounded set.**

---

## 15. Auto-compressor

- Unlocked **per ore type via that ore's collection tier.**
- A **capacity** tool: it lets a player stay out longer. Keep it bounded — ~3 tiers, ~4× capacity.
- **Output is a custom, non-placeable item.** Load-bearing: it closes §3's placement laundering route
  without a building restriction. Do not replace it with vanilla storage blocks.
- **Compressed units drop on death exactly like raw ore.** Falls out of ore-equivalents with no
  special handling. **The compressor buys time in the box, never safety.**
- Toggleable (`/compress off`).

---

## 16. Coverless arena

**Movement speed is the dominant stat.** With little natural cover, escaping is close to a pure
footrace. Escape-related nodes need a **hard ceiling**, not a soft curve.

**Entering the box is the most dangerous moment in the loop.** Provide **multiple entrances** spread
around the shell, plus brief immunity on entry.

**Immunity rules — all three required:**

- Breaks the instant the player attacks.
- **Breaks on any offensive assist**, not only direct damage. *Otherwise an immune player is an
  uninterruptible body-blocker.*
- **Breaks on entering a bank zone**, and banking cannot start while immune. *An immune player cannot
  be interrupted, so a zone reachable within the window makes banking free.*

---

## 17. Performance — Minehut free plan

Regenerating cubes mean `BlockBreakEvent` fires at very high volume. That path must be lean.

- **No storage writes in `BlockBreakEvent`.** Counters in memory; flush on quit plus a timer.
- Cube regeneration lightweight per-block, never a schematic operation.
- **Zone checks** on a scheduled task or throttled move handler, never per-tick per-player.
- **Zone rotation timing computed from a stored timestamp at startup.** *Free servers sleep, so
  scheduled tasks do not survive.*
- **Exposure and survival clocks are the exception** — accumulated counters, not reconstructed from
  timestamps, for the reason in §5.
- **One placed-block tracker, unbounded by design.** Packed longs in a chunk-keyed map, never
  `Location` objects. Two consumers: the wipe (§12) and collections anti-farm (§14). Cleared on wipe.
- **Wipe removal batched across ticks** and drop-free.
- **Containment and clamp checks are per-transfer, not per-tick.** Nothing polls.

---

## 18. Explicitly rejected

Do not reintroduce.

| Rejected | Why |
|---|---|
| **Coins / any second currency** | Ore is the currency. A separate balance has already cleared the risk system. |
| **A sell step** | Nothing to sell for. Ore is spent directly. |
| **Box depletion, refill timers, schematic paste** | Cubes regenerate. Scarcity is time, not ore. |
| **Per-material ore value weights** | Every ore counts 1. With no sell step and gear priced in banked ore, materials have no distinguishable worth to encode. |
| **Minimum deposit amount** | Doesn't produce sustained exposure, inverts with progression, and pushes players offline rather than back into the box. |
| **Banking cooldown** | Dead time. Boredom loses players faster than deaths do. |
| **Per-player build caps** | Principle 4. Binds new players hardest; experienced players route around it. Replaced by §13. |
| **Restricting which blocks players may place** | Unnecessary once compressed output is non-placeable. Handled at §3's whitelist audit. |
| **Storing unbanked ore in any container** | A free bank with no zone, channel or fee. Closed at the transfer, not by banning containers. |
| **Crafting gear from ore** | A free cash-out into permanently safe value. Gear is bought with banked ore (§2). |
| **Zero-fee floor for small deposits** | Makes the rate irrelevant — §6's spam case by another door. Early-game relief belongs in §2's pricing. |
| **Paying the fee from ore on hand** | Sources the fee from outside the deposit, eroding previously banked ore. Replaced by proportional draw. |
| **Carry-capacity upgrades as a readable system** | Not being built. Nothing in fee, drop or clock math may read a capacity value. |
| **Safe compressed blocks** | A second protection route bypassing the bank. |
| **Fee keyed to fraction of inventory** | Depends on a capacity system that doesn't exist, and is junk-stuffable. |
| **Fee clock as a stored-timestamp delta** | Ticks while the server sleeps; bank → relog → bank at 5% permanently. |
| **Fee clock accruing during combat** | Made being attacked a discount. Replaced by a freeze (§5). |
| **Pausing the fee clock in spawn** | Let a parked mule reach 5% at no risk and bank other players' hauls. Replaced by a reset (§5). |
| **Reseal decay (any trigger)** | Combat-tag version was circular; proximity version killed legitimate tactical walls. §13 is the answer. |
| **Kill reward as a fee discount or clock reduction** | Value arriving pre-secured. Principle 3. |

---

## 19. Standing note

**Push back with reasoning rather than implementing something you think is wrong.**

**When proposing anything new**, check it against principle 3 (no *spendable* value arrives
pre-secured) and principle 5 (prefer a counter to a restriction).

### Build order

1. **§13's suppression consumable** — bunkering is uncountered by design until this ships.
2. **§3 containment and §4's clamp.** Ship together; a clamp with a missing hook is worse than no
   clamp, because it looks correct.
3. **§5's clock and §6's fee.** The economy does not exist until these do.
4. **§11's kill reward.** PvP has no payout against banked players until this lands.
5. **§14's tracker unification**, which also fixes the anti-farm cache defect.

### Do not "fix" these

- §5's clock **pauses** in combat but **resets** in spawn. A pinned player is still exposed; a player
  in spawn is not.
- §11's clock **does not** pause in combat, unlike §5's. One measures production, the other survival.
- §10's tag requires damage to actually land, so a player cannot freeze *someone else's* clock.
- Bank zones must stay **outside** spawn (§8). Moving one inside pins the fee at 25% with no error
  and no symptom.
