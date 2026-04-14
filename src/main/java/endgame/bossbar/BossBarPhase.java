package endgame.bossbar;

/**
 * One phase of a boss encounter.
 *
 * @param number      1-indexed phase number (1, 2, 3...)
 * @param name        display name (e.g. "Awakened", "Enraged", "FURY")
 * @param textColor   hex color for the phase subtitle text (e.g. "#c8bfff")
 * @param thresholdHp HP percent at which this phase STARTS (1.0 for phase 1,
 *                    then decreasing: e.g. 0.67, 0.33). Used to draw phase
 *                    boundary markers on the HP bar.
 */
public record BossBarPhase(int number, String name, String textColor, float thresholdHp) {
}
