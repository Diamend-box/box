# RoboBear — playtest script

**For:** the first real run of RoboBear on boxpvp (Paper 1.21.4), version 1.0.4.
**Time:** ~15 minutes for the smoke pass, ~75 for the whole thing.
**Point of it:** RoboBear has never been played. Everything below CI can prove —
it compiles, it enables, the logic is right — is proved. What is not proved is
whether the thing is any good, whether the numbers land, and whether the
MineResetLite reader works against *your* build.

Work top to bottom. Anything that behaves oddly, note it and **keep going** —
one broken thing shouldn't cost you the rest of the pass.

---

## 0. Before you start

1. Grab **`RoboBear-1.0.4.jar`** from the
   [releases page](https://github.com/Diamend-box/box/releases/tag/robobear-v1.0.4).
   (Don't build it locally; the build lives in CI.)
2. Drop it in `plugins/`, start the server, **op yourself**.
3. Note your **MineResetLite version** and what `/plugins` shows its name as.

**Capture the boot before you touch anything:**

- [ ] Save `logs/latest.log` from the first boot as `boot.log`.
- [ ] `ls plugins/RoboBear/` — note which files exist (`config.yml`,
      `milestones.yml`, `mines.yml`, `mine-toggles.yml`, `upgrade-toggles.yml`,
      `data/`). Some only appear once you touch the thing that writes them.
- [ ] Note whether PlaceholderAPI is installed — §11 needs it and is skippable.

> **Do §1 before anything else.** If mine detection is broken, most of this
> script is untestable and I need to know that first, not on page four.

---

## 1. Does it see your mines? — *the one that gates everything*

- [ ] `/rb mines` — do your MineResetLite mines appear, with sensible bounds?
- [ ] `/rb mines debug` — **copy the whole output either way.**

The debug report tells us which of four worlds we're in:

| It says | Meaning |
|---|---|
| `Bounds read via: the serialised map (minX/…)` | Working. This is the expected answer for MineResetLite 4.21.2. |
| `Bounds read via: coordinate getters` | Also working, via an unobfuscated build. |
| `Mine collection found: 0 entries` | MRL has no mines loaded — check `/mrl list`, then `/rb reload`. |
| `Could not work out how to read its bounds` | **Stop and send me that output.** It ends with the `serialize()` keys, which is exactly what I need. |

- [ ] Spot-check two or three mines: do the bounds in `/rb mines` actually match
      where the mine is in the world? Stand in a corner and compare coordinates.
      A reader can produce plausible-looking numbers that are wrong.

**Data I want:** the full `/rb mines debug` output, pasted, whatever it says.
This is the single most valuable thing in this document.

---

## 2. Smoke pass (do this even if you have no time for the rest)

- [ ] `/rb` — the menu opens.
- [ ] `/rb pass give` — you get a **Robo Pass** name tag.
- [ ] `/rb start` — a run begins and the choice screen appears with two offers.
- [ ] Pick one. The screen closes and a clock appears on the action bar.
- [ ] Break blocks in the named mine — the counter moves.
- [ ] `/rb cancel` — the run ends cleanly.
- [ ] `/rb reload` — says it reloaded, no stack trace in console.

**Data:** if a menu is empty, misaligned, or an icon is missing, screenshot it
and say which slot (count from 0, top-left, left to right).

---

## 3. The mine picker

You have ~70 mines and all of them are in the objective pool by default. This is
the screen that fixes that.

- [ ] `/rb mines edit` — the picker opens, paged, green = in the pool.
- [ ] Page forward and back. Does the page number in the compass read right?
- [ ] Click a mine off, click it on again. The icon and lore flip both ways.
- [ ] **Shift-click "Disable every mine"**, then switch on only the mines a
      brand-new player can actually reach. This is the configuration you should
      actually run with.
- [ ] Plain-click "Disable every mine" first — it should refuse and tell you to
      shift-click. Confirm a plain click never wipes the pool.
- [ ] `/rb mines` — the excluded ones show `[off]` and the count at the bottom
      matches what you set.
- [ ] Start a run and take a few objectives. **Do they only ever point at mines
      you left switched on?** Reroll a few times to sample more.
- [ ] Check `plugins/RoboBear/mine-toggles.yml` looks sane.

**Data I want:**
- How many mines you ended up with in the pool, and roughly why those.
- Whether the picker is usable at 70 mines or whether it needs search/sorting.

> **A question I can't answer from here:** the objective says *"mine 240 blocks
> in `deepslate_b`"* but RoboBear doesn't tell the player **where that is** or
> teleport them. On a prison server with warps that's probably fine. Is it? If
> not, the fix is either a warp hook or naming the mine's coordinates in the
> objective text — tell me which you'd want.

---

## 4. Passes — *including the exploit check*

- [ ] `/rb pass give` → one pass. `/rb pass give <alt> 5` → five, to them.
- [ ] Fill your inventory, then `/rb pass give` — the extras drop at your feet
      and the message says how many.
- [ ] `/rb start` with a pass → it's consumed, exactly one.
- [ ] `/rb start` with no pass → refused, and the message names what you need.

**The exploit test — this is the point of 1.0.3:**

- [ ] Take a plain name tag, rename it in an **anvil** to `Robo Pass`.
- [ ] `/rb start` holding it → **must be refused**, and you should get the extra
      line *"That one wasn't issued by RoboBear"*.
- [ ] Confirm the anvil copy is **still in your inventory** afterwards — a
      refused start must never eat anything.
- [ ] Rename a **real** issued pass in an anvil to something silly. It must
      **still work** — the tag survives, the name is cosmetic.

- [ ] Put a pass in a shulker box, take it out, use it. Still valid.

**Data I want:** any way you can find to get a working pass without
`/rb pass give`. That's the thing worth breaking.

---

## 5. A full run, properly

Set `run.entry-item.item: ""` temporarily if constantly minting passes is
annoying, and put it back afterwards.

- [ ] The choice screen: two offers, the second harder and paying more Cogs.
      Does the difference read clearly enough to be a *decision*?
- [ ] Reroll. You get one free reroll by default — the second attempt is refused
      with a sound.
- [ ] Take an objective. The clock starts **only then**, not at `/rb start`.
- [ ] Mine in the **wrong** mine → does not count. Mine in the right one → does.
- [ ] **Place blocks yourself and break them** → must not count.
- [ ] Have someone else break blocks in your mine → must not count for you.
- [ ] Let the clock hit the last 10 seconds — it turns red and pings.
- [ ] Let one round **time out** → the run ends and says why.
- [ ] Clear a round → Cogs paid, workshop opens.

**The MineResetLite interaction I actually want checked:**

- [ ] Be mid-objective in a mine when **MRL resets it**. Force it with
      `/mrl reset <mine>` if you can. Does your progress survive? Does anything
      throw? (It should be fine — progress counts blocks you broke, not blocks
      remaining — but nobody has ever run this.)
- [ ] Have an objective pointing at a mine, then **delete that mine in MRL** and
      `/rb reload`. You should get told the job points at a mine that's gone,
      not a stack trace.

**Data I want:** how many rounds you got through, and how it felt — see §9.

---

## 6. The workshop

- [ ] Clear a round, then in the workshop buy each of the six upgrades at least
      once. Does each effect actually happen? Haste and Speed you should feel;
      Plating and Impact Driver need a mob or an alt to notice.
- [ ] **Spare Battery** — does the next round genuinely have more time on it?
- [ ] **Scrap Magnet** — does the next round pay more Cogs?
- [ ] Buy until you can't afford the next level. The refusal names the cost.
- [ ] Max one upgrade out → it says fully upgraded and stops selling.
- [ ] Retire from the workshop screen → run ends, payouts kept.

Then the admin side:

- [ ] `/rb upgrades` — take **Impact Driver** off sale.
- [ ] Start a run, clear a round → it's gone from the workshop.
- [ ] With a workshop screen **already open**, have someone take an upgrade off
      sale, then click it. It must refuse and cost nothing.
- [ ] Buy an upgrade, *then* take it off sale mid-run. Your bought level must
      **keep working** for the rest of that run.
- [ ] Shift-click "Close the workshop" → the shop screen says nothing's on sale
      and doesn't look broken.

**Data I want:** which upgrades you'd actually want on a PvP server. My guess is
Impact Driver is the questionable one; tell me if that's wrong.

---

## 7. Payouts — *the part that decides whether this is worth playing*

Milestone tiers ship **deliberately empty**, so a fresh install pays nothing.

- [ ] `/rb milestones` — the starter tiers are there, all marked as paying
      nothing.
- [ ] Open one, **drop real items into the chest**, close it. Reopen — they're
      still there. Check `milestones.yml`.
- [ ] Add a tier at a round that already has one → refused.
- [ ] Rename a tier (right-click), delete one (shift-right-click). Confirm a
      plain click never deletes.
- [ ] Clear the round a tier sits on → **you get paid**, with a broadcast.
- [ ] Fail the run *after* that → you keep the payout. This is the whole
      premise; confirm it holds.
- [ ] Set `rewards.delivery: ground`, earn a payout in the open with an alt
      nearby → **the alt can take it.** Confirm that, then decide whether you
      want it. `inventory` is the softer setting.
- [ ] Earn a payout with a **full inventory** on `inventory` delivery — where
      does it go? It must not vanish.
- [ ] Add a reward **command** to a tier and confirm it runs.

**Data I want:** what you actually put in the tiers, and at which rounds. That's
the number I most want to see, because it's the one I can't guess.

---

## 8. Ending a run, the awkward ways

- [ ] **Die** mid-run (`run.fail-on-death: true` by default) → run ends, and
      check what happened to your inventory under your normal death rules.
- [ ] **Log out** mid-run, log back in → the run is gone, not resumed
      half-broken, and no buff is stuck on you.
- [ ] `/rb reload` **while a run is live** → the run ends. Confirm the player is
      told, rather than just finding the clock gone.
- [ ] Close the choice screen with escape, then `/rb start` again → it should put
      you back on the same screen, not start a second run.
- [ ] Close the workshop with escape → same, you can get back.
- [ ] End a run and check **potion effects are removed**, and Cogs are gone.
- [ ] `/rb stats` — runs, deepest round, payouts all move sensibly.
- [ ] `/rb reset <alt>` wipes that player only.

---

## 9. Balance — the numbers I most want back

This is the part I flagged in the guide and it's still unplaytested.

Default `objectives.mine-blocks.growth: 1.28` produces roughly:

| Round | Gentle offer | Greedy offer |
|---|---|---|
| 1 | 90 | 140 |
| 5 | 240 | 370 |
| 10 | 850 | 1,250 |
| 15 | 2,850 | 4,350 |

My read is that runs die around **round 11–14**, which makes any tier at round 15
near-decorative. That's a prediction, not a measurement.

- [ ] Do three or four full runs, playing properly, and write down **which round
      each one ended on and why** (timed out / died / retired).
- [ ] Note roughly how long one round takes when you're trying.

**Data I want:** just the list of end rounds. Four numbers is enough to tell me
whether `growth` should go to `1.20` (longer runs) or `1.35` (shorter), and where
the top milestone tier belongs.

Also worth a note: does 300s per round feel long, short, or right for your mines?

---

## 10. The action bar fight — *specific to your server*

RoboBear rewrites the clock five times a second, at the end of the tick, to stay
on top of other plugins. This is exactly the kind of thing that only shows up on
a real server with real plugins.

- [ ] Start a run, then trigger whatever else writes your action bar — combat
      tag, autosell total, boost countdown.
- [ ] Does the run clock come back **immediately**, or does it flicker, or does
      it disappear entirely?
- [ ] If it flickers: set `display.actionbar-refresh-ticks: 1` and try again.
- [ ] Try `display.mode: bossbar` once, if nothing else owns the bossbar. Is it
      better enough to be worth the slot?

**Data I want:** which plugin wins if RoboBear ever loses, and whether `1` fixes
it. If something still beats it, I need to know what that plugin is.

---

## 11. Placeholders (skip if no PlaceholderAPI)

```
%robobear_running%    %robobear_round%      %robobear_progress%
%robobear_target%     %robobear_time%       %robobear_cogs%
%robobear_objective%  %robobear_payouts%    %robobear_best_round%
%robobear_mines%      %robobear_mines_enabled%
```

- [ ] None render blank or as raw `%…%` text.
- [ ] `mines_enabled` matches what the picker says.
- [ ] `running` behaves as a condition when the player isn't in a run.

**Data:** paste the rendered line. A blank means I named something wrong.

---

## 12. Two players and persistence

- [ ] Two players in runs at once, objectives in the **same** mine. Each counts
      only their own blocks.
- [ ] Restart the server mid-session. Stats, payouts, mine toggles and upgrade
      toggles all survive.
- [ ] Check the console for **any** stack trace across the whole session.

---

## What to send me

Zip or paste, in rough order of usefulness:

1. **The `/rb mines debug` output** (§1). Nothing else matters as much.
2. **`logs/latest.log`** for the session, plus every stack trace in full.
3. **Your four end-round numbers** from §9.
4. **`plugins/RoboBear/`** — the configs, `milestones.yml`, `mine-toggles.yml`,
   `upgrade-toggles.yml`. Skip `data/` unless something looks wrong.
5. **Screenshots** of anything that looked wrong, with the slot number if it's a
   layout thing.
6. **Your notes**, one line each, in this shape:

```
[where]   workshop, after clearing round 3
[did]     bought Spare Battery with 9 cogs
[wanted]  next round to have 360s on the clock
[got]     still 300s
[log]     no error in console
```

Rough is fine. `[wanted]` and `[got]` are the two that matter — I can usually
find the rest from those.

---

## Known-unfinished — don't spend time reporting these

Already on the list, so skip unless they're worse than described:

- **The MineResetLite reader has never met your build.** §1 is that test.
- The objective names a mine but doesn't say where it is or take you there.
- The mine picker has no search or sorting — at 70 mines it's two pages of
  clicking.
- Upgrade **prices** aren't editable in game, only on/off (`config.yml`, then
  `/rb reload`).
- Balance is unplaytested end to end — that's §9, not a bug.
- Milestone tiers ship empty on purpose; a fresh install paying nothing is
  correct behaviour, not a fault.
- No cross-server or database storage; one file per player.

---

## If something is badly broken

Stop, grab `latest.log`, and note what the last thing you did was. A single
stack trace with the action that caused it is worth more than an hour of
careful checklist work.
