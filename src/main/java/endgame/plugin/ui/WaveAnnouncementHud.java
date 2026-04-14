package endgame.plugin.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * Compact top-center HUD for wave-based encounters (Void Gauntlet → Boss).
 *
 * <p>State is baked into the HUD instance at construction time, so {@link #build(UICommandBuilder)}
 * constructs the DOM + applies the initial text/colors in a single atomic packet.
 * This avoids the race condition where a post-attach {@code update()} targets a DOM
 * that the client has not finished building, which was causing "Selected element not found"
 * crashes + HUD teardowns between waves.
 *
 * <p>Callers get the HUD visible by calling
 * {@code player.getHudManager().setCustomHud(pr, new WaveAnnouncementHud(...))}.
 * Subsequent state changes = new instance replaces the old one via setCustomHud.
 */
public class WaveAnnouncementHud extends CustomUIHud {

    public static final String COLOR_VOID    = "#aa44ee";
    public static final String COLOR_FIGHT   = "#ff4466";
    public static final String COLOR_CLEAR   = "#55ff88";
    public static final String COLOR_GOLD    = "#ffd27a";
    public static final String COLOR_FAIL    = "#ff3344";
    public static final String COLOR_MUTE    = "#8855bb";
    public static final String COLOR_WHITE   = "#ffffff";

    private final String subtitle;
    private final String title;
    private final String detail;
    private final String subtitleColor;
    private final String titleColor;
    private final String detailColor;

    private WaveAnnouncementHud(@Nonnull PlayerRef playerRef,
                                String subtitle, String title, String detail,
                                String subtitleColor, String titleColor, String detailColor) {
        super(playerRef);
        this.subtitle = subtitle;
        this.title = title;
        this.detail = detail;
        this.subtitleColor = subtitleColor;
        this.titleColor = titleColor;
        this.detailColor = detailColor;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder cmd) {
        cmd.append("Huds/WaveAnnouncementHud.ui");
        // Apply state in the SAME packet as DOM construction (atomic, race-free)
        cmd.set("#WaveTopAccent.Background.Color", titleColor);
        cmd.set("#WaveSubtitle.Text", subtitle);
        cmd.set("#WaveSubtitle.Style.TextColor", subtitleColor);
        cmd.set("#WaveTitle.Text", title);
        cmd.set("#WaveTitle.Style.TextColor", titleColor);
        cmd.set("#WaveDetail.Text", detail);
        cmd.set("#WaveDetail.Style.TextColor", detailColor);
    }

    // ─── Factory methods (one per state) ──────────────────────────────────

    public static WaveAnnouncementHud countdown(@Nonnull PlayerRef pr, @Nonnull String arenaName, int secondsRemaining) {
        return new WaveAnnouncementHud(pr,
                arenaName.toUpperCase(),
                "GET READY",
                "Starting in " + secondsRemaining + "s",
                COLOR_MUTE, COLOR_VOID, COLOR_WHITE);
    }

    public static WaveAnnouncementHud waveStart(@Nonnull PlayerRef pr, @Nonnull String arenaName, int waveIndex, int totalWaves) {
        return new WaveAnnouncementHud(pr,
                arenaName.toUpperCase(),
                "WAVE " + (waveIndex + 1) + " / " + totalWaves,
                "FIGHT !",
                COLOR_MUTE, COLOR_FIGHT, COLOR_WHITE);
    }

    public static WaveAnnouncementHud waveClear(@Nonnull PlayerRef pr, @Nonnull String arenaName, int waveIndex, int totalWaves, int nextWaveSeconds) {
        return new WaveAnnouncementHud(pr,
                arenaName.toUpperCase(),
                "WAVE " + (waveIndex + 1) + " CLEARED",
                "Next wave in " + nextWaveSeconds + "s",
                COLOR_MUTE, COLOR_CLEAR, COLOR_CLEAR);
    }

    public static WaveAnnouncementHud bossIncoming(@Nonnull PlayerRef pr, @Nonnull String arenaName) {
        return new WaveAnnouncementHud(pr,
                arenaName.toUpperCase(),
                "THE GOLEM AWAKENS",
                "Prepare for battle...",
                COLOR_MUTE, COLOR_FAIL, COLOR_FAIL);
    }

    public static WaveAnnouncementHud complete(@Nonnull PlayerRef pr, @Nonnull String arenaName) {
        return new WaveAnnouncementHud(pr,
                arenaName.toUpperCase(),
                "CHALLENGE COMPLETE",
                "Rewards incoming...",
                COLOR_GOLD, COLOR_GOLD, COLOR_GOLD);
    }

    public static WaveAnnouncementHud failed(@Nonnull PlayerRef pr, @Nonnull String arenaName, @Nonnull String reason, int waveReached) {
        return new WaveAnnouncementHud(pr,
                arenaName.toUpperCase(),
                reason.toUpperCase(),
                "Wave " + waveReached + " reached",
                COLOR_FAIL, COLOR_FAIL, COLOR_FAIL);
    }
}
