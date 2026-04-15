package endgame.bossmanager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Extension point for boss-specific logic that runs alongside the generic manager.
 *
 * <p>A third-party mod registers a listener per boss to hook into:
 * <ul>
 *   <li>{@code onRegister} — initial setup (capture spawn position, initialize state, etc.)</li>
 *   <li>{@code onUnregister} — cleanup (release resources tied to the boss)</li>
 * </ul>
 *
 * <p>Phase-change callbacks go through {@link BossPhaseCallback} on the encounter config itself.
 * Keep listener methods lightweight — they run synchronously on the registration path.
 *
 * <p>Part of the extractable {@code endgame.bossmanager.*} core — no Endgame-specific imports.
 */
public interface BossLifecycleListener<S> {
    default void onRegister(Ref<EntityStore> bossRef, S state, Store<EntityStore> store) {}
    default void onUnregister(Ref<EntityStore> bossRef, S state) {}
}
