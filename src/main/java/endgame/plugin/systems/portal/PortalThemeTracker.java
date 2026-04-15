package endgame.plugin.systems.portal;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * When a player right-clicks a base {@code Endgame_Gateway} block while holding one of
 * the 3 Endgame PortalKey items, we immediately replace the block with the matching themed
 * variant ({@code Endgame_Gateway_Void / _Frozen / _Swamp}). The variant has its own
 * Active state particles baked in, so the engine handles rotation automatically.
 *
 * <p><b>Timing:</b> The theme swap must happen at right-click, BEFORE the engine's internal
 * {@code setBlock(Spawning)} on Summon click — otherwise the particle/state pipeline gets
 * desynced (tested). So we swap immediately and rely on a commit-or-revert timer:
 *
 * <ul>
 *   <li>If the user clicks "Summon Portal" → {@code AddWorldEvent} fires for the new instance
 *       → {@link #onInstanceWorldCreated} cancels the revert timer. The portal stays themed
 *       until the instance is destroyed.</li>
 *   <li>If the user closes the UI without summoning → the revert timer fires after
 *       {@link #REVERT_TIMEOUT_MS} and the block returns to the neutral base.</li>
 *   <li>If the instance is destroyed (last player leaves) → {@link #onWorldRemoved}
 *       reverts the source block.</li>
 * </ul>
 */
public class PortalThemeTracker extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private static final Map<String, String> KEY_TO_VARIANT = Map.of(
            "Endgame_Portal_Void_Realm",      "Endgame_Gateway_Void",
            "Endgame_Portal_Frozen_Dungeon",  "Endgame_Gateway_Frozen",
            "Endgame_Portal_Swamp_Dungeon",   "Endgame_Gateway_Swamp"
    );

    private static final Set<String> VARIANT_IDS = Set.copyOf(KEY_TO_VARIANT.values());
    private static final String BASE_BLOCK_ID = "Endgame_Gateway";

    /** How long the themed state persists if the user doesn't click "Summon Portal". */
    private static final long REVERT_TIMEOUT_MS = 15_000L; // 15 sec

    /** Per-player: last themed activation. Transient. */
    private static final ConcurrentHashMap<UUID, Activation> PLAYER_LAST_ACTIVATION =
            new ConcurrentHashMap<>();

    /** Pending revert timers, per-player. Cancelled on Summon click or disconnect. */
    private static final ConcurrentHashMap<UUID, ScheduledFuture<?>> PENDING_REVERTS =
            new ConcurrentHashMap<>();

    /** Instance world name → source portal info. Persists for the instance's whole lifetime. */
    private static final ConcurrentHashMap<String, SourcePortal> INSTANCE_TO_SOURCE =
            new ConcurrentHashMap<>();

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "PortalThemeTracker-Revert");
                t.setDaemon(true);
                return t;
            });

    public PortalThemeTracker() {
        super(UseBlockEvent.Pre.class);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> chunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull UseBlockEvent.Pre event) {
        String blockId = event.getBlockType().getId();
        if (!blockId.equals(BASE_BLOCK_ID)) return;

        ItemStack held = event.getContext().getHeldItem();
        if (held == null || held.isEmpty()) return;

        String variantId = KEY_TO_VARIANT.get(held.getItemId());
        if (variantId == null) return;

        World world = store.getExternalData().getWorld();
        if (world == null) return;

        // Skip themed replacement inside dungeon instance worlds — those host the return
        // portal; swapping them with a single-key variant bricks the only way home.
        String worldName = world.getName();
        if (worldName != null && worldName.startsWith("instance-")) return;

        PlayerRef pr = chunk.getComponent(index, PlayerRef.getComponentType());
        UUID trackedUuid = (pr != null) ? pr.getUuid() : null;
        if (trackedUuid == null) return;

        Vector3i pos = event.getTargetBlock();

        // Swap immediately so the UI opens over a themed block and the Spawning/Active
        // particles display correctly when Summon is clicked.
        try {
            BlockType variant = BlockType.fromString(variantId);
            if (variant == null) {
                EndgameQoL plugin = EndgameQoL.getInstance();
                if (plugin != null) plugin.getLogger().atWarning().log(
                        "[PortalTheme] Variant block '%s' not registered", variantId);
                return;
            }

            long chunkIndex = com.hypixel.hytale.math.util.ChunkUtil
                    .indexChunkFromBlock(pos.x, pos.z);
            WorldChunk chunk2 = world.getChunk(chunkIndex);
            if (chunk2 == null) return;

            int variantBlockId = BlockType.getAssetMap().getIndex(variant.getId());
            int rotation = world.getBlockRotationIndex(pos.x, pos.y, pos.z);
            chunk2.setBlock(pos.x, pos.y, pos.z, variantBlockId, variant, rotation, 0, 198);
        } catch (Exception e) {
            EndgameQoL plugin = EndgameQoL.getInstance();
            if (plugin != null) plugin.getLogger().atWarning().log(
                    "[PortalTheme] Theme swap failed: %s", e.getMessage());
            return;
        }

        Activation activation = new Activation(world.getName(), pos, variantId,
                System.currentTimeMillis());
        PLAYER_LAST_ACTIVATION.put(trackedUuid, activation);

        // Cancel any previous pending revert for this player (re-themed before expiry).
        ScheduledFuture<?> previous = PENDING_REVERTS.remove(trackedUuid);
        if (previous != null) previous.cancel(false);

        // Schedule a revert if the player never clicks Summon. Captures `activation` to
        // avoid reverting if the entry has been replaced in the meantime.
        final UUID finalUuid = trackedUuid;
        ScheduledFuture<?> future = SCHEDULER.schedule(
                () -> revertIfUncommitted(finalUuid, activation),
                REVERT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        PENDING_REVERTS.put(trackedUuid, future);
    }

    /**
     * Revert timer callback. Fires if the user didn't click "Summon Portal" within
     * the timeout window. Checks the activation is still the one we scheduled for
     * (not superseded by re-theme or already committed).
     */
    private static void revertIfUncommitted(UUID playerUuid, Activation expected) {
        PENDING_REVERTS.remove(playerUuid);
        Activation current = PLAYER_LAST_ACTIVATION.get(playerUuid);
        if (current != expected) return; // superseded or already committed
        PLAYER_LAST_ACTIVATION.remove(playerUuid);

        World world = Universe.get().getWorld(expected.worldName);
        if (world == null || !world.isAlive()) return;

        final Vector3i pos = expected.pos;
        world.execute(() -> revertToBase(world, pos));
    }

    /**
     * Called when a new world is added (instance created). This fires when the player
     * clicks "Summon Portal" in the vanilla UI. We use this as the "committed" signal
     * and cancel the pending revert timer — the portal stays themed until instance destroy.
     */
    public static void onInstanceWorldCreated(String instanceWorldName) {
        if (instanceWorldName == null) return;
        String lname = instanceWorldName.toLowerCase();

        String expectedVariantId;
        if (lname.contains("endgame_frozen_dungeon")) {
            expectedVariantId = "Endgame_Gateway_Frozen";
        } else if (lname.contains("endgame_swamp_dungeon")) {
            expectedVariantId = "Endgame_Gateway_Swamp";
        } else if (lname.contains("endgame_void_realm") || lname.contains("endgame_golem_void")) {
            expectedVariantId = "Endgame_Gateway_Void";
        } else {
            return;
        }

        // Pick the most recent matching activation (the summoner most likely owns it).
        Map.Entry<UUID, Activation> best = null;
        for (Map.Entry<UUID, Activation> entry : PLAYER_LAST_ACTIVATION.entrySet()) {
            Activation a = entry.getValue();
            if (a.variantId == null || !a.variantId.equals(expectedVariantId)) continue;
            if (best == null || a.timestampMs > best.getValue().timestampMs) best = entry;
        }

        if (best == null) return;

        ScheduledFuture<?> future = PENDING_REVERTS.remove(best.getKey());
        if (future != null) future.cancel(false);
    }

    /**
     * Called when a player enters a world. If they recently themed a portal and the
     * destination is a dungeon instance, transfer the tracking from per-player to
     * per-instance so we can revert on instance destroy.
     */
    public static void onPlayerEnterInstance(UUID playerUuid, String destinationWorldName) {
        if (playerUuid == null || destinationWorldName == null) return;
        Activation activation = PLAYER_LAST_ACTIVATION.remove(playerUuid);
        if (activation == null) return;

        // Already committed — make sure no revert fires.
        ScheduledFuture<?> future = PENDING_REVERTS.remove(playerUuid);
        if (future != null) future.cancel(false);

        INSTANCE_TO_SOURCE.putIfAbsent(destinationWorldName,
                new SourcePortal(activation.worldName, activation.pos));
    }

    /**
     * Called when a world is removed (instance destroyed, all players left).
     */
    public static void onWorldRemoved(String worldName) {
        if (worldName == null) return;
        SourcePortal source = INSTANCE_TO_SOURCE.remove(worldName);
        if (source == null) return;

        World sourceWorld = Universe.get().getWorld(source.worldName);
        if (sourceWorld == null || !sourceWorld.isAlive()) return;

        final Vector3i pos = source.pos;
        sourceWorld.execute(() -> revertToBase(sourceWorld, pos));
    }

    /** Drop the tracked activation + cancel pending revert for a player (on disconnect). */
    public static void clearPlayer(UUID playerUuid) {
        if (playerUuid == null) return;
        PLAYER_LAST_ACTIVATION.remove(playerUuid);
        ScheduledFuture<?> future = PENDING_REVERTS.remove(playerUuid);
        if (future != null) future.cancel(false);
    }

    private static void revertToBase(World world, Vector3i pos) {
        try {
            BlockType current = world.getBlockType(pos.x, pos.y, pos.z);
            if (current == null) return;
            String currentId = current.getId();
            boolean isVariantState = false;
            for (String vid : VARIANT_IDS) {
                if (currentId.contains(vid)) { isVariantState = true; break; }
            }
            if (!isVariantState) return;

            BlockType base = BlockType.fromString(BASE_BLOCK_ID);
            if (base == null) return;

            long chunkIndex = com.hypixel.hytale.math.util.ChunkUtil
                    .indexChunkFromBlock(pos.x, pos.z);
            WorldChunk chunk = world.getChunk(chunkIndex);
            if (chunk == null) return;

            int baseBlockId = BlockType.getAssetMap().getIndex(base.getId());
            int rotation = world.getBlockRotationIndex(pos.x, pos.y, pos.z);
            chunk.setBlock(pos.x, pos.y, pos.z, baseBlockId, base, rotation, 0, 198);
        } catch (Exception e) {
            EndgameQoL plugin = EndgameQoL.getInstance();
            if (plugin != null) plugin.getLogger().atWarning().withCause(e).log(
                    "[PortalTheme] Revert failed: %s", e.getMessage());
        }
    }

    private static final class Activation {
        final String worldName;
        final Vector3i pos;
        final String variantId;
        final long timestampMs;

        Activation(String worldName, Vector3i pos, String variantId, long timestampMs) {
            this.worldName = worldName;
            this.pos = pos;
            this.variantId = variantId;
            this.timestampMs = timestampMs;
        }
    }

    private static final class SourcePortal {
        final String worldName;
        final Vector3i pos;

        SourcePortal(String worldName, Vector3i pos) {
            this.worldName = worldName;
            this.pos = pos;
        }
    }
}
