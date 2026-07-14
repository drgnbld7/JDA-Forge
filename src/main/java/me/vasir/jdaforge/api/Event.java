package me.vasir.jdaforge.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Thread-safe in-process event bus for cross-module communication. Modules subscribe to and fire
 * their own custom event types.
 */
public final class Event {

    private static final Map<Class<?>, List<Consumer<Object>>> LISTENERS = new ConcurrentHashMap<>();

    private Event() {}

    /**
     * Subscribes to a specific custom event class with type safety.
     */
    @SuppressWarnings("unchecked")
    public static <T> void subscribe(Class<T> eventClass, Consumer<T> listener) {
        if (eventClass == null || listener == null) return;

        LISTENERS.computeIfAbsent(eventClass, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(obj -> {
                    try {
                        listener.accept((T) obj);
                    } catch (ClassCastException e) {
                        Log.error("Event type mismatch for: " + eventClass.getSimpleName());
                    }
                });
    }

    /**
     * Fires a custom event and synchronously notifies all sub-module subscribers.
     */
    public static void fire(Object event) {
        if (event == null) return;

        List<Consumer<Object>> eventListeners = LISTENERS.get(event.getClass());
        if (eventListeners == null || eventListeners.isEmpty()) return;

        List<Consumer<Object>> listenersCopy;
        synchronized (eventListeners) {
            listenersCopy = new ArrayList<>(eventListeners);
        }

        for (Consumer<Object> listener : listenersCopy) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                Log.error("Error in event listener for [" + event.getClass().getSimpleName() + "]:");
                Log.error(e);
            }
        }
    }

    /** Clears all registered custom listeners. Crucial for module hot-reloads. */
    public static void clearAllListeners() {
        LISTENERS.clear();
    }
}