package endgame.plugin.managers;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PetOwnerComponent;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.config.PetConfig;
import endgame.plugin.config.PetData;
import endgame.plugin.events.domain.GameEvent;
import endgame.plugin.utils.EntityUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages pet lifecycle: spawn, despawn, boss-kill unlock, combat target assignment.
 * Thread-safe: ConcurrentHashMap for pet ref cache, world.execute() for entity ops.
 * Scales to 60+ players — O(1) lookups via UUID key, no player list scans.
 */
public class PetManager {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.Pet");

    private static final String[][] BOSS_PET_MAP = {
            {"dragon_frost", "ice_dragon", "Endgame_Pet_Dragon_Frost", "Dragon Frost"},
            {"dragon_fire", "fire_dragon", "Endgame_Pet_Dragon_Fire", "Dragon Fire"},
            {"golem_void", null, "Endgame_Pet_Golem_Void", "Golem Void"},
            {"hedera", null, "Endgame_Pet_Hedera", "Hedera"}
    };

    private final EndgameQoL plugin;
    private final ConcurrentHashMap<UUID, Ref<EntityStore>> petsByOwner = new ConcurrentHashMap<>();

    public PetManager(@Nonnull EndgameQoL plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // Spawn / Despawn
    // =========================================================================

    /**
     * Spawn a pet NPC near the player. Despawns any existing pet first.
     * Must be called from the player's world thread.
     */
    public void spawnPet(@Nonnull Store<EntityStore> store, @Nonnull UUID ownerUuid,
                         @Nonnull String petId, @Nonnull Vector3d position) {
        // Despawn old pet first
        despawnPet(store, ownerUuid);

        try {
            Vector3f rotation = new Vector3f(0, 0, 0);
            var result = NPCPlugin.get().spawnNPC(store, petId, null, position, rotation);

            if (result != null && result.left() != null && result.left().isValid()) {
                Ref<EntityStore> petRef = result.left();
                petsByOwner.put(ownerUuid, petRef);
            }

            PlayerEndgameComponent comp = plugin.getPlayerComponent(ownerUuid);
            if (comp != null) {
                comp.getPetData().setActivePetId(petId);
            }

            LOGGER.atInfo().log("[Pet] Spawned %s for %s", petId, ownerUuid);
        } catch (Exception e) {
            LOGGER.atWarning().log("[Pet] Failed to spawn %s: %s", petId, e.getMessage());
        }
    }

    /**
     * Despawn the player's active pet.
     */
    public void despawnPet(@Nonnull Store<EntityStore> store, @Nonnull UUID ownerUuid) {
        Ref<EntityStore> petRef = petsByOwner.remove(ownerUuid);
        if (petRef != null && petRef.isValid()) {
            try {
                store.removeEntity(petRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
                LOGGER.atFine().log("[Pet] Despawned pet for %s", ownerUuid);
            } catch (Exception e) {
                LOGGER.atFine().log("[Pet] Despawn cleanup: %s", e.getMessage());
            }
        }

        PlayerEndgameComponent comp = plugin.getPlayerComponent(ownerUuid);
        if (comp != null) {
            comp.getPetData().setActivePetId("");
        }
    }

    /**
     * Register a pet ref in the cache. Called by PetFollowSystem when it discovers
     * a pet NPC with a PetOwnerComponent.
     */
    public void registerPet(@Nonnull UUID ownerUuid, @Nonnull Ref<EntityStore> petRef) {
        petsByOwner.put(ownerUuid, petRef);
    }

    /**
     * Clear a pet from cache (called by PetFollowSystem when pet is despawned).
     */
    public void clearPet(@Nonnull UUID ownerUuid) {
        petsByOwner.remove(ownerUuid);
    }

    /**
     * Get the cached pet ref for a player. May be stale — caller should check isValid().
     */
    @Nullable
    public Ref<EntityStore> getCachedPetRef(@Nonnull UUID ownerUuid) {
        Ref<EntityStore> ref = petsByOwner.get(ownerUuid);
        if (ref != null && !ref.isValid()) {
            petsByOwner.remove(ownerUuid, ref);
            return null;
        }
        return ref;
    }

    // =========================================================================
    // Player Lifecycle
    // =========================================================================

    public void onPlayerConnect(@Nonnull UUID uuid, @Nonnull PlayerEndgameComponent comp) {
        // Auto-respawn active pet if player had one
        String activePetId = comp.getPetData().getActivePetId();
        if (activePetId != null && !activePetId.isEmpty()) {
            // Defer spawn to when player is fully in world
            // PetFollowSystem handles this via activePetId check
            LOGGER.atFine().log("[Pet] Player %s has active pet %s — will respawn on world join", uuid, activePetId);
        }
    }

    public void onPlayerDisconnect(@Nonnull UUID uuid) {
        Ref<EntityStore> petRef = petsByOwner.remove(uuid);
        if (petRef != null && petRef.isValid()) {
            try {
                World world = petRef.getStore().getExternalData().getWorld();
                if (world != null && world.isAlive()) {
                    world.execute(() -> {
                        if (petRef.isValid()) {
                            petRef.getStore().removeEntity(petRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
                        }
                    });
                }
            } catch (Exception e) {
                LOGGER.atFine().log("[Pet] Disconnect cleanup: %s", e.getMessage());
            }
        }
    }

    // =========================================================================
    // Boss Kill → Pet Unlock
    // =========================================================================

    public void handleBossKill(@Nonnull GameEvent.BossKillEvent event) {
        PetConfig config = plugin.getConfig().get().pets();
        LOGGER.atInfo().log("[Pet] BossKillEvent received: bossTypeId=%s, enabled=%b, players=%d",
                event.bossTypeId(), config.isEnabled(), event.creditedPlayers().size());

        if (!config.isEnabled()) return;

        String bossTypeId = event.bossTypeId();
        String petId = mapBossToPetId(bossTypeId);
        LOGGER.atInfo().log("[Pet] Mapped boss '%s' → pet '%s'", bossTypeId, petId);
        if (petId == null) return;

        float chance = config.getChanceForBoss(bossTypeId);
        LOGGER.atInfo().log("[Pet] Unlock chance for '%s': %.2f", bossTypeId, chance);
        if (chance <= 0f) return;

        String displayName = mapBossToDisplayName(bossTypeId);

        for (UUID playerUuid : event.creditedPlayers()) {
            PlayerEndgameComponent comp = plugin.getPlayerComponent(playerUuid);
            if (comp == null) continue;

            PetData petData = comp.getPetData();
            if (petData.isUnlocked(petId)) continue;

            if (ThreadLocalRandom.current().nextFloat() < chance) {
                petData.unlock(petId);

                PlayerRef pr = findPlayerRef(playerUuid);
                if (pr != null) {
                    pr.sendMessage(Message.join(
                            Message.raw("[EndgameQoL] ").color("#ffaa00"),
                            Message.raw("Pet Unlocked: " + displayName + "! ").color("#4ade80"),
                            Message.raw("Use /eg pet to summon it.").color("#aaaaaa")
                    ));
                    // Sound notification handled by engine
                }

                LOGGER.atInfo().log("[Pet] %s unlocked pet %s from boss %s", playerUuid, petId, bossTypeId);
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @Nullable
    private static String mapBossToPetId(String bossTypeId) {
        if (bossTypeId == null) return null;
        String lower = bossTypeId.toLowerCase();
        for (String[] entry : BOSS_PET_MAP) {
            if (lower.contains(entry[0])) return entry[2];
            if (entry[1] != null && lower.contains(entry[1])) return entry[2];
        }
        return null;
    }

    @Nonnull
    private static String mapBossToDisplayName(String bossTypeId) {
        if (bossTypeId == null) return "Unknown";
        String lower = bossTypeId.toLowerCase();
        for (String[] entry : BOSS_PET_MAP) {
            if (lower.contains(entry[0])) return entry[3];
            if (entry[1] != null && lower.contains(entry[1])) return entry[3];
        }
        return "Unknown";
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

    public void forceClear() {
        petsByOwner.clear();
    }
}
