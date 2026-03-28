package endgame.plugin.systems.boss;

import endgame.plugin.events.domain.BossKillHelper;
import endgame.plugin.managers.boss.EnrageTracker;
import endgame.plugin.managers.boss.GolemVoidBossManager;

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
 * Death system for the Golem Void boss.
 * Publishes a BossKillEvent via the GameEventBus for decoupled handling.
 */
public class GolemVoidDeathSystem extends DeathSystems.OnDeathSystem {

    private final EndgameQoL plugin;
    private final GolemVoidBossManager bossManager;
    private final EnrageTracker enrageTracker;

    public GolemVoidDeathSystem(EndgameQoL plugin, GolemVoidBossManager bossManager, EnrageTracker enrageTracker) {
        this.plugin = plugin;
        this.bossManager = bossManager;
        this.enrageTracker = enrageTracker;
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
            GolemVoidBossManager.GolemVoidState state = bossManager.getBossState(ref);
            if (state == null) return;

            plugin.getLogger().atFine().log("[GolemVoidBoss] Boss death detected: %s", state.npcTypeId);

            bossManager.unregisterBoss(ref);
            enrageTracker.removeBoss(ref);

            BossKillHelper.publishBossKill(plugin, store, component,
                    state.npcTypeId, "Golem Void", state.spawnTimestamp);

            plugin.getLogger().atFine().log("[GolemVoidBoss] Boss cleanup complete — bars hidden, boss unregistered");
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[GolemVoidBoss] Error handling boss death: %s", e.getMessage());
        }
    }
}
