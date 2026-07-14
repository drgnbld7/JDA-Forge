package me.vasir.jdaforge.internal.core;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import me.vasir.jdaforge.api.Log;

/**
 * Executable entry point of the framework. Boots the engine, then keeps the process alive until a
 * shutdown is requested (Ctrl+C, terminal close, or a programmatic {@link #shutdown()} call).
 */
public final class JDAForge {

    private static JDAForge instance;
    private static final AtomicBoolean isStopping = new AtomicBoolean(false);
    private final Map<String, Object> config;

    private JDAForge(Map<String, Object> config) {
        this.config = Collections.unmodifiableMap(config);
    }

    public static void main(String[] args) {
        Map<String, Object> loadedConfig = ForgeEngine.start();
        if (loadedConfig == null) {
            System.exit(1);
            return;
        }

        instance = new JDAForge(loadedConfig);
        Runtime.getRuntime().addShutdownHook(new Thread(JDAForge::cleanup, "JDAForge-Shutdown-Hook"));
    }

    /**
     * Requests a full shutdown from application code and terminates the JVM. The shutdown hook runs
     * the cleanup exactly once; a manual call performs it here and then exits.
     */
    public static void shutdown() {
        if (cleanup()) System.exit(0);
    }

    /**
     * Runs the cleanup sequence at most once. Safe to invoke from the shutdown hook (never calls
     * {@link System#exit}, which would deadlock a hook thread). Returns true if this call performed
     * the cleanup, false if it had already run.
     */
    private static boolean cleanup() {
        if (!isStopping.compareAndSet(false, true)) return false;
        Thread.currentThread().setName("JDAForge-Shutdown-Thread");

        ForgeEngine.stop();
        instance = null;

        Log.done("JDAForge system successfully offline. Safe to close terminal.");
        return true;
    }

    public static JDAForge get() {
        return instance;
    }

    public Map<String, Object> config() {
        return this.config;
    }
}