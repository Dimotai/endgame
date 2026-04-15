package endgame.plugin.bossmanager;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier.CalculationType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.BossConfig;
import endgame.plugin.utils.BossType;

/**
 * Golem Void-specific hooks that run alongside the generic boss manager.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Spawn Eye_Void minions on phase transitions (count driven by {@link BossConfig}).</li>
 *   <li>Apply HP scaling to minions via {@link StaticModifier}.</li>
 *   <li>Expose the boss's spawn position for the Jump Slam void-safe fallback.</li>
 * </ul>
 *
 * <p>All methods are static; state lives on the generic manager's boss state object. This
 * keeps the class lightweight and easy to test in isolation.
 */
public final class GolemVoidExtras {

    private static final double MINION_Y_OFFSET = 2.0;
    private static final String EYE_VOID_HEALTH_MODIFIER_KEY = "EndgameQoL.EyeVoidHealth";

    private GolemVoidExtras() {}

    /**
     * Spawn Eye_Void minions around the boss for a given phase.
     *
     * @param plugin    plugin instance (for logger + config)
     * @param bossRef   valid reference to the Golem Void NPC
     * @param store     entity store that owns the boss
     * @param newPhase  1-based phase number (count is looked up in BossConfig)
     */
    public static void spawnMinionsForPhase(EndgameQoL plugin, Ref<EntityStore> bossRef,
                                            Store<EntityStore> store, int newPhase) {
        BossConfig golemCfg = plugin.getConfig().get().getBossConfig(BossType.GOLEM_VOID);
        int minionCount = switch (newPhase) {
            case 2 -> golemCfg.getPhase2MinionCount();
            case 3 -> golemCfg.getPhase3MinionCount();
            default -> 0;
        };
        if (minionCount <= 0) return;

        try {
            if (!bossRef.isValid()) return;
            TransformComponent transform = store.getComponent(bossRef, TransformComponent.getComponentType());
            if (transform == null) return;

            final double bossX = transform.getPosition().getX();
            final double bossY = transform.getPosition().getY();
            final double bossZ = transform.getPosition().getZ();

            final World bossWorld = store.getExternalData().getWorld();
            if (bossWorld == null || !bossWorld.isAlive()) return;

            final double spawnRadius = plugin.getConfig().get().getMinionSpawnRadius();
            final float healthMultiplier = plugin.getConfig().get().getEyeVoidHealthMultiplier();
            final int count = minionCount;

            bossWorld.execute(() -> {
                try {
                    Store<EntityStore> worldStore = bossWorld.getEntityStore().getStore();
                    NPCPlugin npcPlugin = NPCPlugin.get();

                    for (int i = 0; i < count; i++) {
                        double angle = (2.0 * Math.PI * i) / count;
                        double offsetX = Math.cos(angle) * spawnRadius;
                        double offsetZ = Math.sin(angle) * spawnRadius;
                        Vector3d spawnPos = new Vector3d(bossX + offsetX, bossY + MINION_Y_OFFSET, bossZ + offsetZ);
                        Vector3f rotation = new Vector3f(0, (float) Math.toDegrees(angle), 0);

                        var result = npcPlugin.spawnNPC(worldStore, "Eye_Void", null, spawnPos, rotation);
                        if (result != null) {
                            applyEyeVoidHealthScaling(plugin, result.left(), worldStore, healthMultiplier);
                        }
                    }
                    plugin.getLogger().atFine().log("[GolemVoidExtras] Spawned %d Eye_Void for phase %d", count, newPhase);
                } catch (Exception e) {
                    plugin.getLogger().atWarning().log("[GolemVoidExtras] Eye_Void spawn failed: %s", e.getMessage());
                }
            });
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[GolemVoidExtras] Failed to queue Eye_Void spawn: %s", e.getMessage());
        }
    }

    private static void applyEyeVoidHealthScaling(EndgameQoL plugin, Ref<EntityStore> minionRef,
                                                  Store<EntityStore> store, float healthMultiplier) {
        if (minionRef == null || !minionRef.isValid() || healthMultiplier <= 1.0f) return;
        try {
            ComponentType<EntityStore, EntityStatMap> statType = EntityStatMap.getComponentType();
            if (statType == null) return;

            EntityStatMap statMap = store.getComponent(minionRef, statType);
            if (statMap == null) return;

            int healthStat = DefaultEntityStatTypes.getHealth();
            EntityStatValue healthValue = statMap.get(healthStat);
            if (healthValue == null) return;

            float baseMax = healthValue.getMax();
            float additionalHealth = baseMax * (healthMultiplier - 1.0f);

            StaticModifier mod = new StaticModifier(ModifierTarget.MAX, CalculationType.ADDITIVE, additionalHealth);
            statMap.putModifier(healthStat, EYE_VOID_HEALTH_MODIFIER_KEY, mod);
            statMap.addStatValue(healthStat, additionalHealth);
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[GolemVoidExtras] HP scaling failed: %s", e.getMessage());
        }
    }

    /**
     * Capture the current position of the boss as its spawn anchor (used by
     * Jump Slam as a void-safe teleport fallback).
     * Returns null if the position can't be read.
     */
    public static Vector3d captureSpawnPosition(Ref<EntityStore> bossRef, Store<EntityStore> store) {
        try {
            if (!bossRef.isValid()) return null;
            TransformComponent tc = store.getComponent(bossRef, TransformComponent.getComponentType());
            if (tc == null) return null;
            Vector3d p = tc.getPosition();
            return p != null ? p.clone() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
