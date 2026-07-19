# DarkSea MythicMobs pack

The sea's enemies, ready to copy onto the server. Two files, two factions:

- **`Mobs/darksea-cursed.yml`** — *The Cursed*: Naxians and sailors consumed
  by the **Mariphage**, the curse-plague rotting the sea. Crazed Sailor →
  Mutated Naxian → Transmuted Naxian → Naxian Abomination.
- **`Mobs/darksea-cult.yml`** — *The Order of the Soul*: the monolith cult
  spreading the plague to wake a primordial power. Vironic Initiate →
  Acolyte → Templar → Lord.

The full storyline lives in [`../LORE.md`](../LORE.md). The tier-4 **boss**
(the half-revived primordial) is not in this pack yet — it gets its own file
and its own design round.

## Install

1. Copy the `Mobs/` folder into `plugins/MythicMobs/` on the server (so the
   files land in `plugins/MythicMobs/Mobs/`). The folder name is
   case-sensitive on Linux hosts — keep the capital M.
2. Run `/mm reload` (or restart).
3. Done — the plugin's shipped `mobs.yml` already references these internal
   names, so islands start spawning them immediately.

Without MythicMobs installed nothing breaks: every `mobs.yml` entry has a
vanilla `fallback` that spawns instead.

## Tuning

- **Internal names** (`CrazedSailor:` etc.) must keep matching `type:` in the
  plugin's `mobs.yml` — a CI test enforces this in the repo, but hand-edits
  on the server can drift.
- **`Health` / `Damage`** are the flat base stats. **`LevelModifiers`** add
  the listed amount per level above 1; `mobs.yml` passes level **1 / 3 / 6 /
  10** for rings 1–4, so one mob definition scales smoothly if you move it
  between rings.
- **Drops** replace vanilla drops entirely (`PreventOtherDrops`). Notable
  choice: the Vironic Lord drops its **totem of undying only 15%** of the
  time — farmable totems would defuse the whole hostile-sea premise. Raise
  it if that feels too mean.
- On-hit effects are the infection made mechanical: Poison (Mutated Naxian),
  Wither (Transmuted Naxian, native), Nausea (Abomination), Weakness
  (Acolyte). Chances sit at 30–40% so fights sting without being constant.

## Body choices (why these vanilla types)

Every mob had to work on a beach in broad daylight: no sun-burning, no
wandering into the sea, no surprise transformations. That ruled out drowned
and skeletons for the base bodies — the Mutated Naxian is a helmeted zombie
villager with a trident (harpoon) instead, with `PreventSunburn` and
`PreventTransformation` as belt-and-suspenders. Illagers (Pillager,
Vindicator, Evoker) don't render worn armor, so cult gear is stats-only.
