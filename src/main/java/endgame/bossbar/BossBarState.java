package endgame.bossbar;

/**
 * Runtime snapshot of a boss's state — passed to {@link BossBarRenderer} each refresh.
 *
 * @param currentPhase    1-indexed current phase (must match a phase in the theme)
 * @param healthPercent   current HP as a 0.0–1.0 fraction
 * @param currentHp       current HP for numeric display (0 = hide numeric)
 * @param maxHp           max HP for numeric display (0 = hide numeric)
 * @param invulnerable    true → show INVULNERABLE badge
 * @param enraged         true → show ENRAGED badge
 */
public record BossBarState(
        int currentPhase,
        float healthPercent,
        int currentHp,
        int maxHp,
        boolean invulnerable,
        boolean enraged) {

    /** Convenience constructor without current/max HP — hides the numeric display. */
    public static BossBarState simple(int currentPhase, float healthPercent,
                                       boolean invulnerable, boolean enraged) {
        return new BossBarState(currentPhase, healthPercent, 0, 0, invulnerable, enraged);
    }
}
