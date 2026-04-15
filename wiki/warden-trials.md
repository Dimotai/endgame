---
title: Warden Trials
description: 4-tier wave survival combat encounters. Built on the WaveArena framework.
order: 7
published: true
---

# Warden Trials

4-tier wave survival combat encounters, migrated onto the generic **WaveArena framework** in v5.0.0.

Start a Warden Trial by using a **Warden Challenge** item (Tier I through IV). Each trial consists of **5 waves** of enemies that must be cleared before the timer expires.

Higher tiers feature stronger enemies, longer timers, and greater rewards. The final wave of Tiers II–IV includes a boss-tier enemy.

> [!NOTE]
> **v5.0.0:** Warden Trials now run on the data-driven [WaveArena framework](endgame-qol/api) (shared with temporal portals and the upcoming Void Realm Gauntlet). The framework is documented for third-party mods.

> [!WARNING]
> **Blocked Inside Instances** — Warden Challenge items cannot be used inside dungeon instances (temporal portals, Void Realm, Frozen/Swamp Dungeons). Using one inside prints a chat warning and cancels the trial.

## State Flow

**COUNTDOWN** (3s) → **SPAWNING** → **ACTIVE** → **WAVE_CLEAR** → **INTERVAL** (8s) → *repeat* → **COMPLETED** or **FAILED**

## Leash / Arena Boundary

Players are leashed to a **50-block radius** around the arena center. Leaving the radius doesn't fail the trial — the zone just warns you. The arena particle boundary visualizes the leash.

## Tier I — Adamantite Level

- **Timer:** 4:30
- **XP Reward:** 150

| Wave | Composition                                                         | Total |
|:----:|---------------------------------------------------------------------|:-----:|
|  1   | Goblin Scrapper x3, Skeleton Archer x2                              |   5   |
|  2   | Skeleton Soldier x2, Skeleton Mage x2, Spider x1                    |   5   |
|  3   | Hyena x2, Goblin Lobber x2, Skeleton Ranger x1                      |   5   |
|  4   | Outlander Brute x1, Skeleton Archmage x1, Skeleton Soldier x2       |   4   |
|  5   | Toad Rhino x1, Outlander Hunter x2, Skeleton Knight x2              |   5   |

## Tier II — Mithril Level

- **Timer:** 6:00
- **XP Reward:** 300
- **Final Boss:** Goblin Duke

| Wave | Composition                                                                               | Total |
|:----:|-------------------------------------------------------------------------------------------|:-----:|
|  1   | Trork Brawler x2, Skeleton Ranger x2, Trork Hunter x1                                     |   5   |
|  2   | Outlander Marauder x2, Outlander Stalker x2, Skeleton Archmage x1                         |   5   |
|  3   | Tiger Sabertooth x2, Trork Sentry x2, Skeleton Mage x1                                    |   5   |
|  4   | Saurian Warrior x2, Ghoul x2, Outlander Sorcerer x1                                       |   5   |
|  5   | **Goblin Duke x1**, Saurian Hunter x1, Skeleton Burnt Wizard x1, Werewolf x1              |   4   |

## Tier III — Onyxium Level

- **Timer:** 7:30
- **XP Reward:** 450
- **Final Boss:** Necromancer Void

| Wave | Composition                                                                                         | Total |
|:----:|-----------------------------------------------------------------------------------------------------|:-----:|
|  1   | Saurian Rogue x2, Skeleton Sand Mage x2, Ghoul x1                                                   |   5   |
|  2   | Werewolf x2, Skeleton Burnt Gunner x2, Skeleton Burnt Wizard x1                                     |   5   |
|  3   | Goblin Duke x1, Saurian Warrior x1, Skeleton Sand Archmage x1, Skeleton Burnt Gunner x1             |   4   |
|  4   | Shadow Knight x2, Skeleton Burnt Gunner x2, Golem Eye Void x1                                       |   5   |
|  5   | **Necromancer Void x1**, Shadow Knight x1, Skeleton Sand Archmage x1, Skeleton Burnt Gunner x2      |   5   |

## Tier IV — Prisma Level

- **Timer:** 9:00
- **XP Reward:** 600
- **Final Boss:** Shadow Knight + full roster

| Wave | Composition                                                                                                                    | Total |
|:----:|--------------------------------------------------------------------------------------------------------------------------------|:-----:|
|  1   | Goblin Duke x1, Necromancer Void x1, Skeleton Burnt Gunner x2, Skeleton Burnt Wizard x1                                        |   5   |
|  2   | Alpha Rex x1, Werewolf x1, Skeleton Burnt Wizard x2, Golem Eye Void x1                                                         |   5   |
|  3   | Necromancer Void x1, Alpha Rex x1, Skeleton Sand Archmage x2, Golem Eye Void x2                                                |   6   |
|  4   | Alpha Rex x2, Goblin Duke x1, Skeleton Burnt Gunner x2                                                                         |   5   |
|  5   | **Shadow Knight x1**, Alpha Rex x1, Skeleton Sand Archmage x1, Skeleton Burnt Gunner x1, Golem Eye Void x1, Necromancer Void x1|   6   |

## Wave Timer

Each tier has a configurable total timer. The HUD displays a countdown during active waves. The last **10 seconds** are displayed in red as a warning.

Setting a timer to `0` disables the timer for that tier, allowing unlimited time to clear waves.

## Fail Conditions

- **Death** — Player is killed during any wave
- **Disconnect** — Player disconnects from the server (`FailReason.DISCONNECT`)
- **Timer Expired** — Wave timer reaches zero before all enemies are defeated
- **Instance Blacklist** — Attempting to start inside a dungeon instance

## HUD Overlay

During an active trial, a persistent HUD overlay displays:

- **Wave Counter** — Current wave / total waves
- **Difficulty Color** — Tier-coded color indicator
- **Progress Bar** — Visual wave completion progress
- **Kill Count** — Enemies remaining in wave
- **Status / Timer** — Current state + countdown (red at 10s)

## Rewards

Each tier has its own drop table. XP is awarded on completion using the formula:

```
XP = Tier x 150
```

| Tier     | XP Reward | Material Level         |
|----------|:---------:|------------------------|
| Tier I   |    150    | Adamantite-tier drops  |
| Tier II  |    300    | Mithril-tier drops     |
| Tier III |    450    | Onyxium-tier drops     |
| Tier IV  |    600    | Prisma-tier drops      |

## Configuration

| Key                       | Default | Description                                              |
|---------------------------|:-------:|----------------------------------------------------------|
| `EnableWardenTrial`       |  true   | Enable or disable the Warden Trials system               |
| `WardenTrialTimerTier1`   |   270   | Tier I timer in seconds (4:30). Set to 0 to disable.     |
| `WardenTrialTimerTier2`   |   360   | Tier II timer in seconds (6:00). Set to 0 to disable.    |
| `WardenTrialTimerTier3`   |   450   | Tier III timer in seconds (7:30). Set to 0 to disable.   |
| `WardenTrialTimerTier4`   |   540   | Tier IV timer in seconds (9:00). Set to 0 to disable.    |

## Framework

Warden Trials are a concrete `WaveArenaConfig` registered with the shared engine. Third-party mods can build custom wave arenas (fixed waves or pool-generated) using the same API. See the [Developer API](endgame-qol/api) page.
