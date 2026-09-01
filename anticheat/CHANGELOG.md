# Changelog — AntiCheat

All notable changes to **AntiCheat** are documented here.

> This plugin was written with AI assistance (Anthropic's Claude).

## [2.0.0]
The combat-only anticheat grows into the whole threat model for the blatant
free clients — Meteor and Wurst.

### Added
- **Movement checks**, all off the main thread:
  - **Flight / Jetpack** — a predictive vertical-physics engine. Each tick it
    works out the vanilla-allowed velocity `(motionY − 0.08) × 0.98` and
    measures how far the client's real motion exceeds it. Liquids, climbables,
    levitation and slow-falling are exempt so they don't false-flag.
  - **Speed** — a deliberately generous horizontal ceiling, held across ticks.
  - **NoFall** — the client's `onGround` is ignored entirely; ground truth
    comes from the block cache, and enforce mode re-instates the fall damage.
  - **Timer** — a token-bucket packet balance: real time mints one token per
    50 ms and each movement packet spends one, so a sped-up client goes
    sharply negative.
  - **Vehicle / BoatFly** — the same physics with vehicle gravity, applied to
    `VehicleMove`; enforce cancels the move and dismounts the rider.
- **Scaffold** — placement delay plus a look-to-face raytrace, because bridging
  while staring forward isn't something a hand does.
- **Passive protections**, which starve the cheat of data rather than catching
  it: health indicators are fed a full health bar in outgoing `EntityMetadata`,
  ESP is answered with throttled line-of-sight entity culling (off by default —
  it must read the world), and X-ray defers to Paper's engine-mode-2 with a
  documented intercept outline.
- **A GUI control panel** (`/ac gui`) — mode, per-check and per-protection
  toggles, and a player violation browser, so the day-to-day never needs the
  config file.
- An **async-safe `BlockCache`**: the main thread refreshes a snapshot once per
  tick and the off-thread checks read that, so a movement check never touches
  the live world from a Netty thread.
- CI that builds the shaded jar, runs the unit tests, and boots a real Paper
  1.21.4 server to confirm a clean enable.

## [1.0.0]
Initial release: a packet-level combat anticheat for Paper 1.21.4.

### Added
- Four **combat checks** — **Reach** (eye-to-hitbox distance at attack time),
  **AutoClicker** (a CPS ceiling *and* unnaturally regular intervals),
  **Aim** (impossible pitch, and GCD analysis that fingerprints stepped
  synthetic rotation), and **KillAura** (bad hit angles, hits without a swing,
  rapid target switching).
- Built on **packetevents**, shaded and relocated: everything inspects packets
  in the Netty pipeline before the server processes them, rather than going
  through Bukkit events that are too coarse and too late.
- A decaying **violation-level** system, with latency, lag and player-state
  exemptions.
- Staff alerts with a per-staff verbose toggle, and the `/ac` command.
- **Alert-only by default** — it flags and notifies but never punishes until
  someone asks it to, via config or `/ac mode enforce`.
- The detection maths lives in pure, unit-tested classes; the packetevents
  surface is confined to one listener so the rest stays testable and portable.
