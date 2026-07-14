package me.vasir.jdaforge.util;

public final class Metrics {

    private Metrics() {}

    /** Current heap usage, e.g. "128 MB / 512 MB". */
    public static String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / 1048576L;
        long freeMemory = runtime.freeMemory() / 1048576L;
        return (totalMemory - freeMemory) + " MB / " + totalMemory + " MB";
    }

    public static int getCpuCores() {
        return Runtime.getRuntime().availableProcessors();
    }
}