package endgame.plugin.systems.portal;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.portals.resources.PortalWorld;
import com.hypixel.hytale.builtin.portals.integrations.PortalRemovalCondition;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.portalworld.PortalSpawnConfig;
import com.hypixel.hytale.server.core.asset.type.portalworld.PortalType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.spawn.IndividualSpawnProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import endgame.plugin.EndgameQoL;
import endgame.plugin.utils.EntityUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Manages the Temporal Portal system using the HyRifts pattern:
 * - Places Portal_Return blocks in overworld (visual + particles + ambient sound)
 * - Proximity detection teleports players via InstancesPlugin
 * - Portal_Return block in instance handles native return teleportation
 *
 * Thread-safe: ConcurrentHashMap, volatile fields, world.execute().
 */
public class TemporalPortalManager {

    private final EndgameQoL plugin;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, TemporalPortalSession> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> entryCooldowns = new ConcurrentHashMap<>();

    private volatile long lastSpawnTimeMs;
    private volatile long nextSpawnIntervalMs;

    private static final String PORTAL_BLOCK_ID = "Endgame_Temporal_Portal";
    private static final String PARTICLE_SPAWN = "Praetorian_Summon_Spawn";
    private static final long SPAWN_CHECK_INTERVAL_MS = 30_000;
    private static final long PROXIMITY_TICK_MS = 500;
    private static final long MAINTENANCE_INTERVAL_MS = 60_000;
    private static final long INITIAL_DELAY_MS = 60_000;
    private static final double ENTER_RADIUS_SQ = 4.0; // 2 blocks
    private static final double ENTER_RADIUS_Y = 3.0;
    private static final long ENTRY_COOLDOWN_MS = 5_000;

    public TemporalPortalManager(@Nonnull EndgameQoL plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EndgameQoL-TemporalPortal");
            t.setDaemon(true);
            return t;
        });
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void start() {
        lastSpawnTimeMs = System.currentTimeMillis();
        nextSpawnIntervalMs = randomInterval();

        scheduler.scheduleAtFixedRate(this::safeSpawnTick, INITIAL_DELAY_MS, SPAWN_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::safeProximityTick, 2_000, PROXIMITY_TICK_MS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::safeMaintenanceTick, MAINTENANCE_INTERVAL_MS, MAINTENANCE_INTERVAL_MS, TimeUnit.MILLISECONDS);

        plugin.getLogger().atInfo().log("[TemporalPortal] System started");
    }

    public void stop() {
        for (TemporalPortalSession session : activeSessions.values()) {
            closePortal(session);
        }
        activeSessions.clear();
        entryCooldowns.clear();

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        plugin.getLogger().atInfo().log("[TemporalPortal] System stopped");
    }

    public void forceClear() {
        activeSessions.clear();
        entryCooldowns.clear();
    }

    // =========================================================================
    // Safe wrappers (prevent scheduler death)
    // =========================================================================

    private void safeSpawnTick() {
        try { trySpawnPortal(); } catch (Exception e) {
            plugin.getLogger().atWarning().log("[TemporalPortal] Spawn error: %s", e.getMessage());
        }
    }

    private void safeProximityTick() {
        try { proximityTick(); } catch (Exception e) {
            plugin.getLogger().atWarning().log("[TemporalPortal] Proximity error: %s", e.getMessage());
        }
    }

    private void safeMaintenanceTick() {
        try { maintenanceTick(); } catch (Exception e) {
            plugin.getLogger().atWarning().log("[TemporalPortal] Maintenance error: %s", e.getMessage());
        }
    }

    // =========================================================================
    // Spawn Logic
    // =========================================================================

    private void trySpawnPortal() {
        TemporalPortalConfig config = plugin.getConfig().get().getTemporalPortalConfig();
        if (!config.isEnabled()) return;
        if (activeSessions.size() >= config.getMaxConcurrentPortals()) return;

        long now = System.currentTimeMillis();
        if (now - lastSpawnTimeMs < nextSpawnIntervalMs) return;

        TemporalPortalSession.DungeonType dungeonType = pickRandomEnabledDungeon(config);
        if (dungeonType == null) return;

        PlayerRef targetPlayer = pickRandomOverworldPlayer();
        if (targetPlayer == null) return;

        Ref<EntityStore> playerRef = targetPlayer.getReference();
        if (playerRef == null || !playerRef.isValid()) return;

        TransformComponent transform = playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) return;

        Vector3d playerPos = transform.getPosition();
        World world = playerRef.getStore().getExternalData().getWorld();
        if (world == null || !world.isAlive()) return;

        // Try up to 3 positions, skip protected zones
        float radius = config.getSpawnOffsetRadius();
        Vector3d spawnPos = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            double offsetX = Math.cos(angle) * (3 + ThreadLocalRandom.current().nextDouble() * (radius - 3));
            double offsetZ = Math.sin(angle) * (3 + ThreadLocalRandom.current().nextDouble() * (radius - 3));
            int bx = (int) Math.floor(playerPos.x + offsetX);
            int bz = (int) Math.floor(playerPos.z + offsetZ);
            if (!isPositionProtected(world.getName(), bx, (int) playerPos.y, bz)) {
                spawnPos = new Vector3d(bx, playerPos.y, bz);
                break;
            }
        }
        if (spawnPos == null) return;

        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        TemporalPortalSession session = new TemporalPortalSession(sessionId, dungeonType);
        placePortalBlock(session, world, spawnPos, config);

        lastSpawnTimeMs = now;
        nextSpawnIntervalMs = randomInterval();
    }

    // =========================================================================
    // Portal Block Placement & Removal
    // =========================================================================

    private void placePortalBlock(TemporalPortalSession session, World world,
                                   Vector3d position, TemporalPortalConfig config) {
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y);
        int bz = (int) Math.floor(position.z);

        world.execute(() -> {
            try {
                // Clear 3x3x3 area above ground + place portal block
                for (int dy = 0; dy < 3; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            world.setBlock(bx + dx, by + dy, bz + dz, "Empty");
                        }
                    }
                }
                world.setBlock(bx, by, bz, PORTAL_BLOCK_ID);

                session.setSpawnWorldName(world.getName());
                session.setPortalPosition(new Vector3d(bx + 0.5, by, bz + 0.5));
                activeSessions.put(session.getId(), session);

                plugin.getLogger().atInfo().log("[TemporalPortal] Placed %s portal at (%d, %d, %d) in %s [session=%s]",
                        session.getDungeonType().getDisplayName(), bx, by, bz, world.getName(), session.getId());

                announcePortalSpawn(world, session.getPortalPosition(), session.getDungeonType(), config.getAnnounceRadius());
                spawnPortalParticles(world, session.getPortalPosition());
            } catch (Exception e) {
                plugin.getLogger().atWarning().log("[TemporalPortal] Failed to place portal block: %s", e.getMessage());
            }
        });
    }

    private void removePortalBlock(@Nonnull TemporalPortalSession session) {
        Vector3d pos = session.getPortalPosition();
        String worldName = session.getSpawnWorldName();
        if (pos == null || worldName == null) return;

        World world = Universe.get().getWorld(worldName);
        if (world == null || !world.isAlive()) return;

        int bx = (int) Math.floor(pos.x);
        int by = (int) Math.floor(pos.y);
        int bz = (int) Math.floor(pos.z);
        world.execute(() -> world.setBlock(bx, by, bz, "Empty"));
    }

    // =========================================================================
    // Proximity Detection (HyRifts pattern: tick every 500ms)
    // =========================================================================

    private void proximityTick() {
        long now = System.currentTimeMillis();
        entryCooldowns.entrySet().removeIf(e -> now - e.getValue() > ENTRY_COOLDOWN_MS * 3);

        for (TemporalPortalSession session : activeSessions.values()) {
            Vector3d portalPos = session.getPortalPosition();
            String portalWorldName = session.getSpawnWorldName();
            if (portalPos == null || portalWorldName == null) continue;
            if (session.isPortalExpired(plugin.getConfig().get().getTemporalPortalConfig().getPortalDurationSeconds())) continue;

            // Find the portal's world and run ECS access on its thread
            World portalWorld = Universe.get().getWorld(portalWorldName);
            if (portalWorld == null || !portalWorld.isAlive()) continue;

            portalWorld.execute(() -> {
                long tick = System.currentTimeMillis();
                for (PlayerRef player : Universe.get().getPlayers()) {
                    if (player == null) continue;
                    Ref<EntityStore> ref = player.getReference();
                    if (ref == null || !ref.isValid()) continue;

                    World playerWorld = ref.getStore().getExternalData().getWorld();
                    if (playerWorld == null || !playerWorld.getName().equals(portalWorldName)) continue;

                    TransformComponent tc = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
                    if (tc == null) continue;

                    Vector3d playerPos = tc.getPosition();
                    double dx = playerPos.x - portalPos.x;
                    double dz = playerPos.z - portalPos.z;
                    double hDistSq = dx * dx + dz * dz;
                    double dy = Math.abs(playerPos.y - portalPos.y);

                    if (hDistSq <= ENTER_RADIUS_SQ && dy <= ENTER_RADIUS_Y) {
                        UUID playerUuid = EntityUtils.getUuid(player);
                        if (playerUuid == null) continue;

                        Long lastEntry = entryCooldowns.get(playerUuid);
                        if (lastEntry != null && (tick - lastEntry) < ENTRY_COOLDOWN_MS) continue;

                        if (session.getInstanceState() == TemporalPortalSession.InstanceState.NONE) {
                            startInstanceGeneration(portalWorld, session, player);
                            entryCooldowns.put(playerUuid, tick);
                        } else if (session.getInstanceState() == TemporalPortalSession.InstanceState.READY) {
                            World instanceWorld = session.getInstanceWorld();
                            if (instanceWorld != null && instanceWorld.isAlive()) {
                                teleportToInstance(player, portalWorld, instanceWorld);
                                entryCooldowns.put(playerUuid, tick);
                            }
                        }
                    }
                }
            });
        }
    }

    // =========================================================================
    // Instance Creation (InstancesPlugin pattern from HyRifts)
    // =========================================================================

    private void startInstanceGeneration(World originWorld, TemporalPortalSession session, @Nullable PlayerRef initiatingPlayer) {
        String portalTypeId = session.getDungeonType().getPortalTypeId();
        PortalType portalType = (PortalType) PortalType.getAssetMap().getAsset(portalTypeId);
        if (portalType == null || !InstancesPlugin.doesInstanceAssetExist(portalType.getInstanceId())) {
            plugin.getLogger().atWarning().log("[TemporalPortal] PortalType '%s' or instance not found", portalTypeId);
            session.setInstanceState(TemporalPortalSession.InstanceState.FAILED);
            return;
        }

        session.setInstanceState(TemporalPortalSession.InstanceState.SPAWNING);
        Vector3d pos = session.getPortalPosition();
        Transform returnTransform = new Transform(pos.x, pos.y + 0.5, pos.z);

        InstancesPlugin.get().spawnInstance(portalType.getInstanceId(), originWorld, returnTransform)
                .thenAcceptAsync(spawnedWorld -> {
                    WorldConfig worldConfig = spawnedWorld.getWorldConfig();
                    worldConfig.setDeleteOnUniverseStart(true);
                    worldConfig.setDeleteOnRemove(true);

                    // Init PortalWorld resource (enables Portal_Return interaction in instance)
                    PortalWorld portalWorld = (PortalWorld) spawnedWorld.getEntityStore().getStore()
                            .getResource(PortalWorld.getResourceType());
                    if (portalWorld != null) {
                        int timeLimitSec = plugin.getConfig().get().getTemporalPortalConfig().getInstanceTimeLimitSeconds();
                        portalWorld.init(portalType, timeLimitSec,
                                new PortalRemovalCondition((double) timeLimitSec), null);
                    }

                    // Place return portal at instance spawn point
                    placeReturnPortal(spawnedWorld, portalType);

                    session.setInstanceWorld(spawnedWorld);
                    plugin.getLogger().atInfo().log("[TemporalPortal] Instance ready: %s [session=%s]",
                            spawnedWorld.getName(), session.getId());

                    // Teleport the initiating player immediately
                    if (initiatingPlayer != null) {
                        teleportToInstance(initiatingPlayer, originWorld, spawnedWorld);
                    }
                }, originWorld)
                .exceptionally(t -> {
                    plugin.getLogger().atWarning().log("[TemporalPortal] Instance spawn failed: %s", t.getMessage());
                    session.setInstanceState(TemporalPortalSession.InstanceState.FAILED);
                    return null;
                });
    }

    // =========================================================================
    // Return Portal (placed inside instance at spawn point)
    // =========================================================================

    private void placeReturnPortal(World instanceWorld, PortalType portalType) {
        PortalSpawnConfig spawnConfig = portalType.getSpawn();
        ISpawnProvider spawnOverride = spawnConfig.getSpawnProviderOverride();
        if (spawnOverride == null) {
            plugin.getLogger().atWarning().log("[TemporalPortal] No SpawnProviderOverride on PortalType");
            return;
        }

        Transform spawnTransform = spawnOverride.getSpawnPoint(instanceWorld, null);
        if (spawnTransform == null) {
            plugin.getLogger().atWarning().log("[TemporalPortal] SpawnProvider returned null transform");
            return;
        }

        // Set spawn point on PortalWorld resource (required for Portal_Return interaction)
        PortalWorld portalWorld = (PortalWorld) instanceWorld.getEntityStore().getStore()
                .getResource(PortalWorld.getResourceType());
        if (portalWorld != null) {
            portalWorld.setSpawnPoint(spawnTransform);
        }

        // Set world spawn provider (so players respawn at portal)
        instanceWorld.getWorldConfig().setSpawnProvider(new IndividualSpawnProvider(spawnTransform));

        Vector3d spawnPos = spawnTransform.getPosition();
        int px = (int) Math.floor(spawnPos.x);
        int py = (int) Math.floor(spawnPos.y);
        int pz = (int) Math.floor(spawnPos.z);

        // Delay block placement to ensure chunk is loaded
        scheduler.schedule(() -> {
            instanceWorld.execute(() -> {
                try {
                    for (int dy = 0; dy < 3; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                instanceWorld.setBlock(px + dx, py + dy, pz + dz, "Empty");
                            }
                        }
                    }
                    instanceWorld.setBlock(px, py, pz, "Portal_Return");
                    plugin.getLogger().atInfo().log("[TemporalPortal] Placed return portal at (%d, %d, %d)", px, py, pz);
                } catch (Exception e) {
                    plugin.getLogger().atWarning().log("[TemporalPortal] Failed to place return portal: %s", e.getMessage());
                }
            });
        }, 3, TimeUnit.SECONDS);
    }

    // =========================================================================
    // Teleportation (InstancesPlugin.teleportPlayerToInstance)
    // =========================================================================

    private void teleportToInstance(PlayerRef playerRef, World fromWorld, World targetWorld) {
        fromWorld.execute(() -> {
            try {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) return;
                InstancesPlugin.teleportPlayerToInstance(ref, ref.getStore(), targetWorld, null);
                plugin.getLogger().atFine().log("[TemporalPortal] Teleported %s to instance %s",
                        playerRef.getUsername(), targetWorld.getName());
            } catch (Exception e) {
                plugin.getLogger().atWarning().log("[TemporalPortal] Teleport failed: %s", e.getMessage());
            }
        });
    }

    // =========================================================================
    // Maintenance (expiry, cleanup)
    // =========================================================================

    private void maintenanceTick() {
        TemporalPortalConfig config = plugin.getConfig().get().getTemporalPortalConfig();

        for (var entry : new ArrayList<>(activeSessions.entrySet())) {
            TemporalPortalSession session = entry.getValue();

            if (session.isPortalExpired(config.getPortalDurationSeconds())) {
                plugin.getLogger().atFine().log("[TemporalPortal] Portal expired [session=%s]", session.getId());
                closePortal(session);
                activeSessions.remove(entry.getKey());
            }
        }
    }

    private void closePortal(@Nonnull TemporalPortalSession session) {
        // Remove overworld portal block
        removePortalBlock(session);

        // Remove instance world (delayed to let players teleport out)
        World instanceWorld = session.getInstanceWorld();
        if (instanceWorld != null && instanceWorld.isAlive()) {
            scheduler.schedule(() -> {
                try {
                    if (instanceWorld.isAlive()) {
                        instanceWorld.execute(() -> InstancesPlugin.safeRemoveInstance(instanceWorld));
                    }
                } catch (Exception e) {
                    plugin.getLogger().atWarning().log("[TemporalPortal] Instance removal failed: %s", e.getMessage());
                }
            }, 3, TimeUnit.SECONDS);
        }
    }

    // =========================================================================
    // Admin: force spawn
    // =========================================================================

    public void forceSpawnNear(@Nonnull PlayerRef targetPlayer, @Nonnull TemporalPortalSession.DungeonType dungeonType) {
        Ref<EntityStore> playerRef = targetPlayer.getReference();
        if (playerRef == null || !playerRef.isValid()) return;

        TransformComponent transform = playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) return;

        Vector3d playerPos = transform.getPosition();
        World world = playerRef.getStore().getExternalData().getWorld();
        if (world == null || !world.isAlive()) return;

        TemporalPortalConfig config = plugin.getConfig().get().getTemporalPortalConfig();
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        int bx = (int) Math.floor(playerPos.x + Math.cos(angle) * 4);
        int bz = (int) Math.floor(playerPos.z + Math.sin(angle) * 4);

        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        TemporalPortalSession session = new TemporalPortalSession(sessionId, dungeonType);
        placePortalBlock(session, world, new Vector3d(bx, playerPos.y, bz), config);
    }

    // =========================================================================
    // Protection check (OrbisGuard + SimpleClaims)
    // =========================================================================

    private boolean isPositionProtected(String worldName, int x, int y, int z) {
        var ogBridge = plugin.getOrbisGuardBridge();
        if (ogBridge != null && ogBridge.isPositionProtected(worldName, x, y, z)) {
            return true;
        }
        var claimBridge = endgame.plugin.integration.ClaimProtectionBridge.get();
        return claimBridge.isPositionClaimed(worldName, x, z);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @Nullable
    private TemporalPortalSession.DungeonType pickRandomEnabledDungeon(TemporalPortalConfig config) {
        List<TemporalPortalSession.DungeonType> enabled = new ArrayList<>();
        if (config.isFrozenDungeonEnabled()) enabled.add(TemporalPortalSession.DungeonType.FROZEN_DUNGEON);
        if (config.isSwampDungeonEnabled()) enabled.add(TemporalPortalSession.DungeonType.SWAMP_DUNGEON);
        if (enabled.isEmpty()) return null;
        return enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
    }

    @Nullable
    private PlayerRef pickRandomOverworldPlayer() {
        List<PlayerRef> candidates = new ArrayList<>();
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p == null) continue;
            Ref<EntityStore> ref = p.getReference();
            if (ref == null || !ref.isValid()) continue;
            World w = ref.getStore().getExternalData().getWorld();
            if (w != null && !w.getName().toLowerCase().contains("instance-")
                    && !w.getName().toLowerCase().contains("temporal-portal-")) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private long randomInterval() {
        TemporalPortalConfig config = plugin.getConfig().get().getTemporalPortalConfig();
        int min = config.getSpawnIntervalMinSeconds();
        int max = config.getSpawnIntervalMaxSeconds();
        return (min + ThreadLocalRandom.current().nextInt(Math.max(1, max - min))) * 1000L;
    }

    private void announcePortalSpawn(World world, Vector3d position,
                                      TemporalPortalSession.DungeonType type, float radius) {
        double radiusSq = radius * radius;
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p == null) continue;
            Ref<EntityStore> ref = p.getReference();
            if (ref == null || !ref.isValid()) continue;

            World pw = ref.getStore().getExternalData().getWorld();
            if (pw == null || !pw.getName().equals(world.getName())) continue;

            TransformComponent tc = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) continue;

            if (tc.getPosition().distanceSquaredTo(position) <= radiusSq) {
                p.sendMessage(Message.join(
                        Message.raw("[Temporal Portal] ").color(type.getColor()),
                        Message.raw("A " + type.getDisplayName() + " portal has appeared nearby!").color("#ffffff")
                ));
            }
        }
    }

    private void spawnPortalParticles(World world, Vector3d position) {
        List<Ref<EntityStore>> viewers = new ArrayList<>();
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p == null) continue;
            Ref<EntityStore> ref = p.getReference();
            if (ref == null || !ref.isValid()) continue;
            World pw = ref.getStore().getExternalData().getWorld();
            if (pw != null && pw.getName().equals(world.getName())) {
                viewers.add(ref);
            }
        }
        if (viewers.isEmpty()) return;

        try {
            Store<EntityStore> particleStore = viewers.getFirst().getStore();
            ParticleUtil.spawnParticleEffect(PARTICLE_SPAWN, position, viewers, particleStore);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public int getActiveSessionCount() { return activeSessions.size(); }
    public ConcurrentHashMap<String, TemporalPortalSession> getActiveSessions() { return activeSessions; }
}
