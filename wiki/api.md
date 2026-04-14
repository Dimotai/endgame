---
title: Developer API
description: Public APIs for third-party mod integration with EndgameQoL — BossBar framework + WaveArena framework.
order: 99
published: true
---

# Developer API

For plugin developers who want to integrate with EndgameQoL or reuse its systems in their own mods.

EndgameQoL exposes two extractable frameworks under top-level packages (`endgame.bossbar.*`, `endgame.wavearena.*`) with **zero imports from the plugin-specific code**. These can be consumed as-is from any other Hytale mod that declares EndgameQoL as a soft-dependency, and are designed to be extracted as standalone libraries in the future.

---

## BossBar Framework

Package: `endgame.bossbar`

A data-driven HyUI boss health bar renderer. Consumers declare a visual theme per NPC type ID and pass a runtime state snapshot — the framework produces the HTML.

### Public classes

| Class | Type | Purpose |
|---|---|---|
| `BossBarTheme` | Immutable + Builder | Per-boss visual config (name, color, phases) |
| `BossBarPhase` | `record` | One phase: number, name, text color, HP threshold |
| `BossBarState` | `record` | Runtime snapshot: HP%, current/max HP, phase, invuln/enrage flags |
| `BossBarRenderer` | Static | Pure HTML renderer + public element IDs for refresh handlers |
| `BossBarRegistry` | Singleton | Register / look up themes by NPC type ID |

### Registering a theme

Call once at plugin startup:

```java
import endgame.bossbar.BossBarRegistry;
import endgame.bossbar.BossBarTheme;

// Multi-phase boss
BossBarRegistry.register(BossBarTheme.builder("My_Custom_Boss")
        .displayName("SHADOW LORD")
        .nameColor("#ff44aa")
        .barColor("#cc2277")
        .phase(1, "Awakening",   "#ffc0dd", 1.00f)   // 100% — 66%
        .phase(2, "Corrupted",   "#ffb060", 0.66f)   // 66% — 33%
        .phase(3, "Unleashed",   "#ff4466", 0.33f)   // 33% — 0%
        .build());

// Single-phase elite
BossBarRegistry.register(BossBarTheme.builder("My_Custom_Elite")
        .displayName("MINI BOSS")
        .nameColor("#ffaa44")
        .barColor("#cc7722")
        .elitePhase("Elite")
        .build());
```

Phases are sorted by threshold descending at build time. A theme with a single phase renders as an elite-style bar (no phase markers).

### Rendering

```java
import endgame.bossbar.BossBarRegistry;
import endgame.bossbar.BossBarState;

BossBarState state = new BossBarState(
        2,           // current phase (1-indexed)
        0.42f,       // HP percent (0.0 — 1.0)
        1260,        // current HP (0 = hide numeric)
        3000,        // max HP (0 = hide numeric)
        false,       // invulnerable?
        false        // enraged?
);

Optional<String> html = BossBarRegistry.render("My_Custom_Boss", state);
html.ifPresent(h -> HudBuilder.hudForPlayer(playerRef).fromHtml(h).show());
```

### Lifecycle & cleanup

The registry is a static singleton. Its contents persist for the lifetime of the JVM unless explicitly cleared. Follow these rules:

| Action | When to call | Who |
|---|---|---|
| `BossBarRegistry.register(theme)` | Once per theme during plugin startup | Theme owner |
| `BossBarRegistry.unregister(npcTypeId)` | During YOUR plugin's shutdown, for each theme YOU registered | Theme owner |
| `BossBarRegistry.clear()` | Wipes **all** registered themes — host plugin only | EndgameQoL itself |

**If you're a third-party mod** registering custom themes:
- Register in your plugin's `setup()` / `onEnable()`
- Unregister in your plugin's `shutdown()` / `onDisable()` — iterate your own keys and call `unregister()`
- **Never call `clear()`** — that removes other mods' themes too

**Re-registration is safe:** `register()` replaces existing entries with the same NPC type ID, so registering twice with the same key on reload is a no-op.

**On EndgameQoL shutdown:** the registry is fully cleared (all themes removed). Third-party mods that registered themes AFTER EndgameQoL started will lose their entries — they should re-register when EndgameQoL reloads (listen for plugin-load events, or re-run your registration logic).

**Per-player HUDs:** the registry owns themes, not live HUDs. Showing a bar to a player creates a `HyUIHud` — cleanup for those lives in the manager that created them. If you integrate by calling `BossBarRegistry.render(...)` directly, you're responsible for clearing the HUDs you show (use `HyUIHud.remove()` or `hideBossBarForHolder()`).

### Incremental updates

The renderer exposes stable element IDs so HyUI `onRefresh` handlers can update bars without rebuilding the HTML. Query each element via `h.getById(id, ...)`:

```java
BossBarRenderer.ID_BOSS_NAME       // "boss-name"
BossBarRenderer.ID_PHASE_TEXT      // "phase-text"
BossBarRenderer.ID_HP_BAR_BG       // "hp-bar-bg"
BossBarRenderer.ID_HP_BAR_FILL     // "hp-bar-fill"
BossBarRenderer.ID_HP_BAR_HL       // "hp-bar-highlight"
BossBarRenderer.ID_HP_NUMERIC      // "hp-numeric"
BossBarRenderer.ID_STATUS_TEXT     // "status-text"

BossBarRenderer.BAR_WIDTH          // 500
BossBarRenderer.BAR_HEIGHT         // 12
BossBarRenderer.HIGHLIGHT_HEIGHT   // 4
```

### Integration guide — bars for your own mobs

The `BossBarRegistry` is a **pure rendering** API. It produces HTML from a theme + state, nothing more. Actually *showing* a bar to a player, tracking damage, and hiding it on death are the consumer's responsibility.

Here's the minimal pattern:

**1. Register your theme at startup**

```java
BossBarRegistry.register(BossBarTheme.builder("My_Shadow_Reaver")
        .displayName("SHADOW REAVER")
        .nameColor("#cc44cc")
        .barColor("#aa33aa")
        .phase(1, "Hunting", "#e0c0ff", 1.00f)
        .phase(2, "Fury",    "#ff4466", 0.50f)
        .build());
```

**2. Damage tracking — `DamageEventSystem` in INSPECT group**

Runs after damage is applied; you read the fresh HP:

```java
public class MyMobDamageSystem extends DamageEventSystem {
    @Override public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }
    @Override public Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType());
    }
    @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk,
                                 Store<EntityStore> store,
                                 CommandBuffer<EntityStore> cb, Damage damage) {
        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null || !"My_Shadow_Reaver".equals(npc.getNPCTypeId())) return;

        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        EntityStatMap stats = store.getComponent(targetRef, EntityStatMap.getComponentType());
        if (stats == null) return;

        float curHp = stats.get(DefaultEntityStatTypes.getHealth()).get();
        float maxHp = stats.get(DefaultEntityStatTypes.getHealth()).getMax();
        float hpPct = maxHp > 0 ? curHp / maxHp : 0f;

        // Show bar to the attacker
        Ref<EntityStore> attackerRef = (damage.getSource() instanceof Damage.EntitySource es)
                ? es.getRef() : null;
        if (attackerRef != null && attackerRef.isValid()) {
            PlayerRef playerRef = attackerRef.getStore()
                    .getComponent(attackerRef, PlayerRef.getComponentType());
            if (playerRef != null) {
                int phase = hpPct > 0.5f ? 1 : 2;
                BossBarState state = new BossBarState(
                        phase, hpPct, Math.round(curHp), Math.round(maxHp), false, false);
                myBarController.show(playerRef, targetRef, state);  // your own HUD manager
            }
        }
    }
}
```

**3. Show / refresh HUD — your own manager**

Build the HTML from the registry, show via `HudBuilder`, update incrementally on refresh:

```java
public void show(PlayerRef playerRef, Ref<EntityStore> npcRef, BossBarState state) {
    String html = BossBarRegistry.render("My_Shadow_Reaver", state).orElse(null);
    if (html == null) return;

    UUID playerUuid = playerRef.getUuid();
    HudKey key = new HudKey(playerUuid, npcRef);
    if (activeHuds.containsKey(key)) return;  // already shown

    HyUIHud hud = HudBuilder.hudForPlayer(playerRef)
            .fromHtml(html)
            .withRefreshRate(200)
            .onRefresh(h -> {
                // Re-read HP and update the fill / numeric / phase text
                // Use BossBarRenderer.ID_HP_BAR_FILL, ID_HP_NUMERIC, etc.
                // See the "Incremental updates" section above.
            })
            .show();
    activeHuds.put(key, hud);
}
```

**4. Cleanup — `DeathSystem`**

When the mob dies, hide all HUDs tied to that ref:

```java
public class MyMobDeathSystem extends DeathSystems.OnDeathSystem {
    @Override protected void onComponentAdded(...) {
        NPCEntity npc = ...;
        if (!"My_Shadow_Reaver".equals(npc.getNPCTypeId())) return;
        myBarController.hideAllForBoss(npcRef);
    }
}
```

**5. Unregister on shutdown**

```java
@Override public void shutdown() {
    BossBarRegistry.unregister("My_Shadow_Reaver");
    myBarController.clearAll();
}
```

### Multi-boss display — `BossBarFocus`

Class: `endgame.bossbar.BossBarFocus`

The renderer positions all bars at `anchor-top: 24`, so multiple bars for the same player would overlap. To avoid this, the framework ships a **shared cross-mod focus registry** that enforces **one bar per player at a time** — last-damaged wins.

**How EndgameQoL uses it:**
- When a manager shows a bar, it calls `BossBarFocus.acquire(playerUuid, bossRef)`.
- On each refresh tick, the bar checks `BossBarFocus.isFocused(playerUuid, bossRef)` — if `false`, it self-hides (another boss/mod took focus).
- On boss death / unregister, `BossBarFocus.releaseBoss(bossRef)` clears focus for any player focused on it.

**How your mod plugs in:** follow the exact same pattern. Both EndgameQoL bars and your bars coexist — whoever calls `acquire` most recently wins; the other self-hides.

```java
// When showing a bar
BossBarFocus.acquire(playerUuid, myMobRef);

// In your refresh loop (e.g. inside HudBuilder.onRefresh)
if (!BossBarFocus.isFocused(playerUuid, myMobRef)) {
    // Another boss stole focus — hide this HUD
    return;
}

// On mob death
BossBarFocus.releaseBoss(myMobRef);

// On player disconnect (optional — focus auto-clears on next boss hit)
BossBarFocus.releasePlayer(playerUuid);
```

**Public API:**

| Method | Purpose |
|---|---|
| `acquire(UUID, Object)` | Claim focus for this player; returns the previous focus (if any) |
| `isFocused(UUID, Object)` | Is this (player, boss) still the current focus target? |
| `current(UUID)` | Get the current `FocusRecord` for a player |
| `release(UUID, Object)` | Release focus only if this boss is still the current focus (safe) |
| `releasePlayer(UUID)` | Clear focus for a player (e.g. disconnect) |
| `releaseBoss(Object)` | Clear focus from ALL players focused on this boss (e.g. boss death) |

The `bossKey` is opaque — use whatever object has stable `equals`/`hashCode`. `Ref<EntityStore>` works out of the box.

---

## WaveArena Framework

Package: `endgame.wavearena`

A generic wave-based arena engine. Supports fixed wave compositions (Warden Trials pattern) and randomly generated waves from a weighted mob pool (temporal portal pattern). Callbacks let consumers inject XP, rewards, chat messages, and custom mob leveling.

### Public classes

| Class | Type | Purpose |
|---|---|---|
| `WaveArenaAPI` | Static facade | Entry point — start/fail arenas, register configs + callbacks |
| `WaveArenaConfig` | Immutable + Builder | Arena config (waves, timers, rewards, spawn radius, blacklist) |
| `WaveDef` | `record` | One wave: list of mob entries |
| `WaveArenaCallbacks` | `interface` | Lifecycle hooks: `onWaveStart`, `onWaveClear`, `onArenaCompleted`, `onArenaFailed` |
| `MobLevelProvider` | `@FunctionalInterface` | Optional — assigns a level to spawned mobs (for cross-mod integration with level systems) |

### Registering a custom arena

```java
import endgame.wavearena.*;

WaveArenaConfig cfg = WaveArenaConfig.builder("my_custom_arena")
        .displayName("Shadow Trial")
        .displayColor("#cc44cc")
        .waveCount(5)
        .timeLimitSeconds(300)
        .spawnRadius(8f)
        .intervalSeconds(10)
        .countdownSeconds(3)
        .rewardDropTable("My_Drop_Reward_Shadow")
        .xpReward(200)
        .xpSource("SHADOW_TRIAL")
        .instanceBlacklist(List.of("instance-"))   // block inside dungeons
        .blockedMessage("You cannot start a Shadow Trial inside a dungeon.")
        .zoneParticleId("My_Arena_Zone")           // optional — visual boundary
        .zoneParticleScale(16f)
        .waves(List.of(
            new WaveDef(List.of(
                new WaveDef.MobEntry("Goblin_Scrapper", 3),
                new WaveDef.MobEntry("Skeleton_Archer", 2)
            )),
            // ... more waves
        ))
        .build();

WaveArenaAPI.getEngine().registerConfig(cfg);
```

### Or: generated waves from a mob pool

```java
WaveArenaConfig.builder("endless_arena")
        .displayName("Endless Arena")
        .waveCount(10)
        .baseCount(4)                      // base mob count for wave 1
        .countScaling(1.2f)                // multiplier per wave
        .bossEveryN(5)                     // elite mob every 5 waves
        .mobPool(List.of(
            new WaveArenaConfig.PoolEntry("Goblin_Scrapper", 100, 1, false),
            new WaveArenaConfig.PoolEntry("Outlander_Marauder", 50, 5, false),
            new WaveArenaConfig.PoolEntry("Alpha_Rex", 10, 9, true)  // boss
        ))
        .build();
```

### Starting an arena

```java
boolean started = WaveArenaAPI.startArena(
        playerUuid, playerRef, arenaCenterPosition, "my_custom_arena", world);

if (!started) {
    // failed — player already in arena, arena disabled, or instance blacklist hit
}
```

### Lifecycle callbacks

Register once at startup to receive events for ALL arenas:

```java
WaveArenaAPI.registerCallbacks(new WaveArenaCallbacks() {
    @Override
    public void onWaveStart(UUID playerUuid, String arenaId, int waveNumber) {
        playerRef.sendMessage(Message.raw("Wave " + waveNumber + "!"));
    }

    @Override
    public void onWaveClear(UUID playerUuid, String arenaId, int waveNumber) {
        myXpSystem.awardXp(playerUuid, 50);
    }

    @Override
    public void onArenaCompleted(UUID playerUuid, String arenaId, long durationMs) {
        // hand out bonus rewards
    }

    @Override
    public void onArenaFailed(UUID playerUuid, String arenaId, FailReason reason) {
        // cleanup, refund items, etc.
    }
});
```

### Lifecycle & cleanup

The `WaveArenaEngine` instance is created fresh on each EndgameQoL reload — its state (sessions, configs, callbacks) is garbage-collected with the old instance. No manual cleanup is required for registered configs or callbacks.

However, if you register callbacks or arena configs while the engine is alive, they are tied to that specific engine instance. On reload, you must re-register them. Pattern:

```java
// Re-register on plugin startup or EndgameQoL availability check
if (WaveArenaAPI.isAvailable()) {
    WaveArenaAPI.registerCallbacks(myCallbacks);
    WaveArenaAPI.getEngine().registerConfig(myArena);
}
```

`WaveArenaAPI.failArena(playerUuid)` is the correct way to abort an ongoing session from your code (e.g., on player disconnect). It fires `onArenaFailed` with `FailReason.DISCONNECT` to all registered callbacks.

### Mob leveling provider (optional)

If you integrate with a leveling mod (RPG Leveling, Endless Leveling, etc.) and want spawned mobs to scale to the player's level:

```java
WaveArenaAPI.setMobLevelProvider((playerUuid, mobNpcRef, mobTypeId) -> {
    return myLevelingMod.getPlayerLevel(playerUuid);
});
```

---

## Soft Dependency Pattern

Your mod should gracefully handle the case where EndgameQoL isn't installed. Standard pattern:

### Manifest

```json
// manifest.json
{
  "OptionalDependencies": {
    "Lewai:EndgameQoL": "*"
  }
}
```

### Runtime guard

```java
public class EndgameBridge {
    private static final boolean AVAILABLE = checkAvailable();

    private static boolean checkAvailable() {
        try {
            Class.forName("endgame.bossbar.BossBarRegistry");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static void registerMyTheme() {
        if (!AVAILABLE) return;
        // Safe to call EndgameQoL classes here
        endgame.bossbar.BossBarRegistry.register(...);
    }
}
```

---

## API Stability

| Framework | Status | Since |
|---|---|---|
| BossBar | Stable | v5.0.0 |
| WaveArena | Stable | v5.0.0 |

Breaking changes only at major version bumps. Non-breaking additions (new methods, new builder fields) may happen in minor versions.

## Future extraction

These two packages are designed to be extracted as standalone Gradle modules. Consumers who want the frameworks without the rest of EndgameQoL can watch the [GitHub repo](https://github.com/lewai1/EndgameQoL) — the grant-driven split is on the roadmap.
