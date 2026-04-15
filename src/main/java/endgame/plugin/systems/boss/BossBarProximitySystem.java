package endgame.plugin.systems.boss;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.managers.boss.GenericBossManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows / hides boss bars to players based on proximity to active bosses.
 * Works for ALL bosses and elites (unified via {@link GenericBossManager}).
 *
 * <p>Lifecycle (no damage needed):
 * <ul>
 *   <li>Player enters {@link #SHOW_DISTANCE} radius → bar appears + focus claimed</li>
 *   <li>Player leaves {@link #HIDE_DISTANCE} radius → bar disappears</li>
 *   <li>Re-entry shows it again — no latch</li>
 * </ul>
 */
public class BossBarProximitySystem extends EntityTickingSystem<EntityStore> {

    public static final float SHOW_DISTANCE = 50.0f;
    public static final float HIDE_DISTANCE = 55.0f;  // hysteresis

    private static final double SHOW_DISTANCE_SQ = SHOW_DISTANCE * SHOW_DISTANCE;
    private static final double HIDE_DISTANCE_SQ = HIDE_DISTANCE * HIDE_DISTANCE;

    private static final long TICK_INTERVAL_MS = 500;

    private static final Query<EntityStore> QUERY = Query.and(
            TransformComponent.getComponentType(),
            Player.getComponentType());

    private final GenericBossManager genericBossManager;

    /** Per-player tick rate-limit (prevents spam since query fires per-player per-tick). */
    private final Map<UUID, Long> lastTickByPlayer = new ConcurrentHashMap<>();

    /** Per-player per-boss HUD visibility. */
    private final Map<UUID, Map<Ref<EntityStore>, Boolean>> visibleByPlayer = new ConcurrentHashMap<>();

    public BossBarProximitySystem(GenericBossManager genericBossManager) {
        this.genericBossManager = genericBossManager;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null; // default (synchronous) group so we can safely read boss transforms
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        if (playerRef == null || !playerRef.isValid()) return;

        UUID playerUuid = endgame.plugin.utils.EntityUtils.getUuid(chunk, index);
        if (playerUuid == null) return;

        long now = System.currentTimeMillis();
        Long last = lastTickByPlayer.get(playerUuid);
        if (last != null && now - last < TICK_INTERVAL_MS) return;

        if (genericBossManager == null) {
            lastTickByPlayer.put(playerUuid, now);
            return;
        }

        var activeBosses = genericBossManager.getActiveBosses();
        if (activeBosses.isEmpty()) {
            visibleByPlayer.remove(playerUuid);
            lastTickByPlayer.put(playerUuid, now);
            return;
        }
        lastTickByPlayer.put(playerUuid, now);

        TransformComponent playerTransform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (playerTransform == null) return;
        Vector3d playerPos = playerTransform.getPosition();
        if (playerPos == null) return;

        PlayerRef matchingPlayerRef = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (matchingPlayerRef == null) return;

        Map<Ref<EntityStore>, Boolean> perBoss = visibleByPlayer.computeIfAbsent(playerUuid,
                k -> new ConcurrentHashMap<>());

        for (var entry : activeBosses.entrySet()) {
            Ref<EntityStore> bossRef = entry.getKey();
            if (!bossRef.isValid()) continue;
            if (bossRef.getStore() != store) continue;

            Vector3d bossPos = readBossPos(store, bossRef);
            if (bossPos == null) continue;

            double dsq = horizontalDistSq(playerPos, bossPos);
            Boolean cur = perBoss.get(bossRef);
            boolean showing = cur != null && cur;

            if (!showing && dsq <= SHOW_DISTANCE_SQ) {
                genericBossManager.showBossBarToPlayer(matchingPlayerRef, bossRef, store);
                endgame.bossbar.BossBarFocus.acquire(playerUuid, bossRef);
                perBoss.put(bossRef, true);
            } else if (showing && dsq > HIDE_DISTANCE_SQ) {
                genericBossManager.hideBossBarForPlayer(matchingPlayerRef);
                perBoss.put(bossRef, false);
            }
        }
        // Prune stale entries
        perBoss.keySet().removeIf(ref -> !ref.isValid() || !activeBosses.containsKey(ref));
    }

    private Vector3d readBossPos(Store<EntityStore> store, Ref<EntityStore> bossRef) {
        try {
            TransformComponent t = store.getComponent(bossRef, TransformComponent.getComponentType());
            return t == null ? null : t.getPosition();
        } catch (Exception e) {
            return null;
        }
    }

    private static double horizontalDistSq(Vector3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    public void clearPlayerState(UUID playerUuid) {
        if (playerUuid == null) return;
        visibleByPlayer.remove(playerUuid);
        lastTickByPlayer.remove(playerUuid);
    }

    public void forceClear() {
        visibleByPlayer.clear();
        lastTickByPlayer.clear();
    }
}
