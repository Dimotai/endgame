package endgame.bossbar;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for {@link BossBarTheme}s, keyed by NPC type ID.
 *
 * <p>Public API — consumers register themes at startup and render bars at refresh
 * time. The registry is a thread-safe singleton (static) so any module can look
 * up themes without needing to plumb the registry instance around.
 *
 * <pre>{@code
 * // At startup
 * BossBarRegistry.register(BossBarTheme.builder("Endgame_Golem_Void")
 *         .displayName("VOID GOLEM")
 *         .nameColor("#bb44ff")
 *         .barColor("#aa44ee")
 *         .phase(1, "Awakened", "#c8bfff", 1.00f)
 *         .phase(2, "Enraged",  "#ffb060", 0.67f)
 *         .phase(3, "FURY",     "#ff4466", 0.33f)
 *         .build());
 *
 * // At refresh time
 * BossBarState st = new BossBarState(currentPhase, hpPct, curHp, maxHp, false, false);
 * Optional<String> html = BossBarRegistry.render("Endgame_Golem_Void", st);
 * }</pre>
 */
public final class BossBarRegistry {

    private static final Map<String, BossBarTheme> THEMES = new ConcurrentHashMap<>();

    private BossBarRegistry() {}

    /** Register (or replace) a theme for the given NPC type ID. */
    public static void register(BossBarTheme theme) {
        Objects.requireNonNull(theme, "theme");
        THEMES.put(theme.npcTypeId(), theme);
    }

    /** Remove a registered theme. */
    public static void unregister(String npcTypeId) {
        THEMES.remove(npcTypeId);
    }

    /** Clear all registered themes (useful on plugin reload). */
    public static void clear() {
        THEMES.clear();
    }

    /** Look up a theme by NPC type ID. */
    public static Optional<BossBarTheme> getTheme(String npcTypeId) {
        if (npcTypeId == null) return Optional.empty();
        return Optional.ofNullable(THEMES.get(npcTypeId));
    }

    /** Convenience: render bar HTML directly. Returns empty if no theme is registered. */
    public static Optional<String> render(String npcTypeId, BossBarState state) {
        return getTheme(npcTypeId).map(theme -> BossBarRenderer.render(theme, state));
    }

    /** All registered themes (read-only view). */
    public static Collection<BossBarTheme> all() {
        return Collections.unmodifiableCollection(THEMES.values());
    }

    public static int size() { return THEMES.size(); }
}
