---
title: Void Realm
description: Void Realm dimension — floating island arena, Golem Void boss, Void Gauntlet wave trial, and boss combat music.
order: 6
published: true
---

# Void Realm

A new dimension introduced in v5.0.0. A floating island arena in the void, home to the **Golem Void** boss.

Replaces the old Shard of the Void portal system. The portal key was renamed from **Shard of the Void** to **Void Realm Key** (auto-migrated via the item ID migration system — old stacks convert on connect and chunk load).

## Overview

| Property | Value |
| :--- | :--- |
| Key | **Void Realm Key** |
| Portal | Endgame Gateway (Void variant) |
| Boss | **Golem Void** — 3,500 HP |
| Mini-wave | **Void Gauntlet** (3 waves, no leash) |
| Ambience | Boss combat music (custom) |

## Entry

1. Craft or acquire a **Void Realm Key**.
2. Right-click an **Endgame Gateway** block with the key. The gateway swaps to its Void-themed variant and the particle VFX activates.
3. Walk through the portal to enter the Void Realm instance.

See [Portals & Gateways](endgame-qol/portals-gateways) for the full key/portal flow.

## Golem Void

Full attack set and phase notes: see [Bosses & Elites — Golem Void](endgame-qol/bosses-elites).

**v5.0.0 highlights:**

- Knockback-immune (KnockbackScale 0.0)
- MaxSpeed 14 (up from 8)
- Signature **Jump Slam** attack unlocks at <66% HP, ramps at <33%
- Void-safe fallback — if players try to bait the boss over the void, it teleports back to its spawn position
- No auto-spawn marker — introduction via **Void Gauntlet** planned

## Void Gauntlet

A wave-survival encounter inside the Void Realm (built on the [WaveArena framework](endgame-qol/api)).

| Property | Value |
| :--- | :--- |
| Waves | 3 |
| Time Limit | 360 seconds |
| Leash | **None** — unlike Warden Trials, leaving the arena zone does not fail the trial |
| Purpose | Clear adds before the Golem appears (planned boss-intro mechanic) |

No leash fail makes the Void Gauntlet distinct from Warden Trials — you can reposition freely across the floating island.

## Boss Combat Music

A custom combat track (`Common/Music/Endgame/Endgame_Golem_Void_Fight.ogg`) plays while inside the Void Realm and within 50 blocks of the Golem.

The music is conditioned on the `Env_Endgame_Void_Realm` environment, so it only plays inside this dimension. Leaving the realm (or boss death, or 55-block egress) stops playback automatically.

## Portal Theme

The Endgame Gateway, when activated with a Void Realm Key, swaps to `Endgame_Gateway_Void` with thematic particle VFX. The return portal inside the instance sends players back to the original gateway location — which is preserved as the themed variant until the instance closes. On close, the origin block reverts to the neutral base gateway (see [Portals & Gateways](endgame-qol/portals-gateways)).

> [!NOTE]
> **Portal replacement now skips instance worlds** — the return portal inside the Void Realm, Frozen Dungeon, and Swamp Dungeon stays usable and is no longer overwritten by the themed-swap logic.
