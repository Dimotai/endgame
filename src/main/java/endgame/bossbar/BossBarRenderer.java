package endgame.bossbar;

/**
 * Pure HTML renderer for boss health bars. Stateless — takes a theme + state
 * snapshot and returns HyUI-compatible HTML.
 *
 * <p>Layout:
 * <pre>
 *              BOSS NAME
 *          Phase 2 — Enraged
 *   ━━━━━━━━━━│━━━━━━━━━━━│━━━━━━━      (solid fill + top highlight + phase markers)
 *          1,200 / 1,800 HP  •  67%
 *                   ENRAGED
 * </pre>
 *
 * <p>Bar width/height and other visual constants are tuned in
 * {@link #BAR_WIDTH}/{@link #BAR_HEIGHT}. The {@code hp-bar-fill},
 * {@code hp-bar-highlight}, {@code phase-text} and {@code hp-numeric} element
 * IDs are part of the public contract — refresh handlers query these by ID.
 */
public final class BossBarRenderer {

    // Public layout constants — refresh handlers must use these for incremental updates.
    public static final int BAR_WIDTH = 500;
    public static final int BAR_HEIGHT = 12;
    public static final int HIGHLIGHT_HEIGHT = 4;

    // Element IDs (public contract for onRefresh handlers)
    public static final String ID_BOSS_NAME    = "boss-name";
    public static final String ID_PHASE_TEXT   = "phase-text";
    public static final String ID_HP_BAR_BG    = "hp-bar-bg";
    public static final String ID_HP_BAR_FILL  = "hp-bar-fill";
    public static final String ID_HP_BAR_HL    = "hp-bar-highlight";
    public static final String ID_HP_NUMERIC   = "hp-numeric";
    public static final String ID_STATUS_TEXT  = "status-text";

    private static final int MARKER_W = 2;
    private static final int MARKER_EXTRA = 3;
    private static final String MARKER_COLOR = "#ffdd55(0.95)";

    private BossBarRenderer() {}

    /** Render the bar HTML for the given theme and runtime state. */
    public static String render(BossBarTheme theme, BossBarState state) {
        BossBarPhase phase = theme.phase(state.currentPhase());
        boolean multiPhase = theme.phases().size() > 1;
        String phaseSubtitle = multiPhase
                ? "Phase " + phase.number() + " - " + phase.name()
                : "";

        int healthPct = Math.round(Math.max(0f, Math.min(1f, state.healthPercent())) * 100);
        int fillWidth = Math.max(0, Math.min(BAR_WIDTH, Math.round(BAR_WIDTH * state.healthPercent())));

        StringBuilder markers = new StringBuilder();
        for (float t : theme.markerThresholds()) {
            if (t <= 0f || t >= 1f) continue;
            int xPx = Math.round(BAR_WIDTH * t) - (MARKER_W / 2);
            markers.append(String.format(
                    "<div style=\"anchor-width: %d; anchor-height: %d; " +
                    "background-color: %s; anchor-left: %d; anchor-top: -%d;\"></div>",
                    MARKER_W, BAR_HEIGHT + MARKER_EXTRA * 2, MARKER_COLOR, xPx, MARKER_EXTRA));
        }

        String statusText = "";
        String statusColor = "#ffffff";
        if (state.enraged() && state.invulnerable()) {
            statusText = "ENRAGED \u2022 INVULNERABLE"; statusColor = "#ff6644";
        } else if (state.enraged()) {
            statusText = "ENRAGED"; statusColor = "#ff6644";
        } else if (state.invulnerable()) {
            statusText = "INVULNERABLE"; statusColor = "#d9a9ff";
        }
        String statusLine = statusText.isEmpty() ? ""
                : String.format("<p id=\"%s\" style=\"font-size: 10; font-weight: bold; " +
                        "color: %s; text-align: center; anchor-height: 12; anchor-width: 100%%; margin-top: 4;\">%s</p>",
                        ID_STATUS_TEXT, statusColor, statusText);

        String hpNumeric = (state.maxHp() > 0)
                ? String.format("%s / %s HP  \u2022  %d%%",
                        formatNumber(state.currentHp()), formatNumber(state.maxHp()), healthPct)
                : healthPct + "%";

        // Multi-phase bosses: name → divider → phase text → bar
        // Single-phase elites:  name → bar (no divider, no phase text)
        String subtitleBlock = multiPhase
                ? String.format(
                        "<div id=\"name-divider\" style=\"anchor-width: 280; anchor-height: 1; " +
                        "background-color: %s(0.55); horizontal-align: center; margin-top: 3;\"></div>" +
                        "<p id=\"%s\" style=\"font-size: 11; font-style: italic; color: %s; " +
                        "text-align: center; anchor-height: 13; anchor-width: 100%%; margin-top: 3;\">%s</p>",
                        theme.nameColor(), ID_PHASE_TEXT, phase.textColor(), phaseSubtitle)
                : "";

        int containerHeight = multiPhase ? 104 : 76;
        int barMarginTop = multiPhase ? 10 : 14;  // more breathing room when no subtitle

        return String.format("""
                <style>
                    #boss-bar-container {
                        anchor-top: 24;
                        horizontal-align: center;
                        anchor-width: 540;
                        anchor-height: %d;
                        padding: 2;
                        layout-mode: Top;
                    }
                    #%s {
                        font-size: 22;
                        font-weight: bold;
                        letter-spacing: 3;
                        color: %s;
                        text-outline-color: #000000;
                        text-align: center;
                        anchor-height: 26;
                        anchor-width: 100%%;
                    }
                    #%s {
                        anchor-width: %d;
                        anchor-height: %d;
                        background-color: #000000(0.75);
                        horizontal-align: center;
                        margin-top: %d;
                    }
                    #%s {
                        anchor-width: %d;
                        anchor-height: %d;
                        background-color: %s;
                        anchor-left: 0;
                        anchor-top: 0;
                    }
                    #%s {
                        anchor-width: %d;
                        anchor-height: %d;
                        background-color: #ffffff(0.22);
                        anchor-left: 0;
                        anchor-top: 0;
                    }
                    #%s {
                        font-size: 12;
                        color: #dddddd;
                        text-align: center;
                        anchor-height: 14;
                        anchor-width: 100%%;
                        margin-top: 6;
                    }
                </style>
                <div id="boss-bar-container">
                    <p id="%s">%s</p>
                    %s
                    <div id="%s">
                        <div id="%s"></div>
                        <div id="%s"></div>
                        %s
                    </div>
                    <p id="%s">%s</p>
                    %s
                </div>
                """,
                // Style block substitutions
                containerHeight,
                ID_BOSS_NAME,   theme.nameColor(),
                ID_HP_BAR_BG,   BAR_WIDTH, BAR_HEIGHT, barMarginTop,
                ID_HP_BAR_FILL, fillWidth, BAR_HEIGHT, theme.barColor(),
                ID_HP_BAR_HL,   fillWidth, HIGHLIGHT_HEIGHT,
                ID_HP_NUMERIC,
                // Body substitutions
                ID_BOSS_NAME,   theme.displayName(),
                subtitleBlock,
                ID_HP_BAR_BG,
                ID_HP_BAR_FILL,
                ID_HP_BAR_HL,
                markers.toString(),
                ID_HP_NUMERIC,  hpNumeric,
                statusLine
        );
    }

    /** Format an integer with thousands separators (e.g. 12345 → "12,345"). */
    public static String formatNumber(int n) {
        if (n < 1000) return String.valueOf(n);
        String s = String.valueOf(n);
        StringBuilder r = new StringBuilder();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) r.append(',');
            r.append(s.charAt(i));
        }
        return r.toString();
    }
}
