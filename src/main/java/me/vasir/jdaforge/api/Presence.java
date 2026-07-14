package me.vasir.jdaforge.api;

import net.dv8tion.jda.api.OnlineStatus;

/**
 * Public control for the bot's gateway presence. Modules can query or change the online status
 * without touching the internal presence rotator.
 */
public final class Presence {

    private static final Presence INSTANCE = new Presence();
    private volatile OnlineStatus currentStatus = OnlineStatus.ONLINE;
    private Object internalEngineRef;

    private Presence() {}

    public static Presence getInstance() {
        return INSTANCE;
    }

    /** Immediately updates the bot's online gateway status. */
    public void status(OnlineStatus status) {
        if (status == null) return;
        this.currentStatus = status;

        // Forward the change to the running engine, if one is linked.
        if (internalEngineRef != null) {
            try {
                java.lang.reflect.Method updateMethod = internalEngineRef.getClass().getDeclaredMethod("updateStatusDirectly", OnlineStatus.class);
                updateMethod.setAccessible(true);
                updateMethod.invoke(internalEngineRef, status);
            } catch (Exception ignored) {}
        }
    }

    public OnlineStatus status() {
        return currentStatus;
    }

    /** Internal: links the running engine and sets the initial status. */
    public void setEngineLink(Object engine, OnlineStatus initialStatus) {
        this.internalEngineRef = engine;
        this.currentStatus = initialStatus;
    }
}