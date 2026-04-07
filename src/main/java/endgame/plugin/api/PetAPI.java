package endgame.plugin.api;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.components.PetOwnerComponent;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Public API for external mods (EndlessLeveling, etc.) to query pet ownership and stats.
 * All methods are O(1) — direct component lookup on the entity ref.
 *
 * Usage from external mod:
 *   UUID owner = PetAPI.getPetOwner(npcRef);
 *   float dmg = PetAPI.getPetDamage(npcRef);
 *   int kills = PetAPI.getPetKill(npcRef);
 */
public final class PetAPI {

    private PetAPI() {}

    /**
     * Get the owner UUID of a pet NPC, or null if the entity is not a pet.
     */
    @Nullable
    public static UUID getPetOwner(@Nullable Ref<EntityStore> npcRef) {
        PetOwnerComponent comp = getComponent(npcRef);
        return comp != null ? comp.getOwnerUuid() : null;
    }

    /**
     * Get the total damage dealt by this pet since it was spawned.
     * Returns 0 if the entity is not a pet.
     */
    public static float getPetDamage(@Nullable Ref<EntityStore> npcRef) {
        PetOwnerComponent comp = getComponent(npcRef);
        return comp != null ? comp.getTotalDamageDealt() : 0f;
    }

    /**
     * Get the total kill count of this pet since it was spawned.
     * Returns 0 if the entity is not a pet.
     */
    public static int getPetKill(@Nullable Ref<EntityStore> npcRef) {
        PetOwnerComponent comp = getComponent(npcRef);
        return comp != null ? comp.getTotalKills() : 0;
    }

    @Nullable
    private static PetOwnerComponent getComponent(@Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return null;
        var type = PetOwnerComponent.getComponentType();
        if (type == null) return null;
        return ref.getStore().getComponent(ref, type);
    }
}
