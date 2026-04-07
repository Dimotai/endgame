package endgame.plugin.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * Native CustomUIHud for the combo meter overlay.
 * Uses Huds/ComboMeterHud.ui — positioned lower-right to avoid portal UI overlap.
 * Timer displayed as text countdown instead of animated bar (Anchor.Width is not settable).
 */
public class ComboMeterHud extends CustomUIHud {

    private static final String[] TIER_COLORS = {"#55ff55", "#ffff55", "#ff8833", "#ff3333"};
    private static final String[] TIER_NAMES = {"x2", "x3", "x4", "FRENZY"};
    private static final String[] TIER_EFFECT_NAMES = {"", "Adrenaline", "Precision", "Bloodlust"};

    private int lastComboCount = -1;
    private int lastTier = -1;
    private String lastTimerText = "";
    private String lastBestText = "";

    public ComboMeterHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append("Huds/ComboMeterHud.ui");
    }

    /**
     * Full rebuild — sets all values and colors for a tier change.
     */
    public void setTier(int comboCount, int tier, int personalBest, boolean newRecord, boolean effectsEnabled) {
        int idx = Math.clamp(tier - 1, 0, TIER_COLORS.length - 1);
        String color = TIER_COLORS[idx];
        String tierName = TIER_NAMES[idx];
        String effectText = effectsEnabled ? TIER_EFFECT_NAMES[idx] : "";
        String tierLine = effectText.isEmpty() ? tierName : tierName + " \u00b7 " + effectText;
        String bestText = newRecord ? "NEW BEST! " + personalBest : "Best: " + personalBest;
        String bestColor = newRecord ? "#ffd700" : "#777777";

        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#ComboAccent.Background.Color", color);
        cmd.set("#ComboCount.Style.TextColor", color);
        cmd.set("#ComboCount.Text", String.valueOf(comboCount));
        cmd.set("#ComboTier.Style.TextColor", color);
        cmd.set("#ComboTier.Text", tierLine);
        cmd.set("#ComboTimer.Style.TextColor", color);
        cmd.set("#ComboTimer.Text", "");
        cmd.set("#ComboBest.Style.TextColor", bestColor);
        cmd.set("#ComboBest.Text", bestText);
        update(false, cmd);

        lastComboCount = comboCount;
        lastTier = tier;
        lastTimerText = "";
        lastBestText = bestText;
    }

    /**
     * Incremental refresh — only sends changed values (called every 200ms).
     */
    public void refresh(int comboCount, int tier, float timeRemaining, int personalBest,
                        boolean newRecord, boolean effectsEnabled) {
        UICommandBuilder cmd = new UICommandBuilder();
        boolean hasChanges = false;

        if (comboCount != lastComboCount) {
            cmd.set("#ComboCount.Text", String.valueOf(comboCount));
            lastComboCount = comboCount;
            hasChanges = true;
        }

        if (tier != lastTier) {
            int idx = Math.clamp(tier - 1, 0, TIER_COLORS.length - 1);
            String color = TIER_COLORS[idx];
            String tierName = TIER_NAMES[idx];
            String effectText = effectsEnabled ? TIER_EFFECT_NAMES[idx] : "";
            String tierLine = effectText.isEmpty() ? tierName : tierName + " \u00b7 " + effectText;

            cmd.set("#ComboAccent.Background.Color", color);
            cmd.set("#ComboCount.Style.TextColor", color);
            cmd.set("#ComboTier.Style.TextColor", color);
            cmd.set("#ComboTier.Text", tierLine);
            cmd.set("#ComboTimer.Style.TextColor", color);
            lastTier = tier;
            hasChanges = true;
        }

        // Timer text (e.g. "4.2s")
        String timerText = String.format("%.1fs", timeRemaining * 5); // 5s default timer
        if (!timerText.equals(lastTimerText)) {
            cmd.set("#ComboTimer.Text", timerText);
            lastTimerText = timerText;
            hasChanges = true;
        }

        String bestText = newRecord ? "NEW BEST! " + personalBest : "Best: " + personalBest;
        if (!bestText.equals(lastBestText)) {
            String bestColor = newRecord ? "#ffd700" : "#777777";
            cmd.set("#ComboBest.Style.TextColor", bestColor);
            cmd.set("#ComboBest.Text", bestText);
            lastBestText = bestText;
            hasChanges = true;
        }

        if (hasChanges) {
            update(false, cmd);
        }
    }
}
