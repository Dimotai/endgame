package endgame.bossbar;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-mod focus coordination for boss bars. Tracks which boss each player is
 * currently "focused on" so only one bar is displayed at a time per player.
 *
 * <p><b>Focus model:</b> last-damaged wins. When a consumer shows a bar, it calls
 * {@link #acquire(UUID, Object)}. Each tick/refresh, the consumer checks
 * {@link #isFocused(UUID, Object)} — if another consumer has taken focus since,
 * it hides its own bar.
 *
 * <p><b>Usage across mods:</b> EndgameQoL and any third-party mod share this
 * registry. Whoever calls {@code acquire} most recently wins; the others see
 * {@code isFocused == false} and self-hide. This avoids overlapping bars from
 * different mods.
 *
 * <p><b>The boss key is opaque:</b> typically a {@code Ref<EntityStore>} — any
 * object with stable {@code equals}/{@code hashCode} works. Using
 * {@code System.identityHashCode} or {@code toString} is not safe.
 */
public final class BossBarFocus {

    /** What a player is currently focused on. */
    public record FocusRecord(Object bossKey, long timestampMs) {}

    private static final Map<UUID, FocusRecord> FOCUS = new ConcurrentHashMap<>();

    private BossBarFocus() {}

    /**
     * Record a focus change. The caller becomes the current focus holder for
     * this player. Any previous focus is returned so the previous owner can
     * clean up its HUD on the next refresh via {@link #isFocused}.
     *
     * @return the previous focus (if any), or {@link Optional#empty()}
     */
    public static Optional<FocusRecord> acquire(UUID playerUuid, Object bossKey) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(bossKey, "bossKey");
        FocusRecord prev = FOCUS.put(playerUuid, new FocusRecord(bossKey, System.currentTimeMillis()));
        return Optional.ofNullable(prev);
    }

    /** Returns the current focus record, or empty if the player has no active bar. */
    public static Optional<FocusRecord> current(UUID playerUuid) {
        return Optional.ofNullable(FOCUS.get(playerUuid));
    }

    /** Is this specific boss currently the focus target for this player? */
    public static boolean isFocused(UUID playerUuid, Object bossKey) {
        FocusRecord r = FOCUS.get(playerUuid);
        return r != null && Objects.equals(r.bossKey(), bossKey);
    }

    /** Release focus for a specific (player, boss) pair — only clears if this
     *  boss is still the current focus (doesn't clobber a newer focus). */
    public static void release(UUID playerUuid, Object bossKey) {
        FOCUS.computeIfPresent(playerUuid,
                (k, v) -> Objects.equals(v.bossKey(), bossKey) ? null : v);
    }

    /** Release focus for a player unconditionally (e.g. on disconnect). */
    public static void releasePlayer(UUID playerUuid) {
        FOCUS.remove(playerUuid);
    }

    /** Release focus for this boss from ALL players (e.g. on boss death). */
    public static void releaseBoss(Object bossKey) {
        FOCUS.values().removeIf(r -> Objects.equals(r.bossKey(), bossKey));
    }

    /** Clear everything — host plugin shutdown. */
    public static void clear() {
        FOCUS.clear();
    }

    public static int size() { return FOCUS.size(); }
}
