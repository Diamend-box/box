# AntiCheat

A **packet-level anticheat for Paper 1.21.4**, built to catch the blatant
free clients — **Meteor Client** and **Wurst** — that dominate public servers.
Those clients cheat by manipulating packets: fake vertical velocities, spoofed
`onGround`, vehicle exploits, inhuman rotation snapping, sped-up game ticks, and
reading ambient server data. This plugin fights them on the same layer.

> ℹ️ **Made with AI.** Written by an AI assistant (Anthropic's Claude) from a
> human's design. Review it and test it on your own server before trusting it in
> production — movement checks in particular reward tuning to your playstyle.

> This is a completely separate project from the *CustomAchievements* plugin in
> the same repository: different module (`anticheat/`), different package
> (`com.diamend.anticheat`), different purpose.

---

## Design principles

1. **No Bukkit `PlayerMoveEvent` / combat events.** Everything hooks
   [packetevents](https://github.com/retrooper/packetevents)' Netty pipeline and
   inspects packets *before* the server processes them. Bukkit events are too
   coarse and too late.
2. **Maths off the main thread.** All prediction, raytracing and statistics run
   on packetevents' Netty threads — never the server tick loop. The main thread
   is only ever touched to **cancel a packet** or **carry out a punishment**
   (and for the one job that genuinely must read the live world — ESP occlusion
   — on a throttled cadence). World state the off-thread checks need is served
   from an async-safe [`BlockCache`](src/main/java/com/diamend/anticheat/world/BlockCache.java)
   snapshot that the main thread refreshes once per tick.
3. **Alert first, punish only on request.** Ships in **alert-only** mode: it
   flags and notifies staff but never punishes. Flip a single switch (GUI,
   config, or `/ac mode enforce`) to enable the configured punishments.
4. **GUI-driven.** Everything day-to-day lives in `/ac gui` — toggle checks and
   protections, switch modes, inspect and reset players — no config editing.

---

## What it catches

### Combat
| Check | Signal |
|-------|--------|
| **Reach** | Eye-to-hitbox distance beyond survival's 3.0 blocks (nearest-point AABB math). |
| **AutoClicker** | Sustained CPS ceiling **and** unnaturally regular click intervals (low coefficient of variation). |
| **Aim** | Impossible pitch, and synthetic rotation (GCD analysis of yaw/pitch deltas — the fingerprint of stepped, generated aim). |
| **KillAura** | Hitting at bad angles, hitting without a swing, and rapid multi-target switching. |

### Movement
| Check | Signal |
|-------|--------|
| **Flight / Jetpack** | A **predictive vertical-physics engine**. Each tick it computes the vanilla-allowed velocity `(motionY − 0.08) × 0.98` and measures how far the client's real vertical motion *exceeds* it. Sustained lift while provably off the ground — or one big instant lift — is flagged. Swimming, ladders, cobwebs, levitation and slow-falling are exempted so they don't false-flag. |
| **Speed** | Deliberately generous horizontal-speed ceiling that only blatant Speed / horizontal-Flight breaches, held across several ticks. |
| **NoFall** | The client's `onGround` flag is **ignored entirely**. Ground truth comes from the block cache; claiming to stand with no block below (after a real fall) or while dropping fast is flagged, and enforce mode re-instates the fall damage. |
| **Timer** | A **token-bucket packet balance**: real time mints one token per 50 ms, each movement packet spends one. A sped-up client spends faster than time mints and the balance goes sharply negative. |
| **Vehicle / BoatFly** | The same predictive physics with boat/entity gravity, applied to `VehicleMove` packets. Enforce mode cancels the move and dismounts the rider. |

### World
| Check | Signal |
|-------|--------|
| **Scaffold** | **Delay** (placements spaced ~0 ms apart, or with inhumanly uniform rhythm) **and** a **raytrace-face** test (the angle between where the player looks and the block face they claim to place against — bridging while staring forward or at the sky is impossible for a human). |

### Passive protections (starve the cheat of data)
| Protection | How |
|------------|-----|
| **Anti health-indicators** | Rewrites other players' health to full (20.0) in outgoing `EntityMetadata`, so Meteor-style HP nametags read nothing useful. The server's real health is untouched; you still see your own true health. |
| **Anti-ESP (entity culling)** | Hides players who are out of a viewer's line of sight, so ESP has nothing to draw. Uses Bukkit's `hideEntity`/`showEntity` (which emit the destroy / re-spawn packets correctly). **Off by default** — it must read the world for occlusion, so it runs throttled on the main thread; enable it only if ESP is a real problem and watch CPU on crowded servers. Staff are never hidden. |
| **Anti-Xray** | *Not reimplemented* — see below. |

#### A note on Anti-Xray
Rewriting chunk packets to obfuscate ores means decoding the section palette and
block-state containers, swapping hidden ores for stone, and re-encoding — and
getting it subtly wrong corrupts the client's view of the world. Paper already
ships a mature, heavily-optimised obfuscator that does exactly this, so this
plugin **defers to it** rather than shipping a fragile copy. Enable it in
`paper-world-defaults.yml` with `engine-mode: 2` (replaces hidden ores with
random fake ores).

The interception approach, for reference: listen on the outgoing `ChunkData`
packet, walk each 16×16×16 section's block-state palette, replace buried ore
states (those with no exposed face) with stone/deepslate, re-pack the data
container, and let the normal block-update packet reveal the true block only
once the player mines adjacent to it.

---

## The violation system

Every flag adds to a per-check, per-player **violation level (VL)** that
**decays over time**, so a one-off false positive never accumulates into a
punishment. Two staff streams:

- **Alerts** — sent to `anticheat.alerts` holders once a check crosses its
  `alert-threshold`.
- **Verbose** (`/ac verbose`) — every single flag, for tuning.

In **enforce** mode, each check runs its configured `actions` as the VL crosses
thresholds:

```yaml
actions:
  - "12 cancel"                 # cancel the offending packet at VL 12
  - "25 kick Flight"            # kick at VL 25
  # also: "<vl> command <cmd with %player%>", "<vl> ban <reason>"
```

---

## Usage

`/ac` (alias for `/anticheat`) — bare command opens the GUI for players.

| Subcommand | Purpose |
|------------|---------|
| `/ac gui` | Open the control panel. |
| `/ac verbose` | Toggle per-flag output for yourself. |
| `/ac info <player>` | Show a player's live violation levels. |
| `/ac reset <player>` | Clear a player's violations. |
| `/ac mode [alert-only\|enforce]` | View or switch the response mode. |
| `/ac checks` | List checks and their state. |
| `/ac reload` | Reload `config.yml`. |

### The GUI (`/ac gui`)
- **Mode button** — click to flip alert-only ⇄ enforce.
- **Check icons** — green = on, red = off; click any to toggle (persists).
- **Protection icons** — health-indicator masking and anti-ESP toggles.
- **Players** — browse online players (heads show total VL); click one to see
  its per-check levels and reset them.
- **Verbose / Reload** buttons.

### Permissions
| Node | Default | Grants |
|------|---------|--------|
| `anticheat.command` | op | Use `/ac` and the GUI. |
| `anticheat.admin` | op | Change mode, toggle checks/protections, reload. |
| `anticheat.alerts` | op | Receive alerts (and are never ESP-culled). |
| `anticheat.bypass` | false | Exempt from every check (and never ESP-culled). |

---

## Building

Standard Maven project targeting Java 21:

```bash
cd anticheat
mvn -B clean package
# -> target/AntiCheat-2.0.0.jar   (packetevents shaded & relocated inside)
```

Drop the single jar in `plugins/` on a **Paper 1.21.4** server. No separate
packetevents install — it's shaded in. What changed between versions is in
[`CHANGELOG.md`](CHANGELOG.md).

> CI builds this on every push (`.github/workflows/anticheat.yml`) and uploads
> the finished jar as the **`AntiCheat-jar`** artifact, plus boots a real Paper
> server to confirm the plugin enables cleanly. That artifact is the download.

---

## Limitations & honesty

- Movement checks are heuristic. Defaults are tuned conservatively (generous
  thresholds, decay, alert-only) so they favour *not* punishing over false
  positives — which means a determined, subtle cheat may need threshold tuning
  to catch. Start in alert-only, watch the alerts, then tighten.
- The block cache samples a small box around each player; ground truth outside
  that box reads as "unknown", and the checks stay conservative when unsure.
- Anti-ESP occlusion uses Bukkit line-of-sight and costs main-thread CPU; it is
  off by default for that reason.
- This catches **blatant** clients well. It is not a commercial anticheat and
  makes no promise against bespoke, server-specific cheats.
