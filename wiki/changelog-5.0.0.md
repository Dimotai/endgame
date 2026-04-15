---
title: Changelog 5.0.0
description: Full v5.0.0 release notes — new systems, content, reworks, and technical changes.
order: 100
published: true
---

# Changelog — v5.0.0

## New Features

### Temporal Portal System
Random portals spawn near players every 5–10 min, leading to temporary dungeon instances. Lifetime stages (STABLE → DESTABILIZING → CRITICAL → COLLAPSING). Respects OrbisGuard and SimpleClaims. **3 instances**: Eldergrove Hollow, Oakwood Refuge, Canopy Shrine. See [Temporal Portals](endgame-qol/temporal-portals).

### Pet Companion System
4 boss pets (Dragon Frost, Dragon Fire, Golem Void, Hedera) dropped 10–15% from bosses. 6 tiers (D → SS) via feeding. Mount at Tier C+, passive perks at S, aura at SS. `/eg pet` UI. See [Pets](endgame-qol/pets).

### Void Realm
New dimension with a floating island arena. Home of the Golem Void boss. Entry via **Void Realm Key** (renamed from "Shard of the Void" — auto-migrated). Dedicated combat music plays throughout the dimension. See [Void Realm](endgame-qol/void-realm).

### Void Gauntlet
3-wave survival encounter inside the Void Realm before facing Golem Void. **No leash fail** — free movement on the island.

### Endgame Gateway Block
New craftable portal block (Epic, Endgame_Bench T1). Right-click with an Endgame key to swap it into a themed variant:

- ❄️ **Frozen** (cyan/ice) → Frozen Dungeon
- 🌿 **Swamp** (green) → Swamp Dungeon
- 🟣 **Void** (purple) → Void Realm

Each variant has its own particles, glow color, and sound. Breaking any variant drops the base Gateway back so the block is never lost. Vanilla Portal_Device rejects Endgame keys with a chat redirect. See [Portals & Gateways](endgame-qol/portals-gateways).

### Dragon Frost Rework
Fly/walk boss with 3 HP-based phases:

- **Phase 1 (>70%)** — Flying, **melee-immune**. Frost Bolt barrage + new **Icy Wind** radial gust.
- **Phase 2 (70–40%)** — Grounded, **projectile-immune**. Melee + Stomp + new **Frost Breath** cone.
- **Phase 3 (<40%)** — Cycling fly/ground (8–10s), alternating immunity, all attacks enabled.

One-time chat hint when a player hits with an immune damage type.

### Hedera Rework
3 new attacks: **Vine Grab** (pull, P2+), **Ground Slam** (AOE + Stagger, P2+), **Charge** (lunge, P3). Lingering poison clouds every 18–25s during P2+.

### Void Golem — Jump Slam Signature
New signature attack unlocks at Phase 2+ and ramps at Phase 3: telegraphed teleport-and-crash for **80 Physical AOE + 1s Stagger**. Void-safe fallback if the player baits the boss off the island. Knockback-immune, MaxSpeed 14.

### Boss Bar Redesign
Fully rebuilt HyUI boss bar shared by all 3 bosses and 5 elites. Thematic per-boss color fill, glossy highlight overlay, gold phase threshold markers, numeric HP display, INVULNERABLE / ENRAGED status badges.

### Proximity Boss Bar Display
HP bar shows within 50 blocks, hides at 55 (hysteresis). No damage required.

### Native UI Overhaul
- **Native Journal Page** — unified Bounty Board, Bestiary, and Achievements with 3 tabs, paginated mob cards, kill milestones, discovery badges.
- **Custom Trade UI** — item icons, affordability coloring, stock display, purchase notifications for all 3 merchants.
- **Native Config UI** — 8 tabs (Difficulty, Scaling, Weapons, Armor, Crafting, Misc, Integration, Addons), dark theme, global search, recipe editor.

### WaveArena Framework
Warden Trials + Void Gauntlet run on a generic data-driven wave engine (extractable). Configurable waves, mob pools, scaling, rewards, zone particles, 50-block leash, instance blacklist. Public `WaveArenaAPI`. See [Developer API](endgame-qol/api).

### Other
- **Warden Trials blocked in instances** — can no longer be started inside dungeon instances.
- **Spear pickup after throw** — thrown spears drop as pickable items (100% return).
- **Endgame Memories** — custom "Endgame" category in Bench Memories with 19 NPCs.
- **Item ID Migration** — auto-converts stale item IDs on connect / chunk load.
- **Developer API** — BossBar + WaveArena frameworks with zero plugin imports. Cross-mod `BossBarFocus` coordinator keeps one bar per player.
- **Snake Arcade rework** — native WASD controls, speed indicator, eat flash, domino death animation.

## Balance

- **Prisma Daggers rework** — Signature **Razor Storm** (100 SE, 3 AOE bursts = 240 dmg). Ability3 **Prisma Dash** (60 Mana, 10-block lunge, 80 dmg).
- **Prisma Sword rework** — Signature **Prismatic Judgment** (100 SE, 10-block AOE, 250 dmg + knockup + slow). Ability3 **Prismatic Beam** (80 Mana, 20-block projectile + AOE).
- **Frostbite Blade** — Signature **Blizzard Stance** (100 SE, 3 pulses = 240 Ice, final freezes). Ability3 **Ice Field** (50 Mana, 3 slow pulses).
- **Boss XP cap** — raised from 100k to 1M for high-level integrations.
- **Accessory tooltips** — `Accessory — store in trinket pouch` marker across all 6 accessories, 5 locales.

## Removed

- **Gauntlet system** — infinite-wave arena removed. Replaced by the Void Gauntlet.

## Config Changes

- **Native Config UI** — rewritten in native `.ui`, 8 tabs.
- **Unified commands** — all under `/eg`: config, admin, pet, journal, status, lang.
- **LuckPerms compatibility** — player commands use default-allow permissions.
- **Integration auto-detection** — RPG Leveling, Endless Leveling, OrbisGuard auto-enable on presence.

## Bug Fixes

- Frostwalker no longer replaces solid blocks.
- OrbisGuard dungeon chest/door/light access flags added.
- **Trinket Pouch only worked in hotbar** — now scans full inventory.
- **RPGLeveling config override** — no longer overwrites RPGLeveling's config on startup.
- AccessoryAttackSystem crash — ref validity check on burn apply.
- Prisma 3×3 drops lost — surrounding blocks now drop as world items.
- Boss kill sound only plays for the killer.

## Technical

- **CAE (Combat Action Evaluator)** — Void Golem + Frost Dragon use vanilla CAE for attack selection. 12 new Root_NPC wrappers, 2 CAE assets, `Template_Endgame_Dragon_Frost` abstract template.
- **Unified boss manager framework** — ~700 duplicate lines removed. All bosses flow through `GenericBossManager`. Plugin-agnostic `endgame.bossmanager.*` package seeded.
- **Status effects consolidation** — 5 custom effects replaced with vanilla (`Burn`, `Root`, `Freeze`, `Slow`, `Poison_T1`). New `Endgame_Stagger` on heavy AOE boss attacks.
- **NPC asset reorganization** — semantic directories (Bosses/, Endgame_Mobs/, Frozen_Dungeon/, Swamp_Dungeon/, Void_Realm/).
- **Full HyUI to native .ui migration** — all pages. HyUI only for boss bars.
- **Combo meter** — migrated to native CustomUIHud.
- **SimpleClaims compatibility** — Prisma 3×3 and portal spawning respect claims.
- **Dependency updates** — EndlessLeveling 7.7.4.
