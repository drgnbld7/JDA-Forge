package me.vasir.jdaforge.internal.logging;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Logger;
import org.slf4j.helpers.BasicMDCAdapter;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SLF4J 2.x binding for JDA-Forge. Registered via
 * {@code META-INF/services/org.slf4j.spi.SLF4JServiceProvider} and discovered by SLF4J on first use,
 * routing library log calls through {@link LogAdapter}. Internal.
 */
public final class Slf4jProvider implements SLF4JServiceProvider {

    public static final String REQUESTED_API_VERSION = "2.0.99";

    private final ILoggerFactory loggerFactory = new EmbeddedLoggerFactory();
    private final IMarkerFactory markerFactory = new BasicMarkerFactory();
    private final MDCAdapter mdcAdapter = new BasicMDCAdapter();

    @Override public ILoggerFactory getLoggerFactory() { return loggerFactory; }
    @Override public IMarkerFactory getMarkerFactory() { return markerFactory; }
    @Override public MDCAdapter getMDCAdapter() { return mdcAdapter; }
    @Override public String getRequestedApiVersion() { return REQUESTED_API_VERSION; }
    @Override public void initialize() {}

    /** Hands out (and caches) one {@link LogAdapter} per logger name. */
    private static final class EmbeddedLoggerFactory implements ILoggerFactory {
        private final ConcurrentMap<String, Logger> cache = new ConcurrentHashMap<>();

        @Override
        public Logger getLogger(String name) {
            return cache.computeIfAbsent(name, LogAdapter::new);
        }
    }
}