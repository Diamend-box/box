# Changelog — BoxTutorial

All notable changes to **BoxTutorial** are documented here.

> This plugin was written with AI assistance (Anthropic's Claude).

## [1.1.0]
### Fixed
- **The charm could not be bought with a compressed log.** The check that
  confirms a custom-priced trade is being paid for properly counted the
  player's *backpack*, but by the time the trade fires the payment has already
  moved into the trade window's cost slots — so a player buying the charm with
  the only compressed log they owned was told "that isn't what this trade
  costs" while it sat on the counter in front of them. The cost slots are now
  what gets checked, which is also the stricter of the two: it asks whether the
  item being handed over is the real one, rather than whether a real one exists
  somewhere else on the player.

### Added
- **Mine drop tables.** A mine can override what a block gives when it's
  broken there:
  ```yaml
  mines:
    wood:
      drops:
        DARK_OAK_LOG: "OAK_LOG 4"
  ```
  Shipped that way, so the dark oak that makes up 10% of the wood mine is worth
  four ordinary logs instead of being a second item to work out what to do
  with. The `swap-dark-oak` trade it replaces has been removed.
- **A tier-two axe** (`axe_t2`) — an Efficiency II iron axe, sold for 24 logs
  and 2 raw iron, with a step that puts it *before* the 64-log grind it
  shortens. Bindable like every other reward.
- **`enchants:` on item slots**, e.g. `efficiency: 2`. Applied to the built-in
  default only: an item you bind arrives with whatever it already had, and
  nothing here edits it.

### Changed
- **A mine that is emptied refills immediately**, whatever `refill-at` says.
  The break counter is still the ordinary trigger, but it is a guess, and
  anything that removes a block without a break event — a reload, worldedit, a
  mine resized while somebody is standing in it — could leave a player waiting
  in an empty room for a mine that thought it was half full. Emptiness is now
  asked of the blocks, and the blocks have the last word.
- Step text follows the above: the dark logs are called out as worth four, and
  the axe upgrade lands between the armour and the grind.

## [1.0.0]
Initial release: `/tutorial` as a place rather than a checklist.

### Added
- **The practice yard.** `/tutorial` teleports the player into their own copy
  of a small arena — instances spaced 512 blocks apart in one void world, built
  fresh on claim, handed back on exit.
- **Regenerating mines**, cuboids with a weighted block table, in the shape any
  mine-reset plugin uses. Ships with a wood mine (90% oak, 10% dark oak) and an
  ore mine.
- **A villager trader** using real vanilla `MerchantRecipe`s, so the trade
  window is the one players already know. Ore is the currency; no economy
  plugin is involved anywhere.
- **A step ladder** — mine, buy, gear up — driven by triggers (`BREAK_BLOCK`,
  `BUY_ITEM`, `HAVE_ITEM`, `OFFHAND_ITEM`, `REACH_LOCATION`,
  `PLAYTIME_MINUTES`, and more), with a boss bar, a menu and a glossary.
- **Named item slots** (`axe`, `pickaxe`, `sword`, `armor`, `compressed_log`,
  `charm`) that staff can point at their own items with `/tutorial items` —
  hold it, click the slot. Bindings are stored as the item's own bytes, so a
  custom item from another plugin keeps its model data, tags and attributes.
- **The charm**: an off-hand item whose `stats:` become vanilla attribute
  modifiers, so the numbers apply when it's in the off hand and not otherwise,
  with no ticking task and nothing to leak.
- **Arena protection** — only the mines break, nothing places, nothing hurts
  anybody, and nobody falls out of the world.
- Players keep everything they made. PlaceholderAPI support
  (`%boxtutorial_...%`) when it's installed.
