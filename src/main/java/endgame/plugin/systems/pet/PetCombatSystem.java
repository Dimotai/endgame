package endgame.plugin.systems.pet;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.managers.PetManager;
import endgame.plugin.utils.EntityUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * INSPECT group damage system that routes the player's attack target to their pet.
 * When a player deals damage to a mob, their pet's CombatTarget is set to that mob.
 * The pet's NPC AI (CAE) then handles chasing and attacking.
 *
 * Event-driven — O(1) per damage event, no polling. Scales to 60+ players.
 */
public class PetCombatSystem extends DamageEventSystem {

    @Nonnull
    private static final Query<EntityStore> QUERY = Query.and(NPCEntity.getComponentType());

    private final EndgameQoL plugin;
    private final PetManager petManager;

    public PetCombatSystem(@Nonnull EndgameQoL plugin, @Nonnull PetManager petManager) {
        this.plugin = plugin;
        this.petManager = petManager;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null; // Default = INSPECT group (after damage applied)
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull Damage damage) {
        if (!plugin.getConfig().get().pets().isEnabled()) return;

        // Get the attacker — must be a player
        if (!(damage.getSource() instanceof Damage.EntitySource es)) return;
        Ref<EntityStore> attackerRef = es.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        Player player = attackerRef.getStore().getComponent(attackerRef, Player.getComponentType());
        if (player == null) return;

        UUID playerUuid = EntityUtils.getUuid(attackerRef.getStore(), attackerRef);
        if (playerUuid == null) return;

        // Check if this player has a pet
        Ref<EntityStore> petRef = petManager.getCachedPetRef(playerUuid);
        if (petRef == null || !petRef.isValid()) return;

        // Get the target (mob being attacked)
        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) return;
        if (targetRef.equals(petRef)) return; // Don't target own pet

        // Set the pet's combat target
        NPCEntity petNpc = store.getComponent(petRef, NPCEntity.getComponentType());
        if (petNpc != null && petNpc.getRole() != null) {
            petNpc.getRole().getMarkedEntitySupport().setMarkedEntity("CombatTarget", targetRef);
        }
    }
}
