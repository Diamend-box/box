# Playtest 3 — what to do, and what to bring back

Written after your second live test, so this one is shaped around what you
actually found rather than around what I guessed you'd find.

**Build: Actions run #86 or newer.** Anything older is missing the loot
editor, the guard huts and the mob budget, and Pass 1 will look wrong.

**How to use it.** Work top to bottom. Pass 1 is a checklist of things you
already reported broken — it should be quick, and it is the most valuable
thing in the document, because a fix I can't confirm is a fix I don't have.
After that the order runs from "never been tested at all" to "nice to have".

**Timeboxing.** Passes 0–4 are the ones that matter. Pass 5 is the caves,
which have **never been played by anyone**, so it is the biggest unknown in
the project — but it is also the longest, which is why it isn't first.

---

## What changed since you last played

Everything below came out of your two reports. It's listed here so you know
what you're looking at, not as a to-do.

| You said | What I did |
| --- | --- |
| Tier 5 chests were empty | loot.yml had no tier 5 section at all. Added one. |
| Chests unreachable through a 1-block gap | My reachability test accepted a crawl gap. It now requires standing height, and two castle rooms that were sealed with **no way in at all** got doorways. |
| Mobs respawned infinitely | Strict per-island budget: 14 spawns, refilling 20 minutes after the first one. |
| The cultist landfall was gone | A full reset was deleting it. It's re-raised on reset now. |
| Husks everywhere | The MythicMobs pack was in the wrong folder — **my** fault, this document gave the wrong path. See Pass 0. |
| Mobs need nametags | Every spawned mob is named from its id. |
| Castles feel empty | Nine roofed guard huts along the walls. 13–16 chests per castle, each rolling slightly less. |
| Stacked relics all woke at once | Only the one in your hand wakes now. |
| GUIs feel janky | Shop boards rebuilt into four bands with filler panes; menu titles shortened so client buttons stop overlapping them. |
| Relics for permanent upgrades too common | Were up to 79% of a vault. Now ~5% of a chest, ~20% of a vault. |
| Vault levers are a pain to find | A sealed vault now tells you the direction and distance, and the lever stands on a redstone lamp that lights when thrown. |
| Boat speed was vanilla regardless of type | Diagnosed and fixed — **but see Pass 2, this is the one I can't confirm.** |
| Remove totems | Gone from every table. |
| A GUI to add my own items to loot | `/ds loot`. See Pass 4. |

---

## Pass 0 — Boot, and the folder that broke last time

1. Stop the server, drop in the jar from run **#86 or newer**.
2. **The MythicMobs pack goes in `plugins/MythicMobs/Mobs/`.** Not
   `plugins/MythicMobs/`. Copy the *contents* of `mythicmobs-pack/` in there.
   This document told you the wrong path last time and that is the entire
   reason every island spawned husks.
3. Restart.

```
/ds diag
```

There's a new line: **`mythicmobs`**, reporting how many of the ids in mobs.yml
Mythic can actually resolve. It exists specifically because the failure you hit
was silent — from the spawner's side, falling back to a vanilla husk looks like
success.

> **RECORD — do this before anything else.** A screenshot of `/ds diag`. If the
> mythicmobs line doesn't say all ids resolved, the pack is still in the wrong
> place, and fixing that before you continue will save the whole session.

**Do not run `/ds reset full confirm` unless you want a fresh sea.** Last time
this document told you to, and it deleted the landfall. That specific bug is
fixed, but a full reset still wipes vault states and island layouts, and you
have a world worth keeping now.

If you *do* want a fresh sea, run it — and note the wall-clock time again. You
measured 82 seconds last time; the guard huts add blocks per castle, so I'd
like to know whether that number moved.

---

## Pass 1 — The fix list

Short, mostly yes/no. This is the pass I most need back.

**Tier 5 loot.** Sail to a tier 5 island, open chests.

> **RECORD:** still empty? If they have items now, roughly what — I want to
> know whether tier 5 feels like the top of the ladder or just like tier 4.

**Chest reachability.** Open every chest you can find on two or three islands,
including a castle and the twin atoll.

> **RECORD:** any chest you can see but cannot open. This should now be zero — I
> swept every shape at every tier across 16 seeds offline and got 1712 reachable
> chests and 0 sealed. A single counter-example means my sweep is testing the
> wrong thing, which I'd want to know immediately.

**Mob nametags.** Kill things.

> **RECORD:** a screenshot with nametags visible. Are the names readable, or is
> "Drowned Reaver" floating over a vanilla drowned because the pack still isn't
> loading?

**Stacked relics.** Get two or more of the same relic, hold the stack, wake one.

> **RECORD:** does exactly one wake?

**Vault levers.** Find a vault chest and try to open it while sealed.

> **RECORD:** does the direction-and-distance hint point you the right way?
> Getting north and south backwards across a 70-block island is the obvious
> failure mode and I have only unit tests to go on. Then throw the lever and
> confirm the lamp under it lights.

**Relic rarity.** A feel question, not a count — you won't open enough chests
to measure 5%.

> **RECORD:** after a normal session, did you find relics at all? Zero in a long
> session means I over-corrected, and cutting from 79% to 5% is a big enough
> swing that over-correcting is entirely plausible.

---

## Pass 2 — Boat speed, which I could not verify

**This is the fix I'm least confident in**, and it needs its own pass.

Your screenshot showed the HUD reading "Maelstrom", which proved the multiplier
was being calculated correctly — so the bug was never the maths. A boat driven
by its rider is moved by the *client*, and the server sees positions rather than
velocity, so the speed guard was reading a velocity of roughly zero and never
applying anything. It now measures actual movement between ticks.

What I don't know is whether the server pushing a velocity back survives the
client's own prediction. Only a real server can answer that.

1. Sail a plain vanilla boat. Note how it feels.
2. Sail a level 4–5 Dark Sea Boat over the same stretch.

> **RECORD — the important one.** Is there now a *noticeable* difference? If you
> can, time yourself over a fixed distance with each; a number beats an
> impression here. If it's still identical, say so plainly — that means the
> velocity approach doesn't work and the delivery mechanism needs replacing,
> which is a different piece of work and I'd rather start it than keep polishing
> something that can't function.

> **RECORD:** also whether the boat feels jittery or fights you. If the server
> and client disagree about where the boat is, that's what it looks like, and it
> would mean the fix is half-working.

---

## Pass 3 — The mob budget, which is a judgement call

Each island now allows **14 spawns**, and the budget refills 20 minutes after
the first one is spent. So an island can be cleared, and clearing it means
something for a while.

These numbers are my guess at your words: *"a strict budget so it feels
reasonable to clear out an island before going to the next"*. I have no way to
know whether 14 is right.

1. Land on a tier 3–4 island and clear it properly.
2. Note whether it goes quiet, and whether that felt earned or abrupt.
3. Stay, or come back within 20 minutes, and see whether it stays quiet.

> **RECORD:** did clearing an island feel like an accomplishment, or like the
> spawner broke? Those look identical from the outside and only you can tell me
> which one it was.

> **RECORD:** roughly how long clearing an island took, and whether 14 was too
> few for a big castle. A castle is much larger than an atoll and they currently
> share a budget, which may well be wrong.

Both numbers live in config.yml under `mob-spawning` (`island-budget`,
`budget-refill-minutes`) and `/ds reload` picks them up. **Tune them yourself
rather than waiting on me** — you'll iterate in minutes where I'd take a day.

---

## Pass 4 — The castles, and the new loot editor

**The castles.** Find one.

> **RECORD:** does the courtyard still feel empty? The nine guard huts were
> meant to fill it with structure as much as with chests. A screenshot from the
> gatehouse looking in would tell me more than a sentence.

> **RECORD:** 13–16 chests per castle, each rolling two fewer items than a
> normal chest of that tier. Does that read as "more loot, worse chests" — what
> you asked for — or just as more clicking?

**The loot editor.** New: `/ds loot`.

1. Run it. You get a picker of every tier, base table on the left, that tier's
   vault table beside it.
2. Open a tier. Grey lines are loot.yml's and can't be clicked; they're there so
   you can see the scale your own weights sit against.
3. **Click any item in your own inventory** to add it to that pool at weight 5.
4. Left/right-click your line to change the weight; each line shows its
   resulting share of the table as a **percentage**, which is the number that
   actually matters. Middle-click cycles stack size. Q removes.
5. Add something obviously identifiable — a renamed, enchanted item — then find
   a chest of that tier and check it turns up.

> **RECORD:** did your custom item appear in a chest, with its name, lore and
> enchants intact? This is the round-trip that matters; a snapshot that loses
> item data is worthless.

> **RECORD:** confirm the editor did **not** consume or move the item you
> clicked. It copies. If it ever takes your item that's a serious bug and I want
> to know at once.

> **RECORD:** are the percentages actually useful for choosing a weight, or
> would you rather see something else on the tile?

Your additions go to `loot-custom.yml`; `loot.yml` is never written, so your
edits and the shipped tables can't damage each other.

---

## Pass 5 — The cultist caves, which nobody has ever played

**No part of this has run on a real server.** Two whole features — the geode
extraction channel and the first-touch regen clock — exist only as unit tests.
If you have time for one long pass, make it this one.

**Getting there.** The landfall is at **2600 / -1800**. Find the portal on it
and step through.

> **RECORD:** did the portal put you in the arrival chamber, on solid ground,
> the right way up? And does going back the other way return you to the landfall
> rather than to spawn?

**The geodes.** They're big now — 60 to 150 blocks — wrapped in a calcite shell
with amethyst buds, embedded in basalt. Three kinds: Emberglass (warm gold),
Voidbloom (pale violet), Godspore (sick green).

> **RECORD:** can you see one from across a cavern? They're meant to be
> landmarks. If you have to hunt for them, the whole loop is wrong.

**The extraction channel.** Mine a crystal core with a good pickaxe. Blocks do
not break instantly — the plugin holds the block and advances the vanilla crack
overlay.

> **RECORD — the one I'd most like video of.** Does the crack overlay advance
> *smoothly*, or does it flicker, jump backwards, or reset? This is the single
> most likely thing to be visibly broken, because it fights the client's own
> prediction of block breaking.

> **RECORD:** release the button part-way. Does the crack cleanly reset? Then
> switch to a different block mid-channel, and swap your held item mid-channel.
> Neither should leave a stuck overlay.

> **RECORD:** hit a geode with something that isn't a pickaxe. You should get a
> refusal message, not a multi-minute channel.

> **RECORD:** is the calcite shell genuinely unbreakable? Try from below, and
> from inside if you can get there.

---

## Pass 6 — The two cave numbers I most want

**Extraction speed.** Mine several blocks of each crystal with your best pick,
timing them.

> **RECORD:** seconds per block for each of the three, and exactly what the
> pickaxe was — tier, Efficiency level, and any Haste. The shipped targets are
> 1.5 / 2.5 / 4.0 seconds at a Netherite pick with Efficiency 15. This is the
> number most likely to need retuning once it's in your hands, and it's a config
> value in `ores.yml` precisely so you can move it yourself.

> **RECORD:** try a deliberately worse pick. It should be *obviously* slower — a
> mid-game pick is about 6.7× slower by design, because the caves are endgame
> content. Confirm that gate feels like a gate and not like a bug.

**The regen clock.** The mechanic I'm least sure reads correctly.

1. Mine **a few blocks** of one vein — not all of it. **Write down the time.**
2. Leave. Go do Pass 7.
3. Come back after the cooldown (18 / 22 / 25 minutes by type).

> **RECORD:** the wall-clock time of your first block, and the wall-clock time
> the vein came back. It should return **whole**, not just the blocks you took,
> and the clock should run from your *first* block rather than your last —
> that's what stops the veins all coming back at once.

---

## Pass 7 — Owed from last time: do the NPCs survive?

Never confirmed, and it's the failure that would quietly ruin the outpost.

1. `/ds npc list` — all five present?
2. Restart the server. Check again.
3. Run a soft reset. Check again.

> **RECORD:** are all five still standing after each? And critically — is there
> ever a **duplicate**? Duplication and disappearance have opposite causes and I
> need to know which one I'm chasing.

While you're there, the shop boards were rebuilt after your screenshots.

> **RECORD:** one screenshot per board. Do the empty rows read as deliberate
> now, or still as gaps?

---

## Pass 8 — Performance, with the sea populated

> **RECORD:** TPS while sailing through a populated area, and TPS in the caves.
> The mob budget should have *helped* here — fewer mobs alive at once — so if
> performance got worse rather than better, that's worth knowing.

---

## Decisions I need from you

Short answers are fine; a phone message is fine.

1. **The name.** You raised changing Dark Sea → Vironic Sea. I'd suggest "The
   Mariphage" or "The Naxian Sea" — both come from the fiction already in the
   plugin rather than from Arcane Odyssey. Renaming only the player-facing
   strings is cheap. Entirely your call and there's no rush.
2. **Crystals still have no sink.** The three cave crystals can't be bought,
   sold or spent on anything — a test actively enforces that. They're meant to
   be upgrade materials for work that doesn't exist yet. Tell me what you want
   them to buy and I'll build it.
3. **`/ds tp`** — should it land you on the highest block instead of the
   configured spawn spot? Still unanswered from last time.

---

## What to send me

In rough order of how much it helps:

1. The `/ds diag` screenshot from Pass 0 — especially the mythicmobs line.
2. **The boat speed answer from Pass 2.** Yes or no is enough. It decides
   whether a whole piece of work needs redoing.
3. Video or a clear description of the extraction channel (Pass 5).
4. The two numbers from Pass 6 — seconds per block, and the first-touch clock.
5. Your verdict on the mob budget (Pass 3), with whatever numbers you settled on.
6. Anything from the Pass 1 checklist that's still broken.
7. **The server log**, `logs/latest.log`, whether or not anything went wrong.
   Warnings without a visible symptom are exactly what I can't guess at.

Impressions are welcome, but keep them separate from the numbers. "The caves
felt slow" and "a block took 6.2 seconds" lead to different fixes, and only one
of them is something I can act on without asking you again.
