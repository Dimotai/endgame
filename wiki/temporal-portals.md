---
title: Temporal Portals
description: Random spawning portals leading to temporary dungeon instances — Eldergrove Hollow, Oakwood Refuge, Canopy Shrine.
order: 5.6
published: true
---

# Temporal Portals

Random particle-only portals that spawn near players and lead to temporary dungeon instances. Introduced in v5.0.0.

## Overview

- Spawn every **5–10 minutes** (configurable)
- Range **80–300 blocks** from the player
- **Particle-only** — no block placement, no permanent world change
- Walk in to enter a temporary dungeon instance
- Respect **OrbisGuard** and **SimpleClaims** protected zones (portals skip claimed land)

## Lifetime Status

Portals decay through four visible states before collapsing:

| Status | Meaning |
| :--- | :--- |
| **STABLE** | Fresh portal, full lifetime remaining |
| **DESTABILIZING** | Halfway through lifetime |
| **CRITICAL** | Final stretch before collapse |
| **COLLAPSING** | Grace period (~30s) — portal still usable but imminent |

## Warnings

| When | Event |
| :--- | :--- |
| 5 minutes before expiration | Chat warning |
| 1 minute before expiration | Chat warning + sound |
| Portal collapse | 30-second grace period begins |

## Entering

Walk into the particle column. You're teleported into a temporary instance. A **return portal** is placed inside — walk back through it to return to the overworld location.

If the portal collapses while you're inside, the instance remains valid until you leave or the instance time limit hits. The return portal will still work.

## The 3 Temporal Instances

All three are data-driven — you can add, remove, or retune them via config.

### Eldergrove Hollow

Ancient forest ruins under a fading canopy. Mob roster draws from nature-aligned temporal dwellers. Balanced for mid-tier combat.

### Oakwood Refuge

Dense oak grove with elevated platforms. Mixed mob pool, encourages vertical play.

### Canopy Shrine

Sky-temple built across treetops. High-mobility arena.

## Configuration

Per-dungeon settings live under the `TemporalPortals` config key:

| Key | Description |
| :--- | :--- |
| `Enabled` | Master toggle for temporal portals |
| `<dungeon>.Enabled` | Per-dungeon enable flag |
| `<dungeon>.PortalDuration` | How long the portal lives before collapsing |
| `<dungeon>.InstanceTimeLimit` | How long the instance stays open once entered |
| `<dungeon>.RespawnInsideInstance` | Whether deaths respawn inside or kick to overworld |
| `SpawnIntervalMin` / `SpawnIntervalMax` | Cooldown window between portal spawns (default 5–10 min) |
| `SpawnRadiusMin` / `SpawnRadiusMax` | Distance from player (default 80–300 blocks) |

## Warden Trials Compatibility

Warden Challenge items are **blocked** inside temporal instances (and all dungeon instances). Using one inside prints a chat warning.

## Framework

Temporal dungeon waves and any future gauntlet-style encounters use the shared [WaveArena framework](endgame-qol/api). The API is public — third-party mods can build custom wave arenas using the same engine.
