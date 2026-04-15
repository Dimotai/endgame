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

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * EndgameQoL's implementation of WaveArenaCallbacks.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>HUD lifecycle: countdown, wave-active (live mob counter), between-wave interval
 *       (live seconds countdown), complete, failed.</li>
 *   <li>Scheduled refresh tasks for dynamic countdowns (swap the HUD each second).</li>
 *   <li>Per-player wave state: current wave index, total waves, mobs alive / spawned
 *       — drives the HUD mob counter.</li>
 *   <li>XP rewards, bounty hooks, chat messages, item drops.</li>
 *   <li>Public {@link #clearWaveHud} for external callers (world transfer, disconnect).</li>
 * </ul>
 */
public class EndgameWaveCallbacks implements WaveArenaCallbacks {

    private final EndgameQoL plugin;
    private final Map<UUID, WaveAnnouncementHud> activeHuds = new ConcurrentHashMap<>();
    private final Map<UUID, WaveState> activeState = new ConcurrentHashMap<>();
    /** Per-player scheduled refresh (countdown or interval). Cancelled on wave start / cleanup. */
    private final Map<UUID, ScheduledFuture<?>> scheduledRefresh = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService HUD_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "EndgameQoL-WaveHUD");
                t.setDaemon(true);
                return t;
            });

    public EndgameWaveCallbacks(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    /** Mutable per-player state driving the live HUD. */
    private static final class WaveState {
        final String arenaId;
        final String arenaName;
        volatile int currentWave;
        volatile int totalWaves;
        volatile int totalMobsInWave;
        volatile int killedMobs;

        WaveState(String arenaId, String arenaName, int totalWaves) {
            this.arenaId = arenaId;
            this.arenaName = arenaName;
            this.totalWaves = totalWaves;
        }
    }

    // ─── HUD attach / detach ──────────────────────────────────────────────

    private void attachHud(UUID playerUuid, PlayerRef pr, WaveAnnouncementHud hud) {
        try {
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) return;
            var store = ref.getStore();
            World world = store.getExternalData().getWorld();
            // Always route HudManager calls through the world thread — calls from the
            // scheduler daemon thread can silently race with engine packet dispatch.
            Runnable dispatch = () -> {
                try {
                    if (!ref.isValid()) return;
                    Player player = ref.getStore().getComponent(ref, Player.getComponentType());
                    if (player == null) return;
                    player.getHudManager().setCustomHud(pr, hud);
                    activeHuds.put(playerUuid, hud);
                } catch (Exception inner) {
                    plugin.getLogger().atWarning().log("[WaveHUD] dispatch error: %s", inner.getMessage());
                }
            };
            if (world != null && world.isAlive()) {
                world.execute(dispatch);
            } else {
                dispatch.run();
            }
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[WaveHUD] Failed to attach HUD: %s", e.getMessage());
        }
    }

    private void removeHud(UUID playerUuid) {
        WaveAnnouncementHud hud = activeHuds.remove(playerUuid);
        if (hud == null) return;
        try {
            PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
            if (pr == null) return;
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) return;
            var store = ref.getStore();
            World world = store.getExternalData().getWorld();
            Runnable dispatch = () -> {
                try {
                    if (!ref.isValid()) return;
                    Player player = ref.getStore().getComponent(ref, Player.getComponentType());
                    if (player != null) player.getHudManager().setCustomHud(pr, null);
                } catch (Exception ignored) {}
            };
            if (world != null && world.isAlive()) {
                world.execute(dispatch);
            } else {
                dispatch.run();
            }
        } catch (Exception ignored) {}
    }

    private void cancelRefresh(UUID playerUuid) {
        ScheduledFuture<?> f = scheduledRefresh.remove(playerUuid);
        if (f != null) f.cancel(false);
    }

    /**
     * Publicly callable — used by {@code EventRegistry.onPlayerLeaveWorld} and any other
     * external path that needs to proactively wipe a player's wave HUD + scheduled task
     * (e.g. world transfer mid-arena). Safe to call even if no HUD is active.
     */
    public void clearWaveHud(UUID playerUuid) {
        if (playerUuid == null) return;
        cancelRefresh(playerUuid);
        activeState.remove(playerUuid);
        removeHud(playerUuid);
    }

    // ─── Callbacks ─────────────────────────────────────────────────────────

    @Override
    public void onCountdown(@Nonnull UUID playerUuid, @Nonnull String arenaId, int secondsRemaining) {
        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
        if (pr == null) return;
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        String name = config != null ? config.getDisplayName() : arenaId;
        int totalWaves = config != null ? config.getWaveCount() : 1;

        // Seed state (next waveStart will increment currentWave to 0 → displayed as "1")
        WaveState state = new WaveState(arenaId, name, totalWaves);
        state.currentWave = -1; // pre-first-wave
        activeState.put(playerUuid, state);

        attachHud(playerUuid, pr, WaveAnnouncementHud.countdown(pr, name, secondsRemaining));
        scheduleCountdownTick(playerUuid, secondsRemaining);
    }

    private void scheduleCountdownTick(UUID playerUuid, int initialSeconds) {
        cancelRefresh(playerUuid);
        ScheduledFuture<?> f = HUD_SCHEDULER.scheduleAtFixedRate(new Runnable() {
            int remaining = initialSeconds - 1;
            @Override public void run() {
                try {
                    if (remaining <= 0) {
                        cancelRefresh(playerUuid);
                        return;
                    }
                    WaveState state = activeState.get(playerUuid);
                    if (state == null) { cancelRefresh(playerUuid); return; }
                    PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
                    if (pr == null) { cancelRefresh(playerUuid); return; }
                    attachHud(playerUuid, pr, WaveAnnouncementHud.countdown(pr, state.arenaName, remaining));
                    remaining--;
                } catch (Exception ignored) {}
            }
        }, 1, 1, TimeUnit.SECONDS);
        scheduledRefresh.put(playerUuid, f);
    }

    @Override
    public void onWaveStart(@Nonnull UUID playerUuid, @Nonnull String arenaId, int waveIndex, int totalWaves) {
        cancelRefresh(playerUuid); // stop any countdown / interval ticker

        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
        if (pr == null) return;
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        String name = config != null ? config.getDisplayName() : arenaId;

        WaveState state = activeState.computeIfAbsent(playerUuid,
                k -> new WaveState(arenaId, name, totalWaves));
        state.currentWave = waveIndex;
        state.totalWaves = totalWaves;
        state.totalMobsInWave = 0;  // will be populated by onMobSpawned
        state.killedMobs = 0;

        // Brief "FIGHT!" banner — will be replaced by waveActive as mobs spawn
        attachHud(playerUuid, pr, WaveAnnouncementHud.waveStart(pr, name, waveIndex, totalWaves));
    }

    @Override
    public void onWaveClear(@Nonnull UUID playerUuid, @Nonnull String arenaId, int waveIndex, int totalWaves) {
        cancelRefresh(playerUuid);

        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
        if (pr == null) return;
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        String name = config != null ? config.getDisplayName() : arenaId;
        int interval = config != null ? config.getIntervalSeconds() : 8;

        if (waveIndex + 1 < totalWaves) {
            attachHud(playerUuid, pr, WaveAnnouncementHud.waveClear(pr, name, waveIndex, totalWaves, interval));
            scheduleIntervalTick(playerUuid, waveIndex, totalWaves, interval);
        } else if ("Void_Realm_Trial".equals(arenaId)) {
            attachHud(playerUuid, pr, WaveAnnouncementHud.bossIncoming(pr, name));
        }

        // Per-wave XP
        if (config != null && config.isXpPerWave() && config.getXpReward() > 0) {
            awardXp(playerUuid, config.getXpReward(), config.getXpSource());
        }
    }

    private void scheduleIntervalTick(UUID playerUuid, int waveIndex, int totalWaves, int initialSeconds) {
        ScheduledFuture<?> f = HUD_SCHEDULER.scheduleAtFixedRate(new Runnable() {
            int remaining = initialSeconds - 1;
            @Override public void run() {
                try {
                    if (remaining <= 0) {
                        cancelRefresh(playerUuid);
                        return;
                    }
                    WaveState state = activeState.get(playerUuid);
                    if (state == null) { cancelRefresh(playerUuid); return; }
                    PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
                    if (pr == null) { cancelRefresh(playerUuid); return; }
                    attachHud(playerUuid, pr, WaveAnnouncementHud.waveClear(pr, state.arenaName,
                            waveIndex, totalWaves, remaining));
                    remaining--;
                } catch (Exception ignored) {}
            }
        }, 1, 1, TimeUnit.SECONDS);
        scheduledRefresh.put(playerUuid, f);
    }

    @Override
    public void onArenaCompleted(@Nonnull UUID playerUuid, @Nonnull String arenaId, int wavesCleared) {
        cancelRefresh(playerUuid);
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);

        if (pr != null) {
            String name = config != null ? config.getDisplayName() : arenaId;
            attachHud(playerUuid, pr, WaveAnnouncementHud.complete(pr, name));
        }

        if ("Void_Realm_Trial".equals(arenaId)) {
            spawnVoidGolem(playerUuid);
        }

        if (config == null) return;

        if (!config.isXpPerWave() && config.getXpReward() > 0) {
            int tier = config.getBountyTier();
            int xp = tier > 0 ? tier * config.getXpReward() : config.getXpReward();
            awardXp(playerUuid, xp, config.getXpSource());
        }

        if (config.getRewardDropTable() != null && pr != null) {
            giveDropTableRewards(pr, config.getRewardDropTable());
        }

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
        cancelRefresh(playerUuid);

        // DISCONNECT / MANUAL = player is leaving. Skip the fail banner to avoid a
        // stuck HUD on the new world (clear-HUD packet races the world transfer).
        if (reason == FailReason.DISCONNECT || reason == FailReason.MANUAL) {
            activeState.remove(playerUuid);
            removeHud(playerUuid);
            return;
        }

        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
        if (pr == null) { activeState.remove(playerUuid); removeHud(playerUuid); return; }
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        String name = config != null ? config.getDisplayName() : arenaId;
        String msg = switch (reason) {
            case PLAYER_DEATH -> "You died";
            case TIMEOUT -> "Time's up";
            case LEFT_ZONE -> "You left the arena";
            default -> "Challenge failed";
        };
        attachHud(playerUuid, pr, WaveAnnouncementHud.failed(pr, name, msg, wavesCleared + 1));

        // Chat message — chat echo of the fail reason, colored red
        String chatMsg = switch (reason) {
            case PLAYER_DEATH -> name + " failed — you were defeated on wave " + (wavesCleared + 1) + ".";
            case TIMEOUT -> name + " failed — time ran out on wave " + (wavesCleared + 1) + ".";
            case LEFT_ZONE -> name + " failed — you left the arena on wave " + (wavesCleared + 1) + ". Stay inside the ring!";
            default -> name + " failed on wave " + (wavesCleared + 1) + ".";
        };
        try {
            pr.sendMessage(Message.raw(chatMsg).color("#ff4466"));
        } catch (Exception ignored) {}
    }

    @Override
    public void onCleanup(@Nonnull UUID playerUuid) {
        // Synchronous teardown — fires after onArenaCompleted / onArenaFailed.
        cancelRefresh(playerUuid);
        activeState.remove(playerUuid);
        removeHud(playerUuid);
    }

    @Override
    public void onMobSpawned(@Nonnull Ref<EntityStore> npcRef, @Nonnull String npcType,
                              @Nonnull UUID ownerUuid, @Nonnull String arenaId) {
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        if (config != null && config.getMobLevel() > 0 && plugin.isRPGLevelingActive()) {
            try {
                if (npcRef.isValid()) {
                    plugin.getRpgLevelingBridge().setMobLevel(
                            npcRef.getStore(), npcRef, config.getMobLevel());
                }
            } catch (Exception ignored) {}
        }

        WaveState state = activeState.get(ownerUuid);
        if (state == null) return;
        state.totalMobsInWave++;
        refreshActiveWaveHud(ownerUuid, state);
    }

    @Override
    public void onMobKilled(@Nonnull Ref<EntityStore> npcRef, @Nonnull UUID killerUuid,
                             @Nonnull String arenaId) {
        WaveState state = activeState.get(killerUuid);
        if (state == null) return;
        state.killedMobs++;
        refreshActiveWaveHud(killerUuid, state);
    }

    private void refreshActiveWaveHud(UUID playerUuid, WaveState state) {
        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
        if (pr == null) return;
        attachHud(playerUuid, pr, WaveAnnouncementHud.waveActive(pr, state.arenaName,
                state.currentWave, state.totalWaves,
                state.killedMobs, state.totalMobsInWave));
    }

    // ─── Side-effects ──────────────────────────────────────────────────────

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
                    com.hypixel.hytale.math.vector.Vector3d spawnPos =
                            new com.hypixel.hytale.math.vector.Vector3d(1.0, 102.0, -55.0);
                    com.hypixel.hytale.server.npc.NPCPlugin.get().spawnNPC(
                            store, "Endgame_Golem_Void", null, spawnPos,
                            com.hypixel.hytale.math.vector.Vector3f.ZERO);
                    plugin.getLogger().atInfo().log("[VoidRealm] Spawned Endgame_Golem_Void after trial completion");

                    pr.sendMessage(Message.raw("The Void Golem rises from the ruins!").color("#aa44ee"));
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
