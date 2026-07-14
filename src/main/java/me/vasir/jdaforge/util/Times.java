package me.vasir.jdaforge.util;

import java.util.concurrent.TimeUnit;

public final class Times {

    private Times() {}

    /** Formats a duration in ms to a readable string (e.g. 65000 -> "1m 5s"). */
    public static String formatDuration(long durationMs) {
        if (durationMs <= 0) return "0s";
        long hours = TimeUnit.MILLISECONDS.toHours(durationMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }
}