# BoxCore — playtest script

**For:** the first real run of BoxCore on boxpvp (Paper 1.21.4).
**Time:** ~15 minutes for the smoke pass, ~60 for the whole thing.
**Point of it:** find the things that only break with a real player in a real
world, and come back with enough detail that each one can be fixed without a
second trip in game.

Work top to bottom. Anything that behaves oddly, note it and **keep going** —
one broken thing shouldn't cost you the rest of the pass.

---

## Round two — what changed after your first pass

Everything below came out of your notes. Re-test these first; the rest of the
script is still valid but you've already run it once.

**§04 — expansion.** You saw nothing at all, which ruled out the space check
(that one always says why it refused) and pointed at the click never arriving.
Three things could swallow it and all three are closed: the handler no longer
ignores interacts another plugin has cancelled, a failed expand now always says
which reason it was, and **compacted units glint** so they can't be confused
with the raw item they were made from.

- [ ] Right-click a unit. If it still does nothing, try `/box expand` — a
      command can't be intercepted, so this tells us whether the expansion logic
      or the click is at fault. **That answer is the whole point of this step.**
- [ ] Sneak-right-click a stack — expands the lot.
- [ ] Check the units glint and the raw ore doesn't.

**§04 — adding recipes.** This already existed: `/box compactor recipes` →
hold the item → **Add a recipe**. It just wasn't findable empty-handed.

- [ ] Click **Add a recipe** with an empty hand and type a material name.

**§05 — boosts.** Drop boosts now apply *after* every other plugin has edited
the drop list, so they multiply what CustomDrops and AuraSkills produced instead
of landing beside it.

- [ ] Start a 2x drop boost and mine with Lucky Miner active. Is it
      multiplicative now?
- [ ] Same with CustomDrops. **If it's still additive, I need CustomDrops'
      config** — it may be spawning items itself rather than through the drop
      event, and no priority change reaches that.
- [ ] Worth checking: `boosts.drops.ores-only` is `true`, so anything
      CustomDrops replaces a drop with that isn't whitelisted ore won't be
      boosted at all. Turn it off if that's biting.
- [ ] `/box boost item server-drops-2x` → a new global boost item. Using it
      starts a server-wide boost, announced to everyone.

**§06 — placing destinations.** Warps now land on the centre of the block and
round to the nearest quarter turn.

- [ ] Place one while standing sloppily off-centre and at an angle. Arrive at
      it — you should land centred and square.
- [ ] `/box warp` → a destination → **Facing** cycles it a quarter turn at a
      time. Set one deliberately and arrive to check.
- [ ] `travel.snap.centre` / `travel.snap.facing` in config if you want it off.

**§07 — the Discord leak.** Confirmed and fixed the only way it can be: answers
are typed into an **anvil rename box** now, never into chat, so there is no chat
message for a bridge to relay.

- [ ] Every editor prompt: the anvil screen opens, the question is its title,
      the current value is already in the box. Type, click the result item.
- [ ] **Watch Discord.** Nothing should appear.
- [ ] Close the anvil without clicking the result → nothing changes, menu comes
      back.
- [ ] If you ever get asked in *chat* instead, tell me — that's the fallback,
      and it means the anvil wouldn't open.
- [ ] Note: the anvil caps answers at 50 characters. Say if that bites on
      description lines.

**Modules.** `/box modules` is now a menu.

- [ ] Switch a module off. Its hub icon goes, its commands refuse, its
      placeholders go blank, its items stop doing anything.
- [ ] Restart — it's still off.
- [ ] Switch it back on without a restart.
- [ ] Edit `modules.<id>.enabled` in the file and `/box reload` — that works now
      too.

**§00.** `warps.yml` is written on first load, so all the files are there.

---

## 0. Before you start

1. Grab the jar from the last green **Verify BoxCore** run on GitHub Actions →
   artifact **`BoxCore-jar`**. (Don't build it locally; the build lives in CI.)
2. Drop it in `plugins/`, start the server, **op yourself**.
3. If this server has run *any* earlier BoxCore build, say so in your notes —
   the answer changes whether some renames are safe. See §9.

**Capture the boot before you touch anything:**

- [ ] Save `logs/latest.log` from the first boot as `boot.log`.
- [ ] Run `/box modules` and copy the whole output.
- [ ] `ls plugins/BoxCore/` — note which files exist
      (`config.yml`, `trees.yml`, `collections.yml`, `compactor.yml`,
      `warps.yml`, `playerdata/`).

If the server has PlaceholderAPI, note that too — §8 needs it and is skippable
without it.

---

## 1. Smoke pass (do this even if you have no time for the rest)

- [ ] `/box` — the hub opens.
- [ ] Every hub icon opens its menu, and the back arrow returns to the hub.
      Hub slots in use: skills, collections, fast travel, compactor, boosts.
- [ ] Close every menu with the barrier. Nothing lingers on screen.
- [ ] `/box reload` — says it reloaded, no stack trace in console.
- [ ] Break 20 stone. `/box collections` shows the count moving.

**Data:** if a menu is empty, misaligned, or an icon is missing, screenshot it
and say which slot (count from 0, top-left, left to right).

---

## 2. Skills

- [ ] `/box points give <you> 20`, then `/box skills`.
- [ ] Unlock a node. Check: point cost deducted, effect actually applied
      (health/speed/etc. visibly change), and the unlock message reads sensibly.
- [ ] Try to unlock something you can't afford — the refusal should name the
      cost and what you have.
- [ ] `/box respec` with no nether star → refused. Give yourself one, respec →
      every point comes back and effects come off.

**Data I want:**
- The exact text of any message that reads awkwardly, unfinished, or wrong.
- Any node whose lore promises something you can't feel in game.

---

## 3. Collections

- [ ] `/box collections` — the category icons should sit in a tidy row under
      the header, **six of them across slots 11–16**. If they look shoved to one
      side, screenshot it.
- [ ] Open a category, then a collection. Tier bar, "next tier at", reward line.
- [ ] Mine enough of one thing to cross a tier. Does the skill point arrive, and
      does the tier-up feel like it happened (message + sound)?
- [ ] Place a block, break it again — that must **not** count.
- [ ] `/box collection set <you> cobblestone 999999` then reopen — a maxed
      collection should say fully collected, not show a broken bar.

**Data I want:**
- How long the first tier of the first collection took you, roughly.
  (You've parked payout tuning — this is just a number to park with it.)

---

## 4. Compactor

- [ ] `/box compactor give 2` → you get a Personal Compactor 5000 (3 slots).
- [ ] Right-click it → the compactor menu; slot a recipe into it.
- [ ] Mine 64+ of that item → it folds into a unit as you go.
- [ ] Right-click a unit → it expands back. Mine a bit more within 30s — it must
      **not** immediately re-fold (that's the expand grace).
- [ ] Try to drop the compactor / die with it — it should survive.
- [ ] `/box compress off` → nothing folds. `/box compress on` → it resumes.

**Data I want:**
- Whether folding ever eats items or gives back the wrong count. If it does, I
  need: what you were mining, which recipe was slotted, how many you had before
  and after.

---

## 5. Boosts

- [ ] `/box boost global drops 2 10m` → the actionbar line appears and counts.
- [ ] `/box boost` → the menu shows it, and the countdown ticks while it's open.
- [ ] `/box boost item drops-2x` → you get the bottle; right-click it to start.
- [ ] Let one expire (or `/box boost clear global`) — you get told, not left to
      notice.
- [ ] Two boosts at once: do they multiply, and does the cap message appear?

**Data I want:** any point where the actionbar fights with something else for
the same line (the travel countdown is the one to watch — see §6).

---

## 6. Fast travel — *new, needs the most attention*

Set up (as staff):

- [ ] Stand somewhere, `/box warp` → the destination editor.
- [ ] Hold a block, click **Add a destination here**, type a name in chat.
      → it should be created where you stand, wearing that block as its icon,
      and drop you into its own screen.
- [ ] On that screen, set each of: icon, rename, move, radius, description
      (add / remove last / clear), permission (typed, `boxcore.warp.<id>`
      shortcut, and clear).
- [ ] Make a second destination somewhere far away.
- [ ] `/box warp list` — both are there. Check `plugins/BoxCore/warps.yml` looks
      sane.

As a player (use an alt or de-op yourself):

- [ ] `/ft` → the menu. A place you haven't walked into shows as **???**.
- [ ] Walk into one → discovery message **and a sound**. Reopen: it's named now.
- [ ] Click it → warmup countdown on the actionbar, a tick sound each second,
      then you arrive.
- [ ] Start a trip and **move** → cancelled, with a sound.
- [ ] Start a trip and **take damage** → cancelled.
- [ ] Get hit by another player, then try to travel → refused, with the time
      left. **Leave the menu open** and watch: within a second of the tag
      expiring the item should change from "Not while you're in combat" to
      "Click to travel" on its own. This is new — it either works in front of
      you or it doesn't.
- [ ] Hit by a **mob** → travel should still be allowed.

**Data I want, specifically:**
- Are the four travel sounds right — discovery, warmup tick, arrival, cancel?
  Too loud / too quiet / wrong sound / annoying after the tenth time? Name the
  ones to change; I'll change them.
- Ordering: found places sort to the top by default. Does that read right, or do
  you want `travel.menu-order` set to `name`, `distance` or `file`? Try
  `distance` before you decide — note that it also hints at how far away the
  places you *haven't* found are, which may be a feature or a leak depending on
  how you feel about it.
- Does the warmup length (3s) feel right for a PvP server, or should it be
  longer near the box and shorter at spawn?

---

## 7. The in-game editors (chat prompts)

Both editors ask questions in chat now. That path is new and worth abusing:

- [ ] Start a prompt, then type `cancel` → nothing changes, menu reopens.
- [ ] Start a prompt, then **walk away and talk normally** — your message must
      go to chat as a message, not get eaten... except the *first* line you type
      while a question is open, which is the answer. Confirm that's what happens.
- [ ] Start a prompt and **log out**, log back in, talk → your message is not
      swallowed.
- [ ] Start a prompt, leave it two minutes, then talk → not swallowed (it lapses).
- [ ] `/box compactor recipes` → a recipe → **Name the unit** → type
      `<gold><ratio> coal`. Check a freshly made unit shows the number, and that
      it still reads right after you change the amount with the step buttons.
- [ ] Same screen → **Describe the unit**: add two lines, right-click to drop
      the last, shift-click to clear.
- [ ] Delete something from each editor: warps need a shift-click on the
      destination's own screen; recipes need a shift-click too. Confirm a plain
      click never deletes anything.

**Data I want:** any case where a typed answer went to public chat, or where a
normal chat message got swallowed. That's the one genuinely risky thing in this
release — exact steps if it happens.

One to check deliberately if you run a chat bridge (Discord, etc.): BoxCore
cancels the answer at the earliest priority it can, but a bridge that listens
just as early may still relay it. Type a prompt answer with the bridge on and
see whether it shows up in Discord. If it does, the fix is the same one
CustomAchievements took — read the answer from an anvil rename box instead of
chat — and that's worth knowing before staff start typing permission nodes.

---

## 8. Placeholders (skip if no PlaceholderAPI)

Put a few on a scoreboard or run them through PAPI's parse command:

```
%boxcore_points%              %boxcore_collected%
%boxcore_travel_found%        %boxcore_travel_total%
%boxcore_travel_percent%      %boxcore_travel_combat%
%boxcore_travel_combat_time%  %boxcore_travel_warmup%
%boxcore_compressor_has%      %boxcore_boost_drops%
```

- [ ] None of them render blank or as the raw `%…%` text.
- [ ] `travel_total` counts only what you're allowed to see (make one warp
      staff-only and check it drops by one for a non-staff player).

**Data:** paste the rendered line. A blank means I named something wrong.

---

## 9. Persistence, reload, and two players

- [ ] Restart the server. Points, collections, discovered places and boosts all
      survive.
- [ ] `/box reload` while a menu is open, and while a trip is running. Nothing
      should throw.
- [ ] Two players online: PvP tag each other, both try to travel, both refused.
- [ ] `/box reset <alt>` wipes that player only.
- [ ] Check the console for **any** stack trace across the whole session.

**The one question that blocks work:** *has this server ever run an earlier
BoxCore build with player data?* If no, I can finish renaming the
compressor/compactor mess (module id `compressor`, package `ore`, config root
`compressor:`, `%boxcore_compressor_*%` placeholders) into one name. If yes,
that rename breaks live configs and player data, and I'll do it with a migration
instead — or not at all. **Yes or no is enough.**

---

## What to send me

Zip or paste, in rough order of usefulness:

1. **`logs/latest.log`** for the whole session (or the last 300 lines if it's
   huge, plus every stack trace in full).
2. **`plugins/BoxCore/`** — the config files and `warps.yml` as they ended up.
   Skip `playerdata/` unless something there looks wrong.
3. **`/box modules` output.**
4. **Screenshots** of anything that looked wrong on screen, with the slot number
   if it's a layout thing.
5. **Your notes**, one line each, in this shape:

```
[where]   /ft menu, second page
[did]     clicked a place I'd found while combat-tagged
[wanted]  refusal message
[got]     nothing happened at all, menu stayed open
[log]     no error in console
```

Rough is fine. `[wanted]` and `[got]` are the two that matter — I can usually
find the rest from those.

---

## Known-unfinished — don't spend time reporting these

These are already on my list, so skip them unless they're worse than described:

- Travel has no per-warp sounds and no grouping/categories — one flat list.
- The travel menu's pages don't remember which page you were on when you come
  back from a trip.
- `OreValues` has a dead accounting API nothing calls (invisible in game).
- The compressor/compactor naming drift, pending your answer in §9.
- Collection payout tuning — parked by you, deliberately.
- There is no boost shop; you're building that.

---

## If something is badly broken

Stop, grab `latest.log`, and note what the last thing you did was. A single
stack trace with the action that caused it is worth more than an hour of
careful checklist work.
