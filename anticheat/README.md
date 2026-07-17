# AntiCheat

A **packet-level combat anticheat** for Minecraft **1.21.4** (Paper), built on
[packetevents](https://github.com/retrooper/packetevents). It watches combat
packets, scores suspicious behaviour with a decaying violation-level (VL)
system, and — depending on the configured mode — either just alerts staff or
enforces punishments.

> ℹ️ **Made with AI.** Like the rest of this repository, this module was written
> by an AI assistant working from a human's requests. Review the code and test
> it on your own server before relying on it in production. A combat anticheat
> is heuristic by nature: run it in the default alert-only mode first and tune
> the thresholds against your own players before enabling enforcement.

This is a **standalone plugin** with its own `pom.xml`, independent of the
CustomAchievements plugin at the repository root. Building one does not affect
the other.

---

## What it checks (v1)

All four checks are **combat** focused and run off the packet stream:

| Check | Signal |
|---|---|
| **Reach** | Distance from the player's eye to the target hitbox at attack time exceeds survival reach (default `3.05`). |
| **AutoClicker** | Sustained CPS above a human ceiling, **or** click intervals that are unnaturally regular (low coefficient of variation) even at plausible rates. |
| **Aim** | Impossible pitch (outside ±90°), and consecutive yaw deltas that share a suspiciously large common divisor (GCD analysis of software-generated rotation). |
| **KillAura** | Landing hits well outside the direction faced, hitting with no swing animation, or sweeping a second distinct target faster than a human could re-aim. |

**Reach** and **AutoClicker** are the reliable workhorses. **Aim** and
**KillAura** are heuristic and intentionally conservative (small per-event VL,
higher alert thresholds) to protect legitimate PvP from false positives.

## Alert-only vs. enforce

The `mode` setting controls the response:

- **`alert-only`** *(default)* — flag, notify staff, and log. Never punishes.
- **`enforce`** — everything alert-only does, **plus** it runs the per-check
  `actions` (cancel the hit, run a command, kick, or ban) as each VL threshold
  is crossed.

Switch live with `/ac mode enforce` (or back with `/ac mode alert-only`); the
choice is written to `config.yml`.

## Not cheating — exemptions

To keep false positives down, checks are skipped for players who are: exempt by
permission (`anticheat.bypass`), in creative/spectator, gliding with elytra,
using a riptide trident, in a vehicle, dead, within the login/teleport grace
window, above the ping ceiling, or while the server TPS is below the configured
floor. All of these are tunable under `exemptions:` in the config.

---

## Commands & permissions

Base command `/anticheat` (alias `/ac`):

| Command | Description | Permission |
|---|---|---|
| `/ac verbose` | Toggle per-flag output for yourself | `anticheat.command` |
| `/ac info <player>` | Show a player's current violation levels | `anticheat.command` |
| `/ac reset <player>` | Clear a player's violation levels | `anticheat.command` |
| `/ac mode [alert-only\|enforce]` | View or switch the response mode | `anticheat.admin` to change |
| `/ac checks` | List checks and whether each is enabled | `anticheat.command` |
| `/ac reload` | Reload `config.yml` | `anticheat.admin` |

| Node | Default | Grants |
|---|---|---|
| `anticheat.command` | op | Use `/ac`, view info, toggle verbose |
| `anticheat.admin` | op | Change mode, reload |
| `anticheat.alerts` | op | Receive alerts |
| `anticheat.bypass` | nobody | Be exempt from all checks |

---

## Design

The plugin keeps the packet library at arm's length so the detection logic
stays testable and portable:

- **`packet/CombatPacketListener`** is the *only* file that touches packetevents.
  It pulls primitives (entity id, yaw/pitch, timestamps, hitbox) out of packets
  and hands them to the checks.
- **`check/combat/*`** decide pass/fail from those primitives and return a
  `CheckResult`. The numeric cores live in **`util/`** (`ReachCalculator`,
  `CpsAnalyzer`, `AimAnalyzer`, `MathUtil`) as pure functions.
- **`violation/ViolationManager`** turns a `CheckResult` into consequences and is
  the single place that honours the alert-only/enforce toggle. VL maths runs on
  the network thread; every Bukkit side effect is bounced to the main thread.
- **`exempt/ExemptionManager`**, **`alert/AlertManager`**, **`config/*`**,
  **`player/*`**, **`command/*`** round it out.

Because the detection cores are pure, they are covered by JUnit tests
(`src/test/java`) without needing a running server or a packet pipeline.

---

## Building

```bash
cd anticheat
mvn clean package
```

The finished plugin is written to `target/AntiCheat-1.0.0.jar` with packetevents
shaded and relocated inside it — server owners just drop in that one jar; there
is no separate packetevents install.

> **Network note.** The build resolves `paper-api` from `repo.papermc.io` and
> `packetevents-spigot` from `repo.codemc.io`. Build on a machine (or CI) that
> can reach both. In sandboxes whose egress policy blocks those hosts, the build
> and the tests can't fetch their dependencies and will fail at resolution —
> that's an environment restriction, not a code problem.

### `packetevents.version`

Pinned to `2.7.0` in `pom.xml` (property `packetevents.version`). If you target
a newer packetevents, bump that property; the only packetevents-specific code is
in `CombatPacketListener`, so any API drift is contained to that one file.
