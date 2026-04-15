---
title: Bosses & Elites
description: All boss encounters and elite mobs in EndgameQoL — phases, attacks, drops, difficulty presets, player scaling, and enrage mechanics.
order: 3
published: true
---

# Bosses & Elites

3 dungeon bosses and 6 elite mobs with configurable difficulty, redesigned boss bars, and boss combat music.

> [!NOTE]
> **Shared Boss Kill Credit** — All players in the instance receive kill credit, bestiary progress, and XP when a boss dies. Toggle with `SharedBossKillCredit` in config.

> [!TIP]
> **Proximity Boss Bar** — All bosses and elites display their HP bar when a player approaches within 50 blocks (auto-hides at 55, hysteresis). No damage required.

## Boss Bar Redesign

All 3 bosses and 6 elites share a unified HyUI boss bar with:

- Thematic per-boss color fill
- Glossy highlight overlay
- Gold phase threshold markers (multi-phase bosses)
- Numeric HP display (`current / max  •  %`)
- **INVULNERABLE** and **ENRAGED** status badges

`BossBarFocus` enforces one bar per player at a time (last-damaged wins) across all mods — see [Developer API](endgame-qol/api).

## Boss Combat Music

When a player enters a boss's 50-block range, a unique combat track plays. Music auto-stops on boss death or when all players leave the zone.

| Boss | Track |
| :--- | :--- |
| Dragon Frost | Custom — `Common/Music/Endgame/Endgame_Dragon_Frost_Fight.ogg` |
| Hedera | Vanilla placeholder (drop-in replaceable) |
| Golem Void | Custom — `Common/Music/Endgame/Endgame_Golem_Void_Fight.ogg` (plays only in Void Realm, gated by `Env_Endgame_Void_Realm`) |

To re-theme Hedera or Void Golem, drop a `.ogg` into `Common/Music/Endgame/` and update the `Tracks` entry in the matching `AmbFX_Boss_*.json`.

## Dungeon Bosses

### Dragon Frost

**Location:** Frozen Dungeon — 50% player scaling
**Health:** 1,400
**Detection Range:** 25 blocks (reduced from 80 — prevents aggro through dungeon ceilings)

Fly/walk boss with 3 HP-based phases and alternating immunity types.

| Phase | Name | HP | Behavior |
| :--- | :--- | :---: | :--- |
| Phase 1 | Sky Sentinel | > 70% | Flying. **Melee-immune.** Aerial barrages + Icy Wind. Use bows, crossbows, staves, spears. |
| Phase 2 | Ground Fury | 70–40% | Grounded. **Projectile-immune.** Melee chain + Stomp AOE + Frost Breath cone. Use melee. |
| Phase 3 | Hybrid Frenzy | < 40% | Rapid fly/ground cycling (8–10s each), alternating immunity, all attacks enabled. |

**Attacks:**

| Attack | Type | Phase | Notes |
| :--- | :--- | :--- | :--- |
| **Frost Bolt** | Ranged | 1, 3 air | Aerial barrage |
| **Icy Wind** | AOE Radial | 1, 3 air | New radial gust |
| **Swing / Bite** | Melee | 2, 3 ground | 15 Physical + 10 Ice |
| **Stomp** | AOE | 2, 3 ground | 40 Physical + 20 Ice, camera shake, Stagger |
| **Frost Breath** | Cone | 2, 3 ground | New ground cone |

When a player hits the dragon with an immune damage type, a one-time chat hint explains the active immunity. FlyIdle animation plays when hovering to cast.

**CAE System:** Separate Flying/Ground ActionSets drive attack selection (distance-aware, cooldown-gated). Flip `UseCombatActionEvaluator` on the Variant to revert to legacy sensor-cascade.

**Drops:** Dragon Heart, Mithril Bar, Storm Hide, Ice Essence, Frozen Sword

---

### Hedera

**Location:** Swamp Dungeon — 50% player scaling
**Health:** 1,800

| Phase | Name | HP |
| :--- | :--- | :---: |
| Phase 1 | Vine Growth | > 66% |
| Phase 2 | Toxic Bloom | 66–33% |
| Phase 3 | Corrupted Fury | < 33% |

**Attacks:**

| Attack | Type | Phase | Notes |
| :--- | :--- | :--- | :--- |
| **Poison Strike** | Melee | All | Applies poison DOT |
| **Root AOE** | AOE | All | Root eruption, snares players |
| **Scream** | AOE | All | Circular scream, knockback |
| **Vine Grab** | Pull | 2+ | New — grabs player with pull knockback |
| **Ground Slam** | AOE | 2+ | New — 7-block AOE + camera shake + Stagger |
| **Charge** | Lunge | 3 | New — runs at player (Run animation) |
| **Poison Clouds** | Environmental | 2+ | Lingering clouds every 18–25s |

All new attacks are health-gated via CAE conditions. Phases 2 and 3 are significantly harder with the new attacks, poison clouds, and spirit summons.

**Drops:** Hedera Gem, Onyxium Bar, Forest Essence, Voidheart

---

### Golem Void

**Location:** Void Realm — 50% player scaling
**Health:** 3,500
**MaxSpeed:** 14 (up from 8 — boss noticeably pursues players)
**KnockbackScale:** 0.0 (knockback-immune)

| Phase | Name | HP |
| :--- | :--- | :---: |
| Phase 1 | Void Awakening | > 66% |
| Phase 2 | Rift Surge | 66–33% |
| Phase 3 | Void Collapse | < 33% |

**Attacks:**

| Attack | Type | Notes |
| :--- | :--- | :--- |
| **Swing** | Melee | AOECircle hitbox |
| **Rumble** | AOE | Bias toward multiple nearby players |
| **Spin** | AOE | Bias toward multiple nearby players |
| **Ground Slam (Single/Double)** | AOE | AOECircle hitbox, Stagger on heavy slam |
| **Projectile Volley** | Ranged | LaunchForce 80/90 with HeadMotion Aim |
| **Jump Slam** | Signature | Unlocks < 66% HP, ramps < 33%, weighted 3x in pool |
| **Danger Zone** | AOE | 8m persistent DOT aura, Phase 3 |

**Jump Slam (signature):** The Golem telegraphs a ground slam, jumps high, teleports to the nearest player's position, and crashes down for **80 Physical AOE + Stagger** (1s stun). Camera shake + Explosion_Big + Impact_Critical on impact. **Void-safe:** if a player hovers over the void to bait the boss off-island, the Golem teleports back to its spawn position instead. Uses vanilla `JumpFar` / `FallFar` animations from Golem_Guardian.

**CAE System:** Jump Slam unlocks at < 66% HP and ramps at < 33%. AOE attacks bias up when multiple players are nearby.

**Spawn:** The boss no longer auto-spawns. Planned future introduction via a wave-survival encounter in the Void Realm (clear adds, then the Golem appears).

**Drops:** Onyxium Bar, Prisma Bar, Emerald, Portal Luminia

## Elite Mobs

Difficulty scaling applies to elite HP and damage. No player-count scaling. Compact elite health bar displayed overhead.

| Elite | Health | Location | Notes |
| :--- | :---: | :--- | :--- |
| **Dragon Fire** | 1,000 | Summoned (volcano) | Wild fire dragon, burn DOT, lava immunity |
| **Alpha Rex** | 700 | Zone 4 | Powerful cave predator, territorial |
| **Swamp Crocodile** | 900 | Swamp Dungeon | Ambush predator, poison bite |
| **Bramble Elite** | 550 | Swamp Dungeon | Thorny guardian, root + poison |
| **Zombie Aberrant** | 400 | Zone 4 | Void-corrupted undead, high physical damage |
| **Spectre Void** | 120 | Void Realm | Fast void spirit, evasive |

## Difficulty Presets

Server-wide difficulty affects all boss and elite HP/damage multipliers. Configurable via `/eg config`.

| Preset | HP Multiplier | Damage Multiplier |
| :--- | :---: | :---: |
| **Easy** | 60% | 50% |
| **Medium** | 100% | 100% |
| **Hard** | 150% | 150% |
| **Extreme** | 250% | 200% |
| **Custom** | 10–1000% | 10–1000% |

## Player Scaling Formula

```
multiplier = 1.0 + (playerCount - 1) * (scalingPercent / 100)
```

**Example — Golem Void / Hard / 4 Players / 50% Scaling:**

3,500 x 1.5 x (1.0 + (4 - 1) x 0.50) = 3,500 x 1.5 x 2.5 = **13,125 HP**

## Enrage Mechanic

When a boss receives more than **200 damage in 5 seconds**, it enters an enraged state (driven by `EnrageTracker` for all bosses):

| Property | Value |
| :--- | :--- |
| Damage Bonus | 1.5x damage for 8 seconds |
| Cooldown | 15 seconds before enrage can trigger again |
| Trigger | DPS exceeds 200 over 5-second window |
| Badge | **ENRAGED** on boss bar |

## Status Effects on Heavy AOE

The three heavy AOE boss attacks apply `Endgame_Stagger` (1s stun, derived from vanilla `Stun`):

- Void Golem Ground Slam / Jump Slam
- Frost Dragon Stomp
- Hedera Ground Slam
