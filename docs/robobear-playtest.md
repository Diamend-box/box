# RoboBear — playtest script

**For:** RoboBear on boxpvp (Paper 1.21.4), version **1.1.0**.
**Time:** ~15 minutes for the smoke pass, ~90 for the whole thing.
**Point of it:** RoboBear has never been played. Everything below CI can prove —
it compiles, it enables, the logic is right — is proved. What is not proved is
whether the thing is any good, whether the numbers land, and whether the
MineResetLite reader works against *your* build.

**New since you last looked:** the three round-quality fixes in §4, and the
challenge mobs in §5. If you're short on time, do §1, §4 and §5 — that's the
new work.

**You'll want a second account** for §5. Nothing else needs one until §14.

Work top to bottom. Anything that behaves oddly, note it and **keep going** —
one broken thing shouldn't cost you the rest of the pass.

---

## 0. Before you start

1. Grab **`RoboBear-1.1.0.jar`** from the
   [releases page](https://github.com/Diamend-box/box/releases/tag/robobear-v1.1.0).
   (Don't build it locally; the build lives in CI.)
2. Drop it in `plugins/`, start the server, **op yourself**.
3. Note your **MineResetLite version** and what `/plugins` shows its name as.

> **Upgrading from 1.0.x?** `config.yml` is never rewritten once it exists, so
> you won't have the new `mobs:` or `objectives.limits:` blocks. Sensible
> defaults are built in and everything works without them — but if you want to
> tune the mobs or the size clamp, rename `config.yml`, restart to get a fresh
> one, and copy your settings across.

**Capture the boot before you touch anything:**

- [ ] Save `logs/latest.log` from the first boot as `boot.log`.
- [ ] `ls plugins/RoboBear/` — note which files exist (`config.yml`,
      `milestones.yml`, `mines.yml`, `mine-toggles.yml`, `upgrade-toggles.yml`,
      `objective-toggles.yml`, `mine-materials.yml`, `data/`). Some only appear
      once you touch the thing that writes them.
- [ ] Note whether PlaceholderAPI is installed — §13 needs it and is skippable.

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

## 4. The quest editor — *the three round-quality fixes*

Three things you reported, all of them ways a round arrives already lost.

**"Break 55 × Deepslate Iron Ore in quartz."** `1.0.5` picked the mine first and
the material from that mine's composition — but only when something could *tell*
it the composition, and on your server nothing could, so it fell back to the
whole config list for every mine. `1.1.0` reads the blocks directly instead: a
stride of reads across each mine, counted. It no longer depends on MineResetLite
exposing anything.

**The safe and the greedy offer being the same job.** Each was rolled without
looking at the other. They're now always different in *what* they ask for.

**"Break 250 blocks" in a mine holding two stacks.** The curve knew the round
number and nothing else. The same survey now sizes each mine's stock and trims
what gets asked for to fit.

**The checks that matter:**

- [ ] `/rb quests` — the screen opens: three job types on top, your pooled mines
      below.
- [ ] Look at the compass at slot 16. Does it say your mines **were** read? If it
      says nothing could be read about any mine, tell me — and check that
      `mines.sample-blocks` in `config.yml` isn't `0`.
- [ ] Look at the quartz mine's icon. Its lore lists what it can be asked for.
      **Is deepslate iron ore in that list?** It must not be.
- [ ] The same icon says *"Holds roughly N blocks"*. **Is N about right** for
      that mine when full? It's an estimate from a sample, so within a factor of
      two is fine — a wildly wrong number is worth reporting.
- [ ] Start a run and reroll ten or fifteen times, noting every offer.
      - **Does every material offer name a block that mine actually contains?**
      - **Are the two offers ever the same job?** They shouldn't be. (If your
        server can only build one kind of job you'll correctly get one offer.)
      - **Is any amount larger than the mine could give you in one round?**
- [ ] Set `objectives.limits.mine-resets-per-round` to `run.round-seconds`
      ÷ your mine's reset interval. At a 5-minute round and a 5-minute reset
      that's `1`, which is the default. If your mines reset faster, raise it or
      jobs will come out smaller than they should.

**The editor:**

- [ ] Click a job type off, then start a run — it should never be offered. Click
      it back on.
- [ ] Set `objectives.kill-mobs.enabled: false` in `config.yml`, `/rb reload`,
      then try to click **Kill mobs** on. It should refuse and tell you why,
      rather than silently doing nothing.
- [ ] Click a mine. A drop-in box opens, **prefilled** with what it can currently
      be asked for. Close it without touching anything — the mine should still
      say *"Read from the mine"*, **not** *"Set by hand"*. (Looking must not pin
      it.)
- [ ] Now put two or three blocks in, close. The mine says *"Set by hand"*, and
      objectives there only ever name those blocks.
- [ ] Drop a **sword** in the box. It should be ignored with a message, not
      stored as something to mine.
- [ ] Shift-click that mine on the quest screen — it goes back to automatic.
- [ ] Check `plugins/RoboBear/mine-materials.yml`: only the mines you corrected
      should be in it.

**Data I want:**
- Whether the automatic lists are *right* for your mines, mine by mine — this is
  the thing I have no way to check from here.
- Any mine where the automatic list contains filler players shouldn't be sent
  after (stone, cobble, deepslate). If there are many, the config's material
  list is probably the better lever and I'd rather fix that than have you click
  through seventy mines.

---

## 5. The mobs that come after you — *the big new one*

New in `1.1.0`. While a round's clock is running, the challenge sends mobs at
you. **Only you can see them and only you can be hurt by them.** Everyone else
is never sent the entity at all.

They're real mobs — a packet-only mob can't attack anything, because targeting
and damage live on the server — so the private part is done with per-player
visibility. **Dying to one ends the run**, which is the largest single change
this makes to how hard the ladder is.

**You need a second player for the important half of this.** Have an alt stand
in the same mine, not in a run.

**The core check — what the bystander sees:**

- [ ] Start a run, take any job. About three seconds in you should get
      *"Something's coming"* and a sound, then mobs arrive from a little way off
      rather than on top of you.
- [ ] **The alt should see nothing.** No mobs, no nameplates, nothing to hit.
- [ ] The alt **will** hear the fight and see you taking damage from nothing.
      That's expected and can't be fixed — sound isn't tied to the entity.
- [ ] Have the alt swing where a mob is. They should hit air, and the mob should
      not react.
- [ ] Have the alt shoot an arrow through the fight. It must not hurt the mob.
- [ ] The mobs must never turn on the alt, even when they're closer than you.

**The lifecycle:**

- [ ] Finish a round. **Every mob should vanish** the moment the shop opens.
- [ ] Take the next round — a fresh wave arrives.
- [ ] Run 50+ blocks away mid-round. They should follow, teleporting to you
      rather than getting stuck.
- [ ] `/rb cancel` mid-round — they all disappear.
- [ ] Log out mid-round, log back in — nothing left standing in the mine.
- [ ] `/rb mobs` — shows the roster and how many are alive. Should read `0` when
      nobody is mid-round.

**The rails:**

- [ ] Stand in a mine and wait for it to reset **with mobs in it**. They must not
      suffocate — an entombed mob that dies on its own is a hazard you beat by
      waiting.
- [ ] If your mines are open to the sky, wait for daytime. **They must not burn.**
- [ ] Kill one. **No drops, no XP**, and the mine is undamaged.
- [ ] Check the mine has no new holes after a few rounds.

**The feel — what I actually want to know:**

- [ ] Take a **kill round**. The mobs should **glow**. On a mining round they
      should **not** — that's the tell for "you need to kill these".
- [ ] Does the kill objective's number feel achievable? It's sized to what the
      challenge can send, not to the old curve, so it should be lower than you'd
      expect and still take the whole clock.
- [ ] Get to **round 6+** and confirm you start seeing the vex (flies through
      rock) and the breeze (knocks you around). Round 3 should add a skeleton,
      round 4 a cave spider.
- [ ] Clear a **milestone round** and confirm **The Foreman** shows up — one
      named piglin brute, tougher than the rest.

**Data I want:**
- **Is it too hard?** Two mobs at round one, up to eight later. If a competent
  player can't mine at all while defending, `mobs.population.base` comes down.
- **Does dying to one feel fair, or cheap?** This is the question. If it reads as
  cheap I'd rather know now than after players have opinions about it.
- Whether the arrival warning gives you enough time to react.
- Anything the alt could see, hear or interact with beyond sound and your
  damage — that's a bug and an important one.

---

## 6. Passes — *including the exploit check*

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

## 7. A full run, properly

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

**Data I want:** how many rounds you got through, and how it felt — see §11.

---

## 8. The workshop

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

## 9. Payouts — *the part that decides whether this is worth playing*

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

## 10. Ending a run, the awkward ways

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

## 11. Balance — the numbers I most want back

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

## 12. The action bar fight — *specific to your server*

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

## 13. Placeholders (skip if no PlaceholderAPI)

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

## 14. Two players and persistence

- [ ] Two players in runs at once, objectives in the **same** mine. Each counts
      only their own blocks.
- [ ] Restart the server mid-session. Stats, payouts, mine toggles and upgrade
      toggles all survive.
- [ ] Check the console for **any** stack trace across the whole session.

---

## What to send me

Zip or paste, in rough order of usefulness:

1. **The `/rb mines debug` output** (§1). Nothing else matters as much.
2. **Whether the alt could see, hit or be attacked by anything** (§5). If any of
   those is a yes, that's the most important line in your report.
3. **`logs/latest.log`** for the session, plus every stack trace in full.
4. **Your four end-round numbers** from §11, and whether the mobs changed them.
5. **What each mine says it holds** in `/rb quests` (§4), next to what you know
   is actually in it.
6. **`plugins/RoboBear/`** — the configs, `milestones.yml`, `mine-toggles.yml`,
   `upgrade-toggles.yml`, `objective-toggles.yml`, `mine-materials.yml`. Skip
   `data/` unless something looks wrong.
7. **Screenshots** of anything that looked wrong, with the slot number if it's a
   layout thing.
8. **Your notes**, one line each, in this shape:

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
  clicking, and so is the mine list in `/rb quests`.
- Objective **amounts** aren't editable in game, only the types and materials
  (`config.yml` under `objectives`, then `/rb reload`).
- Upgrade **prices** aren't editable in game, only on/off (`config.yml`, then
  `/rb reload`).
- Balance is unplaytested end to end — that's §11, not a bug.
- Milestone tiers ship empty on purpose; a fresh install paying nothing is
  correct behaviour, not a fault.
- No cross-server or database storage; one file per player.
- **Bystanders hear challenge mobs and see you take damage from nothing.** Sound
  and particles are positional and aren't tied to the entity, so there is no way
  to hide them along with the mob. Expected, not a bug.
- **Challenge mobs count against the world's mob cap** while a round is running.
  They're real entities because a packet-only mob can't attack anything.
- **Mine stock is an estimate.** It's a sample scaled up, so a mine that's half
  mined-out when the survey runs reads as half-sized, and a rare ore the stride
  never lands on is treated as unknown rather than absent. Wrong by a factor of
  two is fine; wrong by ten is worth reporting.
- The mob roster isn't editable in game — `config.yml` under `mobs.roster`, then
  `/rb reload`. `/rb mobs` shows it but doesn't change it.

---

## If something is badly broken

Stop, grab `latest.log`, and note what the last thing you did was. A single
stack trace with the action that caused it is worth more than an hour of
careful checklist work.
