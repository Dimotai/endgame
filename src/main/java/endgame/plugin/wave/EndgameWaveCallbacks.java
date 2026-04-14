package endgame.plugin.wave;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.ui.WaveAnnouncementHud;
import endgame.plugin.utils.PlayerRefCache;
import endgame.wavearena.WaveArenaCallbacks;
import endgame.wavearena.WaveArenaConfig;
import endgame.wavearena.WaveArenaEngine;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * EndgameQoL's implementation of WaveArenaCallbacks.
 * Handles XP rewards, bounty hooks, chat messages, sounds, item drops.
 */
public class EndgameWaveCallbacks implements WaveArenaCallbacks {

    private final EndgameQoL plugin;
    private final Map<UUID, WaveAnnouncementHud> activeHuds = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService HUD_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "EndgameQoL-WaveHUD");
                t.setDaemon(true);
                return t;
            });

    public EndgameWaveCallbacks(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    /**
     * Attaches a pre-built HUD to the player. The HUD's state is baked in at
     * construction time (see WaveAnnouncementHud factory methods), so the
     * build() call sent to the client is atomic — no race with follow-up updates.
     */
    private void attachHud(UUID playerUuid, PlayerRef pr, WaveAnnouncementHud hud) {
        try {
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) return;
            Player player = ref.getStore().getComponent(ref, Player.getComponentType());
            if (player == null) return;
            player.getHudManager().setCustomHud(pr, hud);
            activeHuds.put(playerUuid, hud);
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[WaveHUD] Failed to attach HUD: %s", e.getMessage());
        }
    }

    /** Detaches the HUD for a player. */
    private void removeHud(UUID playerUuid) {
        WaveAnnouncementHud hud = activeHuds.remove(playerUuid);
        if (hud == null) return;
        try {
            PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
            if (pr == null) return;
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) return;
            Player player = ref.getStore().getComponent(ref, Player.getComponentType());
            if (player != null) player.getHudManager().setCustomHud(pr, null);
        } catch (Exception ignored) {}
    }

    @Override
    public void onCountdown(@Nonnull UUID playerUuid, @Nonnull String arenaId, int secondsRemaining) {
        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
        if (pr == null) return;
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        String name = config != null ? config.getDisplayName() : arenaId;
        attachHud(playerUuid, pr, WaveAnnouncementHud.countdown(pr, name, secondsRemaining));
    }

    @Override
    public void onWaveStart(@Nonnull UUID playerUuid, @Nonnull String arenaId, int waveIndex, int totalWaves) {
        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
        if (pr == null) return;
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        String name = config != null ? config.getDisplayName() : arenaId;
        attachHud(playerUuid, pr, WaveAnnouncementHud.waveStart(pr, name, waveIndex, totalWaves));
    }

    @Override
    public void onWaveClear(@Nonnull UUID playerUuid, @Nonnull String arenaId, int waveIndex, int totalWaves) {
        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
        if (pr == null) return;
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        String name = config != null ? config.getDisplayName() : arenaId;
        int interval = config != null ? config.getIntervalSeconds() : 8;

        if (waveIndex + 1 < totalWaves) {
            attachHud(playerUuid, pr, WaveAnnouncementHud.waveClear(pr, name, waveIndex, totalWaves, interval));
        } else if ("Void_Realm_Trial".equals(arenaId)) {
            // Last wave cleared → boss incoming bridge for Void Gauntlet
            attachHud(playerUuid, pr, WaveAnnouncementHud.bossIncoming(pr, name));
        }

        // Per-wave XP
        if (config != null && config.isXpPerWave() && config.getXpReward() > 0) {
            awardXp(playerUuid, config.getXpReward(), config.getXpSource());
        }
    }

    @Override
    public void onArenaCompleted(@Nonnull UUID playerUuid, @Nonnull String arenaId, int wavesCleared) {
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);

        // HUD complete banner — onCleanup (called right after by the engine) handles removal
        if (pr != null) {
            String name = config != null ? config.getDisplayName() : arenaId;
            attachHud(playerUuid, pr, WaveAnnouncementHud.complete(pr, name));
        }

        // Void Realm Trial — spawn the Void Golem after the final wave is cleared
        if ("Void_Realm_Trial".equals(arenaId)) {
            spawnVoidGolem(playerUuid);
        }

        if (config == null) return;

        // XP (completion-based, not per-wave)
        if (!config.isXpPerWave() && config.getXpReward() > 0) {
            int tier = config.getBountyTier();
            int xp = tier > 0 ? tier * config.getXpReward() : config.getXpReward();
            awardXp(playerUuid, xp, config.getXpSource());
        }

        // Drop table rewards
        if (config.getRewardDropTable() != null && pr != null) {
            giveDropTableRewards(pr, config.getRewardDropTable());
        }

        // Bounty hook
        if (config.getBountyHook() != null && config.getBountyTier() > 0) {
            try {
                var bounty = plugin.getBountyManager();
                if (bounty != null) {
                    bounty.onTrialComplete(playerUuid, config.getBountyTier());
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onArenaFailed(@Nonnull UUID playerUuid, @Nonnull String arenaId,
                               int wavesCleared, @Nonnull FailReason reason) {
        // DISCONNECT / MANUAL = player is leaving (/leave, world transfer, disconnect).
        // Showing a fail HUD that immediately gets cleaned up causes a visual "stuck HUD"
        // on the client because the new-HUD packet and the clear-HUD packet race through
        // the world transfer. Skip the HUD entirely for those cases.
        if (reason == FailReason.DISCONNECT || reason == FailReason.MANUAL) {
            removeHud(playerUuid);
            return;
        }

        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
        if (pr == null) { removeHud(playerUuid); return; }
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        String name = config != null ? config.getDisplayName() : arenaId;
        String msg = switch (reason) {
            case PLAYER_DEATH -> "You died";
            case TIMEOUT -> "Time's up";
            default -> "Challenge failed";
        };
        attachHud(playerUuid, pr, WaveAnnouncementHud.failed(pr, name, msg, wavesCleared + 1));
        // onCleanup (called right after) removes the HUD synchronously
    }

    @Override
    public void onCleanup(@Nonnull UUID playerUuid) {
        // Synchronous HUD teardown — prevents a stuck HUD when the player leaves
        // the world mid-arena. Also fires right after onArenaCompleted / onArenaFailed,
        // so the final banner is only briefly visible (chat hint compensates).
        removeHud(playerUuid);
    }

    @Override
    public void onMobSpawned(@Nonnull Ref<EntityStore> npcRef, @Nonnull String npcType,
                              @Nonnull UUID ownerUuid, @Nonnull String arenaId) {
        // Set RPG Leveling mob level if active
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        if (config != null && config.getMobLevel() > 0 && plugin.isRPGLevelingActive()) {
            try {
                Ref<EntityStore> ref = npcRef;
                if (ref.isValid()) {
                    plugin.getRpgLevelingBridge().setMobLevel(
                            ref.getStore(), ref, config.getMobLevel());
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onMobKilled(@Nonnull Ref<EntityStore> npcRef, @Nonnull UUID killerUuid,
                             @Nonnull String arenaId) {
        // No per-kill logic needed currently — death tracking is engine-side
    }

    /** Spawns the Void Golem on the main island after the Void Realm Trial is beaten. */
    private void spawnVoidGolem(UUID playerUuid) {
        try {
            PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
            if (pr == null) return;
            Ref<EntityStore> pRef = pr.getReference();
            if (pRef == null || !pRef.isValid()) return;
            var store = pRef.getStore();
            World world = store.getExternalData().getWorld();
            if (world == null || !world.isAlive()) return;

            world.execute(() -> {
                try {
                    // Spawn at island center
                    com.hypixel.hytale.math.vector.Vector3d spawnPos =
                            new com.hypixel.hytale.math.vector.Vector3d(1.0, 102.0, -55.0);
                    com.hypixel.hytale.server.npc.NPCPlugin.get().spawnNPC(
                            store, "Endgame_Golem_Void", null, spawnPos,
                            com.hypixel.hytale.math.vector.Vector3f.ZERO);
                    plugin.getLogger().atInfo().log("[VoidRealm] Spawned Endgame_Golem_Void after trial completion");

                    if (pr != null) {
                        pr.sendMessage(Message.raw("The Void Golem rises from the ruins!").color("#aa44ee"));
                    }
                } catch (Exception e) {
                    plugin.getLogger().atWarning().log("[VoidRealm] Golem spawn failed: %s", e.getMessage());
                }
            });
        } catch (Exception ignored) {}
    }

    private void awardXp(UUID playerUuid, int xp, String source) {
        try {
            if (plugin.isRPGLevelingActive()) {
                plugin.getRpgLevelingBridge().addXP(playerUuid, xp, source);
            }
            if (plugin.isEndlessLevelingActive()) {
                plugin.getEndlessLevelingBridge().addXP(playerUuid, xp, source);
            }
        } catch (Exception ignored) {}
    }

    private void giveDropTableRewards(PlayerRef playerRef, String dropTable) {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return;
            World world = ref.getStore().getExternalData().getWorld();
            if (world == null || !world.isAlive()) return;

            world.execute(() -> {
                try {
                    Ref<EntityStore> pRef = playerRef.getReference();
                    if (pRef == null || !pRef.isValid()) return;
                    var store = pRef.getStore();
                    var player = store.getComponent(pRef,
                            com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
                    if (player == null) return;

                    List<ItemStack> rewards = ItemModule.get().getRandomItemDrops(dropTable);
                    if (rewards == null) return;

                    for (ItemStack item : rewards) {
                        var tx = player.giveItem(item, pRef, store);
                        if (tx.getRemainder() != null && !tx.getRemainder().isEmpty()) {
                            com.hypixel.hytale.server.core.entity.ItemUtils.dropItem(pRef, tx.getRemainder(), store);
                        }
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }
}
