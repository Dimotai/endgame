package endgame.plugin.systems.boss;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.utils.BossType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Blocks NPC-to-NPC damage involving bosses (both directions):
 *  - Boss → other NPC (prevents e.g. Hedera Scream from killing allied Spirit_Root)
 *  - Other NPC → Boss (prevents skeletons / dungeon mobs from aggroing the dragon)
 * Player damage to/from bosses is untouched — only NPC vs NPC is filtered.
 * Runs in the FILTER damage group (before damage is applied).
 */
public class BossFriendlyFireFilterSystem extends AbstractBossDamageSystem {

    private static final Query<EntityStore> QUERY = Query.and(NPCEntity.getComponentType());

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull Damage damage) {
        Ref<EntityStore> sourceRef = resolveAttacker(damage);
        if (sourceRef == null) return;

        // Only filter NPC-vs-NPC interactions — leave player damage alone.
        String attackerTypeId = resolveNPCTypeId(sourceRef, store);
        if (attackerTypeId == null) return;  // attacker is a player (no NPCEntity)

        // Direction 1: boss attacking other NPC → block (friendly fire protection)
        if (BossType.fromTypeId(attackerTypeId) != null) {
            damage.setAmount(0);
            return;
        }

        // Direction 2: NPC attacking boss → block (prevent dungeon mobs from aggroing bosses)
        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) return;
        String targetTypeId = resolveNPCTypeId(targetRef, store);
        if (targetTypeId == null) return;
        if (BossType.fromTypeId(targetTypeId) != null) {
            damage.setAmount(0);
        }
    }
}
