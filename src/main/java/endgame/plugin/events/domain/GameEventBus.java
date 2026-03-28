package endgame.plugin.events.domain;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Lightweight in-process event bus for EndgameQoL domain events.
 * Thread-safe: ConcurrentHashMap + CopyOnWriteArrayList for lock-free iteration.
 *
 * Usage:
 *   bus.subscribe(BossKillEvent.class, event -> { ... });
 *   bus.publish(new BossKillEvent(...));
 */
public final class GameEventBus {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.EventBus");

    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    /**
     * Subscribe to a specific event type.
     * Listeners are called synchronously in registration order.
     */
    @SuppressWarnings("unchecked")
    public <T extends GameEvent> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
        LOGGER.atFine().log("[EventBus] Subscribed %s → %s", eventType.getSimpleName(), listener.getClass().getSimpleName());
    }

    /**
     * Publish an event to all registered listeners.
     * Exceptions in one listener don't prevent others from running.
     */
    @SuppressWarnings("unchecked")
    public void publish(GameEvent event) {
        List<Consumer<?>> handlers = listeners.get(event.getClass());
        if (handlers == null || handlers.isEmpty()) return;

        for (Consumer<?> handler : handlers) {
            try {
                ((Consumer<GameEvent>) handler).accept(event);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[EventBus] Listener error for %s", event.getClass().getSimpleName());
            }
        }
    }

    /** Remove all listeners. Called on plugin shutdown. */
    public void clear() {
        listeners.clear();
    }
}
