package me.vasir.jdaforge.internal.logging;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Handles daily rolling log writes safely via synchronized access. Internal. */
public final class FileLogger {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private FileLogger() {}

    private static File getDailyFile() {
        return new File("logs", LocalDateTime.now().format(DATE_FORMAT) + ".log");
    }

    public static synchronized void log(String level, String message) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(getDailyFile(), true))) {
            pw.println("[" + LocalDateTime.now().format(TIME_FORMAT) + "] [" + level + "] " + message);
        } catch (Exception ignored) {}
    }

    public static synchronized void logException(Throwable throwable) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(getDailyFile(), true))) {
            pw.println("[" + LocalDateTime.now().format(TIME_FORMAT) + "] [EXCEPTION] " + throwable.getMessage());
            throwable.printStackTrace(pw);
            pw.println();
        } catch (Exception ignored) {}
    }
}