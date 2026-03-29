package endgame.plugin.services;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import endgame.plugin.EndgameQoL;
import endgame.plugin.events.domain.GameEvent;
import endgame.plugin.events.domain.GameEventBus;

import java.util.UUID;

/**
 * Sound feedback service — plays vanilla sound effects in response to game events.
 * Subscribes to the GameEventBus and plays appropriate sounds via SoundUtil.
 *
 * All sounds are vanilla Hytale SFX IDs — no custom audio files needed.
 */
public final class SoundService {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.Sound");

    // Sound IDs — resolved to indices at registration time
    private static final String SOUND_BOSS_KILL = "SFX_MemoryRestored";
    private static final String SOUND_ACHIEVEMENT = "SFX_MemoryMote";
    private static final String SOUND_COMBO_FRENZY = "SFX_Avatar_Powers_Enable";
    private static final String SOUND_DUNGEON_ENTER = "SFX_Discovery_Z4_Medium";

    private SoundService() {}

    /**
     * Register all sound event listeners on the bus.
     * Called once during plugin setup.
     */
    public static void register(EndgameQoL plugin, GameEventBus bus) {
        bus.subscribe(GameEvent.BossKillEvent.class, event -> {
            int idx = resolveSoundIndex(SOUND_BOSS_KILL);
            if (idx == 0) return;
            if (event.killerUuid() != null) {
                playToPlayer(event.killerUuid(), idx);
            }
        });

        bus.subscribe(GameEvent.AchievementUnlockEvent.class, event -> {
            int idx = resolveSoundIndex(SOUND_ACHIEVEMENT);
            if (idx != 0) playToPlayer(event.playerUuid(), idx);
        });

        bus.subscribe(GameEvent.ComboTierChangeEvent.class, event -> {
            if (event.newTier() >= 4) { // FRENZY tier
                int idx = resolveSoundIndex(SOUND_COMBO_FRENZY);
                if (idx != 0) playToPlayer(event.playerUuid(), idx);
            }
        });

        bus.subscribe(GameEvent.DungeonEnterEvent.class, event -> {
            int idx = resolveSoundIndex(SOUND_DUNGEON_ENTER);
            if (idx != 0) playToPlayer(event.playerUuid(), idx);
        });

        LOGGER.atInfo().log("[SoundService] Registered sound event listeners");
    }

    private static int resolveSoundIndex(String sfxId) {
        return SoundEvent.getAssetMap().getIndex(sfxId);
    }

    private static void playToPlayer(UUID playerUuid, int soundIndex) {
        try {
            for (PlayerRef pr : Universe.get().getPlayers()) {
                if (pr != null && playerUuid.equals(pr.getUuid())) {
                    SoundUtil.playSoundEvent2dToPlayer(pr, soundIndex, SoundCategory.SFX);
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.atFine().log("[Sound] Failed to play sound for %s: %s", playerUuid, e.getMessage());
        }
    }
}
