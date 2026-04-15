---
title: Pets
description: 4 boss-tier pets, D → SS progression, mount system, passive perks, and aura effects.
order: 16
published: true
---

# Pets

4 boss-tier pet companions. Unlocked by killing bosses. Scale from tier D → SS by feeding items.

**Command:** `/eg pet` — Permission: `endgameqol.pet` (default: allow)

## Unlocking a Pet

Each boss has a small chance to drop its pet egg / spirit:

| Pet | Drop Source | Drop Chance |
| :--- | :--- | :---: |
| **Dragon Frost Pet** | Dragon Frost (Frozen Dungeon) | 10–15% |
| **Dragon Fire Pet** | Dragon Fire (volcano) | 10–15% |
| **Golem Void Pet** | Golem Void (Void Realm) | 10–15% |
| **Hedera Pet** | Hedera (Swamp Dungeon) | 10–15% |

## Tier Progression

Pets advance through 6 tiers by feeding them items. Each tier scales damage, health, and visual size.

| Tier | Damage Multiplier | Unlock |
| :---: | :---: | :--- |
| D | 1.0x | Initial tier on unlock |
| C | 1.3x | **Mount unlocked** — pet can be ridden |
| B | 1.6x | — |
| A | 1.9x | — |
| S | 2.2x | **Passive perk unlocked** |
| SS | 2.5x | **Aura unlocked** + advanced perk |

Health and visual scale increase with tier. Higher tiers look noticeably bigger.

## Mount System (Tier C+)

At Tier C and above, the pet becomes mountable. Right-click to ride. Each pet's mount behavior fits its archetype (dragons fly, Golem Void grounds heavy, Hedera is a nature-themed runner).

## Perks (Tier S) and Auras (Tier SS)

- **Tier S — Passive Perk** — each pet applies a passive benefit while summoned (damage aura, resistance, regen, etc., depending on the pet).
- **Tier SS — Aura** — a visible aura FX plus a stronger / additional perk.

## Feeding

Open `/eg pet` to see the active pet's feed panel. Feed item stacks to advance tier. The UI shows the current tier badge, ability list, and remaining progress to the next tier.

## Respawn

If the pet dies, it enters a **30-second respawn cooldown**, then becomes available again. You cannot summon a new pet during the cooldown.

## UI

`/eg pet` opens a native page with:

- **Tier badge** — current tier (D / C / B / A / S / SS)
- **Ability list** — perks and aura currently active
- **Feed button** — consume items from inventory to advance tier
- **Mount indicator** — shows whether the pet can be ridden

## Cross-Mod Integration

The pet system bridges to **EndlessLeveling** for cross-mod stat access. If EndlessLeveling is installed, pet stats may integrate with the player's leveling progress.

## Configuration

Pet drop rates, tier scaling, and mount/perk toggles are configurable via `/eg config` (or the relevant `Pets` config key).
