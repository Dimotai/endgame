package endgame.plugin.systems.boss;

import endgame.plugin.managers.boss.EnrageTracker;
import endgame.plugin.managers.boss.GenericBossManager;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;

import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tick system that applies damage to players in the Golem Void danger zone.
 * Runs every tick and checks player distance to active bosses.
 */
public class DangerZoneTickSystem extends EntityTickingSystem<EntityStore> {

    private final EndgameQoL plugin;
    private final GenericBossManager genericBossManager;
    private final EnrageTracker enrageTracker;

    // Track last cleanup time to periodically clean lastDamageTime map
    private volatile long lastCleanupTime = 0;
    private static final long CLEANUP_INTERVAL_MS = 60000; // 1 minute

    // Configuration
    public static final float DANGER_ZONE_RADIUS = 8.0f;
    public static final float TICK_DAMAGE = 3.0f;
    public static final long DAMAGE_INTERVAL_MS = 1000; // 1 second between damage ticks

    // Pre-computed squared threshold (avoids Math.sqrt() in hot paths)
    private static final double DANGER_ZONE_RADIUS_SQ = DANGER_ZONE_RADIUS * DANGER_ZONE_RADIUS;

    // Track last damage time per entity (by player UUID)
    private final Map<UUID, Long> lastDamageTime = new ConcurrentHashMap<>();

    // Frame-rate guard for bossManager.tick() to prevent per-entity spam (per-world)
    private final ConcurrentHashMap<Store<EntityStore>, Long> lastBossTickTimes = new ConcurrentHashMap<>();
    private static final long BOSS_TICK_INTERVAL_MS = 200;


    // Query for players with transform and stats
    private static final Query<EntityStore> QUERY = Query.and(
            TransformComponent.getComponentType(),
            EntityStatMap.getComponentType(),
            Player.getComponentType());

    public DangerZoneTickSystem(EndgameQoL plugin,
                                GenericBossManager genericBossManager, EnrageTracker enrageTracker) {
        this.plugin = plugin;
        this.genericBossManager = genericBossManager;
        this.enrageTracker = enrageTracker;
        plugin.getLogger().atFine().log("[DangerZoneTickSystem] Initialized (radius: %.1f, damage: %.1f)",
                DANGER_ZONE_RADIUS, TICK_DAMAGE);
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Periodic cleanup to prevent memory leak
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = now;
            cleanup();
        }

        if (genericBossManager == null)
            return;

        // Cache config once per tick to avoid repeated indirection
        endgame.plugin.config.EndgameConfig config = plugin.getConfig().get();

        // Call manager tick() with per-store frame-rate guard to prevent per-entity spam
        Long lastBossTime = lastBossTickTimes.get(store);
        if (lastBossTime == null || now - lastBossTime >= BOSS_TICK_INTERVAL_MS) {
            lastBossTickTimes.put(store, now);
            genericBossManager.tick(store);
            if (enrageTracker != null) {
                enrageTracker.tick(now);
            }
        }

        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        if (playerRef == null || !playerRef.isValid())
            return;

        // Get player position
        TransformComponent playerTransform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (playerTransform == null)
            return;

        Vector3d playerPos = playerTransform.getPosition();
        if (playerPos == null)
            return;

        long damageNow = now;

        // O(1) direct component lookups — replaces O(n) Universe.getPlayers() loop
        UUID playerUuid = endgame.plugin.utils.EntityUtils.getUuid(archetypeChunk, index);
        if (playerUuid == null) return;
        PlayerRef matchingPlayerRef = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (matchingPlayerRef == null) return;

        // Danger zone damage for Golem Void (proximity boss-bar show/hide moved to BossBarProximitySystem).
        // Filter the unified boss map to just Golem Void entries.
        var activeBosses = genericBossManager.getActiveBosses();
        for (var entry : activeBosses.entrySet()) {
            Ref<EntityStore> bossRef = entry.getKey();
            GenericBossManager.GenericBossState state = entry.getValue();
            if (state.config.bossType != endgame.plugin.utils.BossType.GOLEM_VOID) continue;

            if (!bossRef.isValid()) continue;
            if (bossRef.getStore() != store) continue;

            TransformComponent bossTransform;
            try {
                bossTransform = store.getComponent(bossRef, TransformComponent.getComponentType());
            } catch (Exception e) {
                continue;
            }
            if (bossTransform == null) continue;
            Vector3d bossPos = bossTransform.getPosition();
            if (bossPos == null) continue;

            double dx = playerPos.x - bossPos.x;
            double dz = playerPos.z - bossPos.z;
            double distanceSq = dx * dx + dz * dz;

            int startPhase = config.getBossConfig(endgame.plugin.utils.BossType.GOLEM_VOID).getDangerZoneStartPhase();
            if (state.currentPhase >= startPhase && distanceSq <= DANGER_ZONE_RADIUS_SQ) {
                Long lastDamage = lastDamageTime.get(playerUuid);

                if (lastDamage == null || (damageNow - lastDamage) >= DAMAGE_INTERVAL_MS) {
                    float phaseMult = switch (state.currentPhase) {
                        case 1 -> 0.5f;
                        case 2 -> 1.0f;
                        case 3 -> 2.0f;
                        default -> 0f;
                    };
                    float damageAmount = TICK_DAMAGE * phaseMult;

                    if (damageAmount > 0) {
                        @SuppressWarnings("deprecation")
                        Damage damage = new Damage(Damage.NULL_SOURCE, DamageCause.OUT_OF_WORLD, damageAmount);
                        DamageSystems.executeDamage(playerRef, commandBuffer, damage);

                        lastDamageTime.put(playerUuid, damageNow);

                        plugin.getLogger().atFine().log(
                                "[DangerZone] Player in zone (dist: %.1f, dmg: %.1f, phase: %d, mult: %.1fx)",
                                Math.sqrt(distanceSq), damageAmount, state.currentPhase, phaseMult);
                    }
                }
                break; // only damage once per tick
            }
        }
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null; // Run in default group (synchronous) to safely access other entities
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        lastDamageTime.entrySet().removeIf(e -> (now - e.getValue()) > 60000);

        // EndgameSpawnNPC interaction stale caster cleanup
        endgame.plugin.systems.npc.EndgameSpawnNPCInteraction.cleanupStaleEntries();

        // Command rate limit cleanup
        endgame.plugin.utils.CommandRateLimit.cleanup();
    }

    /**
     * Clear all tracking state for a specific player.
     * Called on disconnect or world leave to prevent stale damage timers.
     */
    public void clearPlayerState(UUID playerUuid) {
        if (playerUuid == null) return;
        lastDamageTime.remove(playerUuid);
    }

    /**
     * Force clear all tracking state. Called on plugin shutdown or boss death.
     */
    public void forceClear() {
        lastDamageTime.clear();
        lastBossTickTimes.clear();
    }
}
