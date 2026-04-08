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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PetOwnerComponent;
import endgame.plugin.managers.PetManager;
import endgame.plugin.utils.EntityUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * INSPECT group damage system that makes pets react to combat:
 * 1. Owner attacks a mob → pet chases and attacks that mob
 * 2. Owner gets attacked by a mob/player → pet defends (chases attacker)
 * 3. Pet never targets its own owner
 *
 * Uses LockedTarget slot (same as dragon template AI) so the NPC AI
 * naturally chases and attacks the target.
 */
public class PetCombatSystem extends DamageEventSystem {

    private final EndgameQoL plugin;
    private final PetManager petManager;

    public PetCombatSystem(@Nonnull EndgameQoL plugin, @Nonnull PetManager petManager) {
        this.plugin = plugin;
        this.petManager = petManager;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null; // INSPECT group
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull Damage damage) {
        if (!plugin.getConfig().get().pets().isEnabled()) return;
        if (!(damage.getSource() instanceof Damage.EntitySource es)) return;

        Ref<EntityStore> attackerRef = es.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        Ref<EntityStore> victimRef = archetypeChunk.getReferenceTo(index);
        if (victimRef == null || !victimRef.isValid()) return;

        // Case 1: Owner attacks something → pet chases that target
        Player attackerPlayer = attackerRef.getStore().getComponent(attackerRef, Player.getComponentType());
        if (attackerPlayer != null) {
            UUID ownerUuid = EntityUtils.getUuid(attackerRef.getStore(), attackerRef);
            if (ownerUuid != null) {
                Ref<EntityStore> petRef = petManager.getCachedPetRef(ownerUuid);
                if (petRef != null && petRef.isValid() && !victimRef.equals(petRef)) {
                    setPetTarget(store, petRef, victimRef);
                }
            }
            return;
        }

        // Case 2: Owner gets attacked → pet defends (chases attacker)
        Player victimPlayer = store.getComponent(victimRef, Player.getComponentType());
        if (victimPlayer != null) {
            UUID ownerUuid = EntityUtils.getUuid(store, victimRef);
            if (ownerUuid != null) {
                Ref<EntityStore> petRef = petManager.getCachedPetRef(ownerUuid);
                if (petRef != null && petRef.isValid() && !attackerRef.equals(petRef)) {
                    setPetTarget(store, petRef, attackerRef);
                }
            }
        }
    }

    private void setPetTarget(@Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> petRef,
                               @Nonnull Ref<EntityStore> targetRef) {
        NPCEntity petNpc = store.getComponent(petRef, NPCEntity.getComponentType());
        if (petNpc != null && petNpc.getRole() != null) {
            petNpc.getRole().getMarkedEntitySupport().setMarkedEntity("LockedTarget", targetRef);
            // Land to attack — switch to Walk controller for ground combat
            petNpc.getRole().setActiveMotionController(petRef, petNpc, "Walk", store);
        }
    }
}
