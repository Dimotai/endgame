package endgame.plugin.systems.pet;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.managers.PetManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * Global tick system (every 500ms) that manages pet emergency teleport and combat target reset.
 *
 * Following is handled by NPC AI (LockedTarget set once at spawn).
 * Combat targeting is handled by PetCombatSystem (damage event).
 * This system only handles:
 * - Emergency teleport if pet is too far from owner
 * - Reset LockedTarget to owner when combat target dies/becomes invalid
 * - Despawn pet if owner disconnects or changes world
 *
 * Thread safety: Uses PlayerRef.getUuid() (no Store access needed).
 * Only accesses petRef components via world.execute() on the pet's world thread.
 */
public class PetFollowSystem extends TickingSystem<EntityStore> {

    private static final float INTERVAL_SEC = 0.5f;
    private float timer = 0f;

    private final EndgameQoL plugin;
    private final PetManager petManager;

    public PetFollowSystem(@Nonnull EndgameQoL plugin, @Nonnull PetManager petManager) {
        this.plugin = plugin;
        this.petManager = petManager;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        timer += dt;
        if (timer < INTERVAL_SEC) return;
        timer = 0f;

        if (!plugin.getConfig().get().pets().isEnabled()) return;

        var entries = new ArrayList<>(petManager.getPetsByOwner().entrySet());
        for (Map.Entry<UUID, Ref<EntityStore>> entry : entries) {
            UUID ownerUuid = entry.getKey();
            Ref<EntityStore> petRef = entry.getValue();

            if (petRef == null || !petRef.isValid()) {
                petManager.clearPet(ownerUuid);
                continue;
            }

            // Find owner using PlayerRef.getUuid() — NO Store access, thread-safe
            PlayerRef ownerPlayerRef = findPlayerRef(ownerUuid);
            if (ownerPlayerRef == null) {
                despawnPetSafe(petRef, ownerUuid);
                continue;
            }

            Ref<EntityStore> ownerRef = ownerPlayerRef.getReference();
            if (ownerRef == null || !ownerRef.isValid()) {
                despawnPetSafe(petRef, ownerUuid);
                continue;
            }

            // All pet component access must happen on the pet's world thread
            World petWorld;
            try {
                petWorld = petRef.getStore().getExternalData().getWorld();
            } catch (Exception e) {
                petManager.clearPet(ownerUuid);
                continue;
            }
            if (petWorld == null || !petWorld.isAlive()) {
                petManager.clearPet(ownerUuid);
                continue;
            }

            // Check owner is in same world (compare world objects, no Store access needed)
            World ownerWorld;
            try {
                ownerWorld = ownerRef.getStore().getExternalData().getWorld();
            } catch (Exception e) {
                continue; // Skip this tick, owner might be transitioning
            }

            if (ownerWorld == null || !ownerWorld.equals(petWorld)) {
                despawnPetSafe(petRef, ownerUuid);
                continue;
            }

            // Execute all pet component access on the pet's world thread
            final Ref<EntityStore> finalOwnerRef = ownerRef;
            petWorld.execute(() -> {
                if (!petRef.isValid() || !finalOwnerRef.isValid()) return;

                try {
                    Store<EntityStore> petStore = petRef.getStore();

                    // Clear LockedTarget if combat target died/invalid — pet returns to wandering near owner
                    NPCEntity npc = petStore.getComponent(petRef, NPCEntity.getComponentType());
                    if (npc != null && npc.getRole() != null) {
                        Ref<EntityStore> currentTarget = npc.getRole().getMarkedEntitySupport()
                                .getMarkedEntityRef("LockedTarget");
                        if (currentTarget != null && !currentTarget.isValid()) {
                            npc.getRole().getMarkedEntitySupport().setMarkedEntity("LockedTarget", null);
                            // Take off again after combat ends
                            npc.getRole().setActiveMotionController(petRef, npc, "Fly", petStore);
                        }
                    }

                    // Emergency teleport if too far
                    float teleportDistSq = plugin.getConfig().get().pets().getTeleportDistance();
                    teleportDistSq *= teleportDistSq;

                    TransformComponent petTransform = petStore.getComponent(petRef, TransformComponent.getComponentType());
                    TransformComponent ownerTransform = finalOwnerRef.getStore().getComponent(
                            finalOwnerRef, TransformComponent.getComponentType());

                    if (petTransform != null && ownerTransform != null) {
                        double distSq = petTransform.getPosition().distanceSquaredTo(ownerTransform.getPosition());
                        if (distSq > teleportDistSq) {
                            Vector3d ownerPos = ownerTransform.getPosition();
                            Vector3d targetPos = new Vector3d(ownerPos.x + 2, ownerPos.y + 1, ownerPos.z + 2);
                            var teleportType = com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.getComponentType();
                            var teleport = com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.createForPlayer(
                                    petWorld, targetPos, new Vector3f(0, 0, 0));
                            petStore.addComponent(petRef, teleportType, teleport);
                        }
                    }
                } catch (Exception ignored) {
                    // Non-fatal: pet might have been removed between check and execute
                }
            });
        }
    }

    private void despawnPetSafe(Ref<EntityStore> petRef, UUID ownerUuid) {
        try {
            World world = petRef.getStore().getExternalData().getWorld();
            if (world != null && world.isAlive()) {
                world.execute(() -> {
                    if (petRef.isValid()) {
                        petRef.getStore().removeEntity(petRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
                    }
                });
            }
        } catch (Exception ignored) {}
        petManager.clearPet(ownerUuid);
    }

    /**
     * Find PlayerRef by UUID using PlayerRef.getUuid() — thread-safe, no Store access.
     */
    @Nullable
    private PlayerRef findPlayerRef(@Nonnull UUID uuid) {
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p == null) continue;
            if (uuid.equals(p.getUuid())) return p;
        }
        return null;
    }
}
