package me.vasir.jdaforge.internal.logging;

import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;
import me.vasir.jdaforge.api.Log;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Forwards SLF4J log entries directly into the JDAForge API framework. */
public final class LogAdapter extends LegacyAbstractLogger {

    private static volatile int threshold = Level.INFO.toInt();

    LogAdapter(String name) { this.name = name; }

    public static void setThreshold(String level) {
        try {
            threshold = Level.valueOf(level == null ? "INFO" : level.trim().toUpperCase()).toInt();
        } catch (IllegalArgumentException e) {
            threshold = Level.INFO.toInt();
        }
    }

    private static boolean enabled(Level level) { return level.toInt() >= threshold; }

    @Override public boolean isTraceEnabled() { return enabled(Level.TRACE); }
    @Override public boolean isDebugEnabled() { return enabled(Level.DEBUG); }
    @Override public boolean isInfoEnabled()  { return enabled(Level.INFO); }
    @Override public boolean isWarnEnabled()  { return enabled(Level.WARN); }
    @Override public boolean isErrorEnabled() { return enabled(Level.ERROR); }
    @Override protected String getFullyQualifiedCallerName() { return null; }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String pattern, Object[] args, Throwable t) {
        if (!enabled(level)) return;
        String body = "[" + shortName() + "] " + MessageFormatter.basicArrayFormat(pattern, args);
        if (t != null) body += System.lineSeparator() + stackTrace(t);

        switch (level) {
            case ERROR -> Log.error(body);
            case WARN  -> Log.warn(body);
            default    -> Log.info(body);
        }
    }

    private String shortName() {
        if (name == null) return "?";
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1) : name;
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString().stripTrailing();
    }
}