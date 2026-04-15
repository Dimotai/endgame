package endgame.plugin.systems.boss;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.managers.boss.GenericBossManager;
import endgame.plugin.utils.BossType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-registers bosses into their managers based on spawn/presence, instead of
 * waiting for the first damage event.
 *
 * <p>Implemented as an {@link EntityTickingSystem} over all NPCs (not a
 * {@link com.hypixel.hytale.component.system.RefChangeSystem}) so it also catches
 * NPCs that existed BEFORE the plugin loaded (e.g. spawn-marker-placed bosses).
 *
 * <p>Per-entity rate-limited to 5s to keep the check cheap.
 */
public class BossAutoRegisterSystem extends EntityTickingSystem<EntityStore> {

    private static final long CHECK_INTERVAL_MS = 5000;

    private static final Query<EntityStore> QUERY = Query.and(NPCEntity.getComponentType());

    private final EndgameQoL plugin;
    private final GenericBossManager genericBossManager;

    /** Per-entity last-check timestamp — rate-limits the registration attempts. */
    private final Map<Ref<EntityStore>, Long> lastCheck = new ConcurrentHashMap<>();

    public BossAutoRegisterSystem(EndgameQoL plugin,
                                  GenericBossManager genericBossManager) {
        this.plugin = plugin;
        this.genericBossManager = genericBossManager;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
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
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;

        // Rate-limit per-entity
        long now = System.currentTimeMillis();
        Long last = lastCheck.get(ref);
        if (last != null && now - last < CHECK_INTERVAL_MS) return;
        lastCheck.put(ref, now);

        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null) return;

        String typeId = npc.getNPCTypeId();
        if (typeId == null) return;

        BossType bossType = BossType.fromTypeId(typeId);
        if (bossType == null) return;   // not a tracked boss/elite

        try {
            if (genericBossManager.getBossState(ref) == null) {
                genericBossManager.registerBoss(ref, typeId, store);
                plugin.getLogger().atInfo().log("[BossAutoRegister] Registered: %s", typeId);
            }
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[BossAutoRegister] Registration failed for %s: %s",
                    typeId, e.getMessage());
        }

        // Periodic cleanup — if a ref is dead, drop its entry
        if (lastCheck.size() > 100) {
            lastCheck.keySet().removeIf(r -> r == null || !r.isValid());
        }
    }
}
