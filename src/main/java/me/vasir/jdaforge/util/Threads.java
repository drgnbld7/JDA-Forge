package me.vasir.jdaforge.util;

import org.jetbrains.annotations.NotNull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class Threads {

    private static final ExecutorService ASYNC_POOL;

    static {
        int cores = Runtime.getRuntime().availableProcessors();
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread t = new Thread(r, "JDAForge-Async-Worker-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };

        ASYNC_POOL = new ThreadPoolExecutor(
                Math.max(2, cores),
                Math.max(4, cores * 2),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private Threads() {}

    /** Runs the task in the background. */
    public static void async(Runnable task) {
        if (task != null) ASYNC_POOL.submit(task);
    }

    /** Gracefully drains the pool, forcing shutdown if it does not finish within 5s. */
    public static void shutdown() {
        ASYNC_POOL.shutdown();
        try {
            if (!ASYNC_POOL.awaitTermination(5, TimeUnit.SECONDS)) ASYNC_POOL.shutdownNow();
        } catch (InterruptedException e) {
            ASYNC_POOL.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}