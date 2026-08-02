# Playtest 2 — what to do, and what to bring back

For the first live test since the outpost, the caves, and the guarded-startup
work. Nothing in this document has ever run on a real server.

**How to use it.** Work top to bottom. The order is not arbitrary — each pass
depends on the one before it having worked, and the earliest passes are the
ones most likely to end the session. Every box marked **RECORD** is a thing I
cannot derive from anything else; a number or a screenshot is worth more than
a sentence. If something fails, note *what you saw* rather than what you think
caused it, and move to the next pass — most of these are independent.

**Timeboxing.** Passes 0–4 are the ones that matter. If you get through those
and nothing else, the test succeeded. 5–9 are nice to have. 10 is only if
you're enjoying yourself.

---

## Pass 0 — Boot

1. Upgrade the Minehut plan (6 GB) and **update FAWE** — the old one throws
   `Unsupported class file major version 69` and takes the world generator
   with it.
2. Drop in the DarkSea jar from **Actions run #77 or newer**. Older green
   builds are missing the NPC respawn fix and will look broken in Pass 2.
3. Apply the 6 GB profile from the README (config values + `view-distance`),
   restart.
4. Optional but strongly recommended: copy the **contents** of
   `mythicmobs-pack/` into `plugins/MythicMobs/Mobs/`, restart. The `Mobs/`
   subfolder is the part that matters — MythicMobs only reads mob definitions
   from there, and this document said `plugins/MythicMobs/` for the whole
   first playtest, which is why every island spawned husks. Check it landed
   with `/ds diag`, which now reports how many of the ids in mobs.yml Mythic
   can actually resolve. Anything less than all of them means the pack is in
   the wrong place; the spawner falls back to vanilla mobs silently.

Then, **before anything else**:

```
/ds diag
```

The plugin now stays enabled through a failed startup step, so "it loaded" is
no longer proof that it all loaded. `/ds diag` is the console log for someone
who doesn't have the console.

> **RECORD — the single most valuable thing in this document.**
> A screenshot of the full `/ds diag` output, and of `/ds diag warnings` if
> the warning count is anything but 0.

If diag reports a failed step, send it and stop reading — everything below
assumes the plugin came up whole, and I'd rather fix the step than have you
test around it.

Then:

```
/ds reset full confirm
```

A full reset now re-raises the cultist landfall along with everything else. It
did not before, which is why the landfall was missing after the first
playtest's setup — this document told you to run a full reset, and the reset
deleted it.

> **RECORD:** how long the full reset takes, wall-clock, and whether the
> server visibly hangs during it. 34 islands get pasted; if that's a 30-second
> freeze it's a config knob, but I need to know it happens.

**Heads up:** the timed sea reset ships **enabled** (6-hour soft cycle,
`reset.auto`). Turn it off while you're setting up or it will re-paste islands
underneath you mid-test.

---

## Pass 1 — Place the outpost

The NPCs don't exist until you place them. Stand where each one should be:

```
/ds npc create refugee_trader
/ds npc create artificer
/ds npc create black_market
/ds npc create boat_expert
/ds npc create apothecary
/ds npc list
```

Right-click each one. The board should open; vanilla villager trading should
never appear.

> **RECORD:** one screenshot per board. I've only ever seen these rendered by
> a unit test — I want to know if the layout is actually readable, whether
> prices sit where you'd look for them, and whether anything is cut off.

> **RECORD:** does right-clicking ever open the *vanilla* trade screen, even
> once, even briefly? That's an event-priority problem and I need to know.

---

## Pass 2 — The one I'm least sure of: do the NPCs survive?

This is a fix I wrote blind. The NPCs are non-persistent entities, which means
the server does not save them when their chunk unloads — they're simply gone.
There was no chunk-load handler at all until last week. If the fix is wrong,
your shopkeepers vanish the first time nobody stands near the outpost, and the
symptom looks like "the plugin broke".

1. Note the time. Sail well away from the outpost — far enough that its chunks
   unload (a few hundred blocks, or just go do Pass 5 and come back).
2. Come back.

> **RECORD:** are all five still standing? If any are missing, `/ds npc list`
> and say which ids are listed but absent.

3. Then the harder version: **restart the server** and check again. Then
   `/ds npc respawn` and confirm you don't end up with two of anyone standing
   in the same doorway.

> **RECORD:** any duplicated NPC, at any point. Duplication and disappearance
> are opposite failures of the same code and I need to know which one I've got.

---

## Pass 3 — The extraction channel

The known-hard piece, and the one with no CI coverage at all.

Get to the cultist landfall (~3,100 blocks out — it's a black ziggurat, and
it's the only one). Stand on the pad at the top of it.

> **RECORD:** did the portal put you in the arrival chamber? Standing on the
> matching pad down there — does it bring you back to the ziggurat?

Find a geode and hold left-click on the core with **your real endgame
pickaxe**.

The problem: vanilla runs its own dig timer underneath mine and completes
almost instantly against good gear. I cancel the break and resend the block.
In theory you see a smooth crack overlay. In practice you might see the block
strobing.

> **RECORD — the important one.** Does the crack overlay advance smoothly, or
> does the block flicker/flash/reappear while you hold? A few seconds of video
> is worth more than any description here. If it flickers, say roughly how
> badly — barely noticeable, or unusable?
>
> If it's bad, the fallback is holding Mining Fatigue on you while channelling
> so vanilla's timer never completes. That's a real change and I'd rather make
> it knowing than guessing.

> **RECORD:** does releasing the button cleanly reset the crack, or does a
> half-mined block stay looking damaged?

> **RECORD:** hit a geode with something that isn't a pickaxe. You should get
> a message, not silence.

---

## Pass 4 — The two numbers I most want

**4a. The tool curve.** Time one emberglass block with your best pickaxe.
Configured target is **1.5 seconds** at reference gear (netherite, Efficiency
15). Then time one with a deliberately worse pick.

> **RECORD:** seconds per block for both, and what each pick actually was
> (tier + Efficiency level + any Haste). This is the number most likely to
> need retuning, and it's a config value precisely so we can retune it without
> a build.

Voidbloom should be ~2.5s and godspore ~4.0s at reference; spot-check one of
them if you have the patience.

**4b. Regrow, and the thing that makes it fair.** Mine **three or four
blocks** out of one geode — not the whole thing — and **write down the clock
time of the first block**. Leave it alone. Emberglass is on an 18-minute
timer from **first touch**, not last.

> **RECORD:** the wall-clock time you took the first block, and the wall-clock
> time the geode came back. It must come back **whole** — every block at once,
> at first-touch + 18 minutes. If it comes back block by block, or the timer
> restarted when you took the second block, that's a real bug and this is the
> only way to catch it.

> **RECORD:** is the calcite rind genuinely unbreakable? Try. Try from below,
> too — the whole "you have to stand in the open" design falls apart if you
> can tunnel up into a core.

---

## Pass 5 — The sea, since it's been two weeks

You've tested most of this before; what's changed is the ring layout and the
new island shapes.

> **RECORD:** sail out and note where each zone message fires against your
> distance. Zones 1–4 are the run; the Sunless Trench starts ~14,500 out.
> Rough numbers are fine — I want to know the ramp feels like a ramp.

> **RECORD:** a screenshot of the naval HUD in actual use. Same question as
> the shop boards — I've never seen it on a real screen.

> **RECORD:** find a ruined castle and a Core nest if the layout gives you
> one. Screenshots. Is the castle walkable, or does it have places you
> obviously can't get to?

**Chest refill cooldown** (owed from last time): open the same chest twice in
quick succession.

> **RECORD:** did the second open restock it? It should NOT until the tier's
> cooldown has passed.

**Vault lever:** find an island with sealed vaults, throw the lever, confirm
the vaults open. Then trigger a soft reset (`/ds reset soft`) and go back.

> **RECORD:** does the lever come back already thrown, and do the vaults stay
> open? Cracked is supposed to stay cracked through a soft reset.

---

## Pass 6 — Boats, end to end (also owed from last time)

Find an upgrade token in loot → `/ds boat upgrade` → check the boat menu.

> **RECORD:** is the speed increase actually noticeable, and does the shield
> ring show? Does the level survive a relog *and* a full server restart?

> **RECORD:** ram something. Does hull damage read sensibly on the HUD, and
> does a sunk boat leave you a wrecked-boat item rather than nothing?

---

## Pass 7 — The shops, in anger

Buy something. Sell something. Wake a relic at the artificer.

> **RECORD:** does the black market's GUI timer count down correctly, and does
> the stock actually rotate when it hits zero? It's on a 2-hour wall clock, so
> you may need to come back — the timer displaying wrong is the more likely
> failure and you can see that immediately.

Open `/ds shop` (admin editor). Click an item in your own inventory to put it
on a shelf.

> **RECORD:** confirm the editor did NOT move or consume the item you clicked.
> It's supposed to copy. If it takes the stack, tell me immediately and stop
> using it.

Try adding one of **your custom boxpvp items** to a board and buying it back.

> **RECORD:** did the name, lore, and enchants survive the round trip? This is
> the `custom:<base64>` path and it's the one most likely to lose NBT.

---

## Pass 8 — Performance

Once things are running, with you moving around the sea:

```
/tps
/ds status
```

> **RECORD:** TPS while sailing through a populated area, and TPS during a
> soft reset. Also whether anything stutters when you cross into a new island's
> spawn radius.

---

## Pass 9 — A decision I need from you

Should `/ds tp` land you on the highest block instead of the configured spawn
spot? You'll find out in about thirty seconds of using it. A one-line phone
message is enough.

---

## Pass 10 — Only if you're enjoying yourself

Bring someone else on. PvP in the sea, PvP over a geode. The caves have PvP on
but **no item loss** — worth confirming that's actually how it behaves, since
it's the one rule that differs between the two worlds and getting it backwards
would be memorable for the wrong reason.

> **RECORD:** dying in the caves — do you keep your inventory? Dying in the
> sea — do you lose only run-scoped items?

---

## What to send me

In rough order of how much it helps:

1. The `/ds diag` screenshot from Pass 0.
2. Video or a clear description of the extraction channel flicker (Pass 3).
3. The two numbers from Pass 4 — seconds per block, and the first-touch /
   regrow clock times.
4. Whether the NPCs survived Pass 2.
5. Screenshots of anything with a GUI — shop boards, naval HUD, boat menu.
   I have never seen any of these rendered.
6. **The server log**, if anything at all went wrong. `logs/latest.log`. Even
   if nothing went wrong it's worth grabbing — warnings that don't produce a
   visible symptom are exactly what I can't guess at.

Impressions are welcome too, but keep them separate from the numbers. "The
caves felt slow" and "a block took 6.2 seconds" lead to completely different
fixes, and only one of them is something I can act on without asking you again.
