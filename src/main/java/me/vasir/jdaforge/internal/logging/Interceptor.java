package me.vasir.jdaforge.internal.logging;

import me.vasir.jdaforge.api.Log;
import java.io.PrintStream;

/**
 * Routes raw {@code System.out} / {@code System.err} writes through the framework {@link Log},
 * so stray print statements share the same console/file formatting. Internal.
 */
public final class Interceptor extends PrintStream {

    private final PrintStream original;
    private final boolean isErrorStream;

    private Interceptor(PrintStream original, boolean isErrorStream) {
        super(original, true);
        this.original = original;
        this.isErrorStream = isErrorStream;
    }

    public static void bind() {
        System.setOut(new Interceptor(System.out, false));
        System.setErr(new Interceptor(System.err, true));
    }

    @Override
    public void println(String x) { log(x); }

    @Override
    public void print(String x) { log(x); }

    private void log(String text) {
        if (text == null || text.trim().isEmpty()) return;

        // Our own formatted output is written straight to the real stream. This must go through the
        // original PrintStream (not super.println, which would re-enter our overridden print()).
        if (text.contains("[INFO]") || text.contains("[DONE]") || text.contains("[WARN]") || text.contains("[ERROR]")) {
            original.println(text);
            return;
        }

        if (isErrorStream) Log.error(text);
        else Log.info(text);
    }
}
