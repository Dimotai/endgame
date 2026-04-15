package endgame.bossmanager;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Callback invoked when a registered boss transitions to a new phase.
 *
 * <p>Implementations are plugin-specific — they typically spawn minions, apply visual effects,
 * or trigger boss-specific mechanics. The framework guarantees this is called on the boss's
 * world thread, so direct component mutation is safe.
 *
 * <p>Part of the extractable {@code endgame.bossmanager.*} core — no Endgame-specific imports.
 */
@FunctionalInterface
public interface BossPhaseCallback<S> {
    /**
     * @param state      mutable per-boss state (contains current phase, HP, ref, config)
     * @param newPhase   1-based phase number the boss just entered
     * @param store      entity store where the boss lives
     */
    void onPhaseChange(S state, int newPhase, Store<EntityStore> store);
}
