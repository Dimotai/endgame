package endgame.plugin.systems.portal;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Tracks the state of one active temporal portal + its linked instance.
 * Thread-safe: all mutable fields are volatile.
 */
public class TemporalPortalSession {

    public enum DungeonType {
        FROZEN_DUNGEON("Endgame_Frozen_Dungeon", "Frozen Dungeon", "#5bceff",
                "Endgame_Portal_Frozen_Dungeon"),
        SWAMP_DUNGEON("Endgame_Swamp_Dungeon", "Swamp Dungeon", "#23970c",
                "Endgame_Portal_Swamp_Dungeon");

        private final String instanceId;
        private final String displayName;
        private final String color;
        private final String portalTypeId;

        DungeonType(String instanceId, String displayName, String color, String portalTypeId) {
            this.instanceId = instanceId;
            this.displayName = displayName;
            this.color = color;
            this.portalTypeId = portalTypeId;
        }

        public String getInstanceId() { return instanceId; }
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
        public String getPortalTypeId() { return portalTypeId; }
    }

    public enum InstanceState { NONE, SPAWNING, READY, FAILED }

    // Identity
    @Nonnull private final String id;
    @Nonnull private final DungeonType dungeonType;
    private final long createdAtMs;

    // Overworld portal
    @Nullable private volatile String spawnWorldName;
    @Nullable private volatile Vector3d portalPosition;

    // Instance
    private volatile InstanceState instanceState = InstanceState.NONE;
    @Nullable private volatile World instanceWorld;

    public TemporalPortalSession(@Nonnull String id, @Nonnull DungeonType dungeonType) {
        this.id = id;
        this.dungeonType = dungeonType;
        this.createdAtMs = System.currentTimeMillis();
    }

    // --- Identity ---
    @Nonnull public String getId() { return id; }
    @Nonnull public DungeonType getDungeonType() { return dungeonType; }
    public long getCreatedAtMs() { return createdAtMs; }

    // --- Overworld portal ---
    @Nullable public String getSpawnWorldName() { return spawnWorldName; }
    public void setSpawnWorldName(@Nullable String name) { this.spawnWorldName = name; }
    @Nullable public Vector3d getPortalPosition() { return portalPosition; }
    public void setPortalPosition(@Nullable Vector3d pos) { this.portalPosition = pos; }

    // --- Instance ---
    public InstanceState getInstanceState() { return instanceState; }
    public void setInstanceState(InstanceState s) { this.instanceState = s; }
    @Nullable public World getInstanceWorld() { return instanceWorld; }
    public void setInstanceWorld(@Nullable World w) {
        this.instanceWorld = w;
        if (w != null) this.instanceState = InstanceState.READY;
    }

    // --- Lifecycle ---
    public boolean isPortalExpired(int durationSeconds) {
        return System.currentTimeMillis() - createdAtMs > durationSeconds * 1000L;
    }
}
