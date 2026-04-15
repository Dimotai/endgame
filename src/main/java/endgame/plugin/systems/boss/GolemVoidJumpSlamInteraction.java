package endgame.plugin.systems.boss;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.managers.boss.GenericBossManager;

import javax.annotation.Nonnull;

/**
 * Custom interaction "GolemVoidJumpSlam" — teleports the Golem Void HIGH ABOVE
 * the nearest player's position so gravity (+ a downward ApplyForce in the
 * JSON chain) can slam it down onto them. This produces a real vertical jump
 * instead of the previous horizontal dash.
 *
 * <p>JSON: {@code { "Type": "GolemVoidJumpSlam", "RunTime": 0.05 }}
 *
 * <p>Behavior:
 * <ul>
 *   <li>Resolve attacker (Golem Void boss ref) from context</li>
 *   <li>Find nearest player in same world</li>
 *   <li>Ray-cast down from player's feet up to 5 blocks to find solid ground</li>
 *   <li>If ground found → teleport boss to {@code (playerX, groundY + AIRBORNE_HEIGHT, playerZ)}
 *       — boss appears directly above the target at altitude</li>
 *   <li>Downstream JSON applies a downward {@code ApplyForce} + {@code WaitForGround}
 *       to crash the boss back down exactly where the player is</li>
 *   <li>If no ground within 5 blocks (player over void) → teleport boss to
 *       {@link GenericBossManager.GenericBossState#spawnPosition} (anti-grief)</li>
 * </ul>
 */
public class GolemVoidJumpSlamInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<GolemVoidJumpSlamInteraction> CODEC = BuilderCodec
            .builder(GolemVoidJumpSlamInteraction.class, GolemVoidJumpSlamInteraction::new,
                    SimpleInstantInteraction.CODEC)
            .build();

    /** No distance cap — boss jumps to the nearest player regardless of how far they are
     *  (phase gating + cooldown are handled by the CAE, not by this interaction). */
    private static final double MAX_TELEPORT_DISTANCE_SQ = Double.MAX_VALUE;

    /** Max Y blocks below player's feet to look for ground before declaring "void". */
    private static final int MAX_GROUND_SEARCH_DEPTH = 5;

    /** Height above the player's ground position where the boss materializes at the
     *  jump apex. Gravity + downward ApplyForce (in the JSON) slams it back down. */
    private static final int AIRBORNE_HEIGHT = 15;

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        try {
            EndgameQoL plugin = EndgameQoL.getInstance();
            if (plugin == null) return;

            Ref<EntityStore> attackerRef = context.getEntity();
            if (attackerRef == null || !attackerRef.isValid()) return;

            GenericBossManager mgr = plugin.getSystemRegistry().getGenericBossManager();
            if (mgr == null) return;

            GenericBossManager.GenericBossState state = mgr.getBossState(attackerRef);
            if (state == null) return;

            Store<EntityStore> attackerStore = attackerRef.getStore();
            World world = attackerStore.getExternalData().getWorld();
            if (world == null) return;

            // Boss current position
            TransformComponent bossTransform = attackerStore.getComponent(attackerRef,
                    TransformComponent.getComponentType());
            if (bossTransform == null) return;
            Vector3d bossPos = bossTransform.getPosition();
            if (bossPos == null) return;

            // Find nearest player in same world within MAX_TELEPORT_DISTANCE
            Vector3d nearestPlayerPos = null;
            double nearestSq = MAX_TELEPORT_DISTANCE_SQ;
            for (PlayerRef pr : Universe.get().getPlayers()) {
                if (pr == null) continue;
                Ref<EntityStore> playerEntity = pr.getReference();
                if (playerEntity == null || !playerEntity.isValid()) continue;
                if (playerEntity.getStore() != attackerStore) continue;

                TransformComponent pt = attackerStore.getComponent(playerEntity,
                        TransformComponent.getComponentType());
                if (pt == null) continue;
                Vector3d pp = pt.getPosition();
                if (pp == null) continue;

                double dx = pp.x - bossPos.x;
                double dz = pp.z - bossPos.z;
                double dsq = dx * dx + dz * dz;
                if (dsq < nearestSq) {
                    nearestSq = dsq;
                    nearestPlayerPos = pp;
                }
            }

            if (nearestPlayerPos == null) return; // no player in range → no-op

            // Void-safety ray-cast: find first solid block below player's feet
            Vector3d landing = computeSafeLanding(world, nearestPlayerPos, state.spawnPosition);
            if (landing == null) return;

            // Teleport on the boss world's thread (the engine's teleportPosition handles NaN guards)
            final Vector3d target = landing;
            final Store<EntityStore> finalStore = attackerStore;
            world.execute(() -> {
                try {
                    TransformComponent tc = finalStore.getComponent(attackerRef,
                            TransformComponent.getComponentType());
                    if (tc != null) tc.teleportPosition(target);
                } catch (Exception e) {
                    plugin.getLogger().atFine().log("[GolemVoidJumpSlam] Teleport failed: %s", e.getMessage());
                }
            });

        } catch (Exception e) {
            EndgameQoL plugin = EndgameQoL.getInstance();
            if (plugin != null) {
                plugin.getLogger().atFine().log("[GolemVoidJumpSlam] Error: %s", e.getMessage());
            }
        }
    }

    /**
     * Scan downward from the player's feet looking for solid ground. If found within
     * {@link #MAX_GROUND_SEARCH_DEPTH} blocks, return a point {@link #AIRBORNE_HEIGHT}
     * blocks ABOVE that ground (boss materializes in mid-air above the target). A
     * downward ApplyForce + WaitForGround in the JSON chain then slams it back down.
     * If no ground is found within the search depth, fall back to the spawn position
     * (anti-grief — player is hovering over the void).
     */
    private static Vector3d computeSafeLanding(World world, Vector3d playerPos, Vector3d spawnFallback) {
        int px = (int) Math.floor(playerPos.x);
        int pz = (int) Math.floor(playerPos.z);
        int startY = (int) Math.floor(playerPos.y);

        for (int i = 0; i <= MAX_GROUND_SEARCH_DEPTH; i++) {
            int y = startY - i;
            try {
                BlockType bt = world.getBlockType(px, y, pz);
                if (bt != null && !"Empty".equals(bt.getId())) {
                    // Materialize AIRBORNE_HEIGHT blocks above the ground (not the player's feet)
                    return new Vector3d(playerPos.x, y + 1.0 + AIRBORNE_HEIGHT, playerPos.z);
                }
            } catch (Exception ignored) {}
        }

        // Void detected — fallback to spawn (no airborne lift needed there)
        return spawnFallback != null ? spawnFallback.clone() : null;
    }
}
