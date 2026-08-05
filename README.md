# Box

The Minecraft server plugins behind **Box** — a **Paper 1.21.4** boxpvp server.

This repository is a home for several **independent** plugins rather than one
project. Each lives in its own module directory with its own Maven build, its
own Java package, its own README and its own CI workflow. Nothing is shared
between them: no parent POM, no common library, no cross-dependencies. You can
run any one of them without the others, and they are built and downloaded
separately.

> ℹ️ **Made with AI.** Everything here was written by an AI assistant
> (Anthropic's Claude) working from a human's design, and is maintained the same
> way. It's said up front in the interest of transparency — review the code and
> test it on your own server before relying on it in production.

---

## The plugins

| Plugin | What it is | Module | Version |
| --- | --- | --- | --- |
| **[CustomAchievements](customachievements/README.md)** | Fully custom achievements built through an in-game GUI, with a player-facing menu, progress tracking, triggers and rewards. | `customachievements/` | 1.8.0 |
| **[BoxCore](boxcore/README.md)** | The server's utility and progression core — a modular plugin holding skill trees, collections, boosts, a personal compactor and fast travel. | `boxcore/` | 1.0.0 |
| **[AntiCheat](anticheat/README.md)** | A packet-level anticheat aimed at the blatant free clients (Meteor, Wurst) — combat, movement, world and passive protections. | `anticheat/` | 2.0.0 |

Each README is the full documentation for that plugin: features, commands,
permissions, configuration and developer notes. Start there.

### At a glance

| Plugin | Command | Package | Soft dependencies |
| --- | --- | --- | --- |
| CustomAchievements | `/achievements` (`/ca`), `/reopen` | `com.diamend.customachievements` | MythicMobs, AuraSkills, PlaceholderAPI (AnvilGUI is shaded in) |
| BoxCore | `/box` | `com.diamend.boxcore` | PlaceholderAPI |
| AntiCheat | `/anticheat` (`/ac`) | `com.diamend.anticheat` | — (packetevents is shaded in) |

All three target **Paper 1.21.4** and **Java 21**.

---

## Repository layout

```
.
├── customachievements/   # CustomAchievements — plugin, README, CHANGELOG, pom
├── boxcore/              # BoxCore — plugin, README, pom
├── anticheat/            # AntiCheat — plugin, README, pom
├── docs/                 # design notes and playtest scripts (see below)
└── .github/workflows/    # one CI workflow per plugin
```

---

## Building

There is no top-level build. Each plugin is a standalone Maven project — build
the one you want from its own directory:

```bash
cd customachievements && mvn -B clean package   # -> target/CustomAchievements-1.8.0.jar
cd boxcore           && mvn -B clean package    # -> target/BoxCore-1.0.0.jar
cd anticheat         && mvn -B clean package    # -> target/AntiCheat-2.0.0.jar
```

Drop the jar you want into your server's `plugins/` folder and restart.

> The builds pull `paper-api` (and packetevents / AnvilGUI / PlaceholderAPI)
> from external Maven repositories, so the build machine needs network access to
> them. In practice **CI is the build** — see below.

---

## CI

Every plugin has its own workflow, triggered only by changes under its own
directory — on pushes to `main` (or that plugin's development branch) and on
pull requests into `main`. Each one packages the jar, runs the unit tests
(against a real Bukkit/Paper API via MockBukkit) and boots a real headless
Paper 1.21.4 server to confirm the plugin enables cleanly and answers its
command.

| Plugin | Workflow | Jar artifact |
| --- | --- | --- |
| CustomAchievements | `.github/workflows/customachievements.yml` | `CustomAchievements-jar` |
| BoxCore | `.github/workflows/boxcore.yml` | `BoxCore-jar` |
| AntiCheat | `.github/workflows/anticheat.yml` | `AntiCheat-jar` |

The artifact from the last green run is the download.

---

## Docs

`docs/` holds material that spans a plugin's design rather than its usage:

- **[`risk-banking-spec.md`](docs/risk-banking-spec.md)** — the BoxPvP risk,
  banking and loss spec: how ore, banking fees and death loss are meant to work.
- **[`boxcore-playtest.md`](docs/boxcore-playtest.md)** — the playtest script for
  BoxCore's first real run on the server.
