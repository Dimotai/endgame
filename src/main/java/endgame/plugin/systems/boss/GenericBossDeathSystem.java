package endgame.plugin.systems.boss;

import endgame.plugin.events.domain.BossKillHelper;
import endgame.plugin.managers.boss.EnrageTracker;
import endgame.plugin.managers.boss.GenericBossManager;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;

/**
 * Death system for bosses handled by GenericBossManager (Frost Dragon, Hedera).
 * Publishes a BossKillEvent via the GameEventBus for decoupled handling.
 */
public class GenericBossDeathSystem extends DeathSystems.OnDeathSystem {

    private final EndgameQoL plugin;
    private final GenericBossManager bossManager;
    private final EnrageTracker enrageTracker;
    private final GenericBossDamageSystem damageSystem;

    public GenericBossDeathSystem(EndgameQoL plugin, GenericBossManager bossManager,
                                  EnrageTracker enrageTracker, GenericBossDamageSystem damageSystem) {
        this.plugin = plugin;
        this.bossManager = bossManager;
        this.enrageTracker = enrageTracker;
        this.damageSystem = damageSystem;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType());
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component,
                                  @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            GenericBossManager.GenericBossState state = bossManager.getBossState(ref);
            if (state == null) return;

            plugin.getLogger().atFine().log("[GenericBoss] Boss death detected: %s (%s)",
                    state.config.displayName, state.npcTypeId);

            bossManager.unregisterBoss(ref);
            enrageTracker.removeBoss(ref);
            damageSystem.clearDamageTracking(ref);

            BossKillHelper.publishBossKill(plugin, store, component,
                    state.npcTypeId, state.config.displayName, state.spawnTimestamp);

            plugin.getLogger().atFine().log("[GenericBoss] Boss cleanup complete — bars hidden, boss unregistered");
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[GenericBoss] Error handling boss death: %s", e.getMessage());
        }
    }
}
