package endgame.plugin.systems.pet;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
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
 * Tick system for pet NPCs. Runs every 500ms (DelayedEntitySystem).
 * - Sets pet's locked target to owner (NPC AI handles follow via Seek)
 * - Emergency teleports if pet is too far from owner
 * - Despawns pet if owner is disconnected or dead
 * - Registers pet in PetManager cache on first tick
 *
 * Queries only PetOwnerComponent archetype — O(n) where n = active pets, not all entities.
 */
public class PetFollowSystem extends DelayedEntitySystem<EntityStore> {

    private static final float INTERVAL_SEC = 0.5f;

    private final EndgameQoL plugin;
    private final PetManager petManager;

    public PetFollowSystem(@Nonnull EndgameQoL plugin, @Nonnull PetManager petManager) {
        super(INTERVAL_SEC);
        this.plugin = plugin;
        this.petManager = petManager;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(PetOwnerComponent.getComponentType(), NPCEntity.getComponentType());
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (!plugin.getConfig().get().pets().isEnabled()) return;

        Ref<EntityStore> petRef = archetypeChunk.getReferenceTo(index);
        if (petRef == null || !petRef.isValid()) return;

        PetOwnerComponent petOwner = store.getComponent(petRef, PetOwnerComponent.getComponentType());
        if (petOwner == null || petOwner.getOwnerUuid() == null) return;

        UUID ownerUuid = petOwner.getOwnerUuid();

        // Register in cache if not already
        if (petManager.getCachedPetRef(ownerUuid) == null) {
            petManager.registerPet(ownerUuid, petRef);
        }

        // Find owner
        PlayerRef ownerPlayerRef = findPlayerRef(ownerUuid);
        if (ownerPlayerRef == null) {
            // Owner disconnected — despawn pet
            commandBuffer.removeEntity(petRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
            petManager.clearPet(ownerUuid);
            return;
        }

        Ref<EntityStore> ownerRef = ownerPlayerRef.getReference();
        if (ownerRef == null || !ownerRef.isValid()) {
            commandBuffer.removeEntity(petRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
            petManager.clearPet(ownerUuid);
            return;
        }

        // Check if owner is in same world
        World ownerWorld = ownerRef.getStore().getExternalData().getWorld();
        World petWorld = store.getExternalData().getWorld();
        if (ownerWorld == null || petWorld == null || !ownerWorld.equals(petWorld)) {
            commandBuffer.removeEntity(petRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
            petManager.clearPet(ownerUuid);
            return;
        }

        // Set locked target to owner (NPC AI follows via Seek instruction)
        NPCEntity npc = store.getComponent(petRef, NPCEntity.getComponentType());
        if (npc != null && npc.getRole() != null) {
            npc.getRole().getMarkedEntitySupport().setMarkedEntity("LockedTarget", ownerRef);
        }

        // Emergency teleport if too far
        float teleportDist = plugin.getConfig().get().pets().getTeleportDistance();
        float teleportDistSq = teleportDist * teleportDist;

        TransformComponent petTransform = store.getComponent(petRef, TransformComponent.getComponentType());
        TransformComponent ownerTransform = ownerRef.getStore().getComponent(ownerRef, TransformComponent.getComponentType());
        if (petTransform != null && ownerTransform != null) {
            Vector3d petPos = petTransform.getPosition();
            Vector3d ownerPos = ownerTransform.getPosition();
            double distSq = petPos.distanceSquaredTo(ownerPos);

            if (distSq > teleportDistSq) {
                Vector3d targetPos = new Vector3d(ownerPos.x + 2, ownerPos.y + 1, ownerPos.z + 2);
                try {
                    var teleportType = com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.getComponentType();
                    var teleport = com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.createForPlayer(
                            petWorld, targetPos, new Vector3f(0, 0, 0));
                    commandBuffer.putComponent(petRef, teleportType, teleport);
                } catch (Exception ignored) {}
            }
        }
    }

    @Nullable
    private PlayerRef findPlayerRef(@Nonnull UUID uuid) {
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p == null) continue;
            UUID pUuid = EntityUtils.getUuid(p);
            if (uuid.equals(pUuid)) return p;
        }
        return null;
    }
}
