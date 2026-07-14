package me.vasir.jdaforge.internal.logging;

import me.vasir.jdaforge.util.Metrics;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Failsafe diagnostics reporter that writes dump reports bypassing the logging system. */
public final class CrashReporter {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter HUMAN_STAMP = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private CrashReporter() {}

    public static void generateDump(Thread thread, Throwable t, String reason) {
        File file = new File("logs", "dump_" + reason.toLowerCase() + "_" + LocalDateTime.now().format(FILE_STAMP) + ".txt");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("=========================================================================");
            pw.println("                       JDAFORGE SYSTEM DIAGNOSTIC DUMP                   ");
            pw.println("=========================================================================");
            pw.println("Timestamp : " + LocalDateTime.now().format(HUMAN_STAMP));
            pw.println("Reason    : " + reason);
            pw.println("Thread    : " + thread.getName() + " (ID: " + thread.threadId() + ")");
            pw.println("Processors: " + Metrics.getCpuCores());
            pw.println("Memory    : " + Metrics.getMemoryUsage());
            pw.println("-------------------------------------------------------------------------");
            if (t != null) {
                pw.println("[EXCEPTION DETAILS]\nClass: " + t.getClass().getName());
                pw.println("Message: " + t.getMessage() + "\n\n[STACKTRACE]");
                t.printStackTrace(pw);
            }
            pw.println("=========================================================================");
            System.err.println("\u001B[38;5;167m[ERROR] Critical crash captured. Report written to: " + file.getPath() + "\u001B[0m");
        } catch (Exception e) {
            System.err.println("\u001B[38;5;167m[ERROR] Failed to write diagnostic dump: " + e.getMessage() + "\u001B[0m");
        }
    }
}