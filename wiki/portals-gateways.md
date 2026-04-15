---
title: Portals & Gateways
description: Endgame Gateway block, themed variants, dungeon keys, and the portal theming system.
order: 5.7
published: true
---

# Portals & Gateways

The **Endgame Gateway** is the single portal block that opens all three Endgame dungeons. Introduced in v5.0.0 as a replacement for the fragmented per-dungeon portal system.

## Endgame Gateway

Epic-tier craftable portal block. Reuses the vanilla Portal_Device model / texture / icon.

| Property | Value |
| :--- | :--- |
| Rarity | Epic |
| Craft Bench | Endgame Bench Tier 1 |
| Recipe | 30 Cobble + 10 Mithril + 20 Void + 10 Life Essence |
| Accepts | Void Realm Key, Frozen Dungeon Key, Swamp Dungeon Key |

## The 3 Dungeon Keys

| Key | Unlocks | Portal Variant |
| :--- | :--- | :--- |
| **Void Realm Key** | [Void Realm](endgame-qol/void-realm) | `Endgame_Gateway_Void` |
| **Frozen Dungeon Key** | [Frozen Dungeon](endgame-qol/frozen-dungeon) | `Endgame_Gateway_Frozen` |
| **Swamp Dungeon Key** | [Swamp Dungeon](endgame-qol/swamp-dungeon) | `Endgame_Gateway_Swamp` |

> [!NOTE]
> **"Shard of the Void" is now "Void Realm Key"** — renamed in v5.0.0 across all 5 locales (en-US, pt-BR, ru-RU, fr-FR, es-ES). Old item stacks are auto-migrated on connect and chunk load (`Endgame_Portal_Golem_Void` → `Endgame_Portal_Void_Realm`).

## How It Works

1. **Place** an Endgame Gateway block.
2. **Right-click** the gateway while holding one of the 3 keys. The block swaps to its themed variant (Void / Frozen / Swamp) and the active particle VFX turns on.
3. **Walk into** the portal to enter the instance.
4. The **return portal** inside the instance sends you back to the original gateway block.
5. When the **last player leaves** the instance, the gateway **reverts** to the neutral base block (on `RemoveWorldEvent`). The socket is reusable with any key.

## Theming System (Technical)

Rather than runtime state-swap (which caused `InteractionManager` counter desync crashes in earlier attempts), each theme is a dedicated **block variant**:

- `Endgame_Gateway` — neutral base
- `Endgame_Gateway_Void` — Void Realm portal
- `Endgame_Gateway_Frozen` — Frozen Dungeon portal
- `Endgame_Gateway_Swamp` — Swamp Dungeon portal

Right-clicking the neutral base with a matching key swaps the block on `UseBlockEvent.Pre` (before any interaction chain starts — safe). Particles are baked into each variant's Active state; the engine handles rotation automatically.

On instance close (`RemoveWorldEvent`), a tracker reverts the source block to the neutral base. This fixes the v5.0.0-early bug where portals stayed themed forever because `ReturnBlockType` only acts on the destination world and `OffState` only supports state-swaps within the same BlockType.

> [!NOTE]
> **Portal replacement skips instance worlds** — return portals placed inside Frozen Dungeon, Swamp Dungeon, and Void Realm are never rewritten by the themed-swap logic. Return portals stay usable.

## Breaking Themed Variants

Breaking a themed gateway variant (e.g. `Endgame_Gateway_Void`) drops the **base `Endgame_Gateway` block** — not the themed variant. Powered by the `Drop_Endgame_Gateway_Revert` drop list. You never lose the base block, and you can't stockpile themed variants.

## Vanilla Portal_Device Rejects Endgame Keys

If you right-click a vanilla Portal_Device while holding one of the 3 Endgame keys, the interaction is cancelled and a chat warning points you to the Endgame Gateway block. This prevents the vanilla device from consuming Endgame keys.

## Temporal Portals

Temporal Portals are a separate system — **particle-only**, no block placement, and don't use gateway blocks or keys. See [Temporal Portals](endgame-qol/temporal-portals).
