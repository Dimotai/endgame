---
title: Commands
description: All commands, permission nodes, and the config UI reference.
order: 15
published: true
---

# Commands & Permissions

All EndgameQoL commands are under `/eg`.

## /eg

| Subcommand | Description | Permission |
|:-----------|:------------|:-----------|
| `/eg journal` | Open the Journal (native 3-tab page: Bounties, Bestiary, Achievements) | endgameqol.journal |
| `/eg pet` | Open the Pet UI (tier badge, ability list, feed button) | endgameqol.pet |
| `/eg config` | Open the configuration UI (7 tabs, search, recipe editor) | endgameqol.config |
| `/eg status` | Diagnostics dashboard | endgameqol.admin |
| `/eg lang <locale\|auto>` | Set display language (EN, FR, ES, PT-BR, RU) | None |

## /eg journal

The **Native Journal Page** replaces the previous HyUI builders with a single native `.ui` with 3 tabs:

- **Bounties** — active daily/weekly bounties, progress bars, streak tracker, reputation rank
- **Bestiary** — paginated mob cards, kill milestones per category, discovery badges
- **Achievements** — all 24 achievements with progress and claim state

## /eg pet

Opens the native Pet UI. Shows the active pet's tier badge (D → SS), ability list, and feed button. See [Pets](endgame-qol/pets).

## /eg config

**Opens the native configuration UI** with 7 tabs: Difficulty, Scaling, Weapons, Armor, Crafting, Misc, Integration.

Features: global search bar, editable value fields, recipe override editor with per-recipe editing, colored ON/OFF toggles, dark theme.

Permission: **endgameqol.config** (op-only by default)

## /eg admin

Permission: **endgameqol.admin** (op-only)

| Subcommand | Description |
|:-----------|:------------|
| `/eg admin debug boss <type>` | Dump active boss state |
| `/eg admin reset leaderboard` | Reset the combo leaderboard |
| `/eg admin reset bounties <player\|all>` | Force refresh bounties |
| `/eg admin reload` | Reload config from disk (async) |

## Permission Model

**Default-allow** — `/eg journal`, `/eg pet`, `/eg lang` work for all players by default (LuckPerms-compatible).

**Op-only** — `endgameqol.admin` and `endgameqol.config` require operator.

**Deny** — use negation: `-endgameqol.journal` or `-endgameqol.*`

## Removed in v5.0.0

- `/gauntlet` — infinite-wave arena removed. Will be replaced by a wave system integrated into temporal portal instances.
- `/bounty` — merged into `/eg journal`.
