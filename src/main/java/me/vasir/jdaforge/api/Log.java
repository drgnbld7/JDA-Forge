package me.vasir.jdaforge.api;

import me.vasir.jdaforge.internal.logging.CrashReporter;
import me.vasir.jdaforge.internal.logging.FileLogger;
import me.vasir.jdaforge.internal.logging.Interceptor;
import me.vasir.jdaforge.util.Files;
import me.vasir.jdaforge.util.Checks;

/**
 * Single entry point for framework logging.
 * Manages standardized ANSI console routing and daily rolling log files.
 */
public final class Log {

    public enum Target { CONSOLE, FILE, BOTH }

    private static final char ESC = 27;
    private static final String RESET = ESC + "[0m";
    private static final String RED = ESC + "[38;5;167m";
    private static final String GREEN = ESC + "[38;5;108m";
    private static final String ORANGE = ESC + "[38;5;179m";
    private static final String BLUE = ESC + "[38;2;209;221;237m"; // #d1dded

    private static volatile boolean console = true;
    private static volatile boolean file = true;

    static {
        Files.ensureDirectoryExists("logs");
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> dump(t, e, "FATAL_CRASH"));
        // Route raw System.out / System.err writes through this logger.
        Interceptor.bind();
    }

    private Log() {}

    /** Configures where log statements should be routed. */
    public static void configure(String targetOpt) {
        if (Checks.isEmpty(targetOpt)) return;
        try {
            Target t = Target.valueOf(targetOpt.trim().toUpperCase());
            console = (t == Target.CONSOLE || t == Target.BOTH);
            file = (t == Target.FILE || t == Target.BOTH);
        } catch (IllegalArgumentException e) {
            console = true;
            file = true;
        }
    }

    public static void info(String msg) { write("INFO", BLUE, msg); }
    public static void done(String msg) { write("DONE", GREEN, msg); }
    public static void warn(String msg) { write("WARN", ORANGE, msg); }
    public static void error(String msg) { write("ERROR", RED, msg); }

    public static void error(Throwable t) {
        if (t == null) return;
        if (console) {
            System.out.println(RED + "[ERROR] An unexpected system exception occurred: " + t.getMessage() + RESET);
            if (file) System.out.println(ORANGE + "[WARN] Check the 'logs/' directory for full exception details." + RESET);
        }
        if (file) FileLogger.logException(t);
    }

    /** Forces a deep crash dump file with system metrics. */
    public static void dump(Thread thread, Throwable t, String reason) {
        CrashReporter.generateDump(thread, t, reason);
    }

    private static void write(String level, String color, String msg) {
        if (console) System.out.println(color + "[" + level + "] " + msg + RESET);
        if (file) FileLogger.log(level, msg);
    }
}