package me.vasir.jdaforge.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import me.vasir.jdaforge.internal.core.ConfigLoader;

/**
 * Immutable, type-safe view over a parsed YAML tree using dot notation.
 * Acts as the unified configuration access point for modules and the platform.
 */
public final class Config {

    private static final Config EMPTY = new Config(Map.of());
    private final Map<String, Object> root;

    public Config(Map<String, Object> root) {
        this.root = (root != null) ? root : Map.of();
    }

    public static Config register(String fileName) {
        return register(Config.class, null, fileName, true);
    }

    public static Config register(String fileName, boolean recreate) {
        return register(Config.class, null, fileName, recreate);
    }

    public static Config register(String subPath, String fileName) {
        return register(Config.class, subPath, fileName, true);
    }

    public static Config register(String subPath, String fileName, boolean recreate) {
        return register(Config.class, subPath, fileName, recreate);
    }

    /** Extracts the template from the caller's jar resources into {@code config/} and caches it. */
    public static Config register(Class<?> callerClass, String subPath, String fileName, boolean recreate) {
        return ConfigLoader.register(callerClass, subPath, fileName, recreate);
    }

    /** Retrieves an already cached configuration view. */
    public static Config get(String fileName) {
        return ConfigLoader.get(null, fileName);
    }

    /** Retrieves an already cached sub-path configuration view. */
    public static Config get(String subPath, String fileName) {
        return ConfigLoader.get(subPath, fileName);
    }

    public static Config empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return root.isEmpty();
    }

    public boolean has(String path) {
        return resolve(path) != null;
    }

    /** Navigates to a specific nested configuration block. */
    @SuppressWarnings("unchecked")
    public Config section(String path) {
        Object o = resolve(path);
        return new Config(o instanceof Map ? (Map<String, Object>) o : Map.of());
    }

    public String getString(String path) {
        return getString(path, null);
    }

    public String getString(String path, String def) {
        Object o = resolve(path);
        return o != null ? o.toString() : def;
    }

    public int getInt(String path, int def) {
        Object o = resolve(path);
        return o instanceof Number n ? n.intValue() : def;
    }

    public long getLong(String path, long def) {
        Object o = resolve(path);
        return o instanceof Number n ? n.longValue() : def;
    }

    public boolean getBoolean(String path, boolean def) {
        Object o = resolve(path);
        return o instanceof Boolean b ? b : def;
    }

    public List<?> getList(String path) {
        Object o = resolve(path);
        return o instanceof List<?> l ? l : Collections.emptyList();
    }

    public List<String> getStringList(String path) {
        List<String> out = new ArrayList<>();
        for (Object o : getList(path)) {
            if (o != null) out.add(o.toString());
        }
        return out;
    }

    public List<Long> getLongList(String path) {
        List<Long> out = new ArrayList<>();
        for (Object o : getList(path)) {
            Long id = toLong(o);
            if (id != null) out.add(id);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Object resolve(String path) {
        if (path == null) return null;
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(part);
            if (current == null) return null;
        }
        return current;
    }

    private static Long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}