# Endgame & QoL

[![CurseForge](https://img.shields.io/badge/CurseForge-Download-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/hytale/mods/endgame-qol) [![Discord](https://img.shields.io/badge/Discord-Join_Us-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/mrCyvJmC28) [![Ko-fi](https://img.shields.io/badge/Ko--fi-Support_Me-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/lewai)

Hytale server plugin adding endgame content: bosses, weapons, dungeons, NPCs, crafting, and quality-of-life features.

## Features

- **3 Boss encounters** — Dragon Frost, Hedera, Golem Void with multi-phase AI and player scaling
- **40 Weapons** — Longswords, daggers, spears, staves, battleaxes, maces, shields, bows across 7 material tiers
- **2 Dungeons** — Frozen Dungeon and Swamp Dungeon with unique enemies, traders, and loot
- **3 Armor sets** — Mithril, Onyxium, Prisma (helmet, chestplate, leggings, boots)
- **6 Accessories** — Trinket Pouch with Frostwalkers, Ocean Striders, Void Amulet, Blazefist, Pocket Garden, Hedera Seed
- **Achievement System** — 42 achievements across 8 categories (Combat, Boss, Bounty, Discovery, Crafting, Exploration, Speedrun, Mining)
- **Bounty Board** — 54 bounty templates with daily/weekly quests, mining and exploration bounties, reputation ranks
- **Combo Meter** — Kill streak tracker with tier effects
- **Warden Trials** — 4-tier wave survival challenge
- **Bestiary** — 32 NPC entries with kill milestones
- **Multi-language** — EN, PT-BR, RU, FR, ES (FR/ES active via "Use System Language" setting)
- **Database support** — Optional SQL persistence (SQLite, MySQL, MariaDB, PostgreSQL)

## Requirements

- **Java 25** (auto-provisioned by [Hygradle](https://github.com/hygradle/hygradle) — JetBrains Runtime with hot-reload support)
- **Hytale Server** `2026.03.26` or later

## Dependencies

| Dependency | Type | Bundled in JAR | Source |
|---|---|---|---|
| **Hytale:NPC** | Required | No (engine) | Built-in |
| **[HyUI](https://www.curseforge.com/hytale/mods/hyui)** | Required | Yes (shaded) | CurseForge |
| HikariCP | Internal | Yes (shaded) | Maven Central |
| [RPGLeveling](https://www.curseforge.com/hytale/mods/rpg-leveling-and-stats) | Optional | No | CurseForge |
| [EndlessLeveling](https://www.curseforge.com/hytale/mods/endless-leveling) | Optional | No | CurseForge |
| [OrbisGuard](https://www.curseforge.com/hytale/mods/orbisguard) | Optional | No | CurseForge |

HyUI and HikariCP are bundled inside the plugin JAR via shadow/shading — no separate download needed.
Optional dependencies go in your server's `Mods/` folder.

## Development Setup

This project uses [Hygradle](https://github.com/hygradle/hygradle) for build tooling, authentication, and dev server management.

### Build

```bash
./gradlew compileJava       # Compile only (fast check)
./gradlew build             # Full build + shadow JAR + auto-deploy to Mods folder
./gradlew shadowJar         # Shadow JAR only (no deploy)
```

### Dev Server (with hot-reload)

```bash
./gradlew startDevServer    # Build, authenticate, and launch server with hot-reload
```

First run will:
- Download the JetBrains Runtime JDK 25 (supports hot-reload)
- Download Hytale server assets
- Open your browser for OAuth authentication

Subsequent runs reuse cached JDK, assets, and auth tokens.

**Hot-reload workflow:**
1. Start the dev server: `./gradlew startDevServer`
2. Make code changes
3. In another terminal: `./gradlew compileJava`
4. Changes apply live — no server restart needed for method body changes

## Project Structure

```
src/main/java/endgame/plugin/
  EndgameQoL.java          # Main plugin class
  commands/                 # Slash commands (/eg, /egconfig, /egadmin)
  components/               # ECS components
  config/                   # BuilderCodec config system
  database/                 # Optional SQL persistence
  events/                   # Event handlers + domain events (GameEventBus)
  integration/              # Optional mod bridges (RPGLeveling, EndlessLeveling, OrbisGuard)
  managers/                 # Game managers (boss, combo, gauntlet, bounty, achievement)
  migration/                # Data migration helpers
  services/                 # Domain services (sound)
  spawns/                   # NPC spawn systems
  systems/                  # ECS systems (boss, weapon, effect, trial, accessory)
  ui/                       # HyUI pages and HUD
  utils/                    # Utilities
  watchers/                 # Entity watchers (temple events)

src/main/resources/
  manifest.json             # Plugin manifest
  Server/                   # Server-side JSON assets (items, NPCs, drops, instances, etc.)
  Common/                   # Client-side shared assets (models, textures, icons, UI, docs)
```

## Wiki

- **External wiki**: [wiki.hytalemodding.dev/mod/endgame-qol](https://wiki.hytalemodding.dev/mod/endgame-qol)
- **In-game wiki**: Install [Voile](https://www.curseforge.com/hytale/mods/docs) and use `/wiki` or `/voile` in chat

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE). You are free to use, modify, and redistribute this project under the terms of the GPL v3. Any derivative work must also be released under the same license.
