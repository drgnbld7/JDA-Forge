package me.vasir.jdaforge.internal.module;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.yaml.snakeyaml.Yaml;
import me.vasir.jdaforge.api.Log;

/** Scans the jars in {@code modules/}, loads them into a shared classloader, and orders them by
 *  dependency. Internal. */
final class JarLoader {

    private static final Yaml YAML = new Yaml();
    private final File dir;
    private URLClassLoader loader;

    JarLoader(File dir) { this.dir = dir; }

    List<Map<String, Object>> scan() {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".jar"));
        if (files == null) return Collections.emptyList();

        Map<String, Map<String, Object>> configs = new HashMap<>();
        List<URL> urls = new ArrayList<>();

        for (File f : files) {
            Map<String, Object> cfg = readJarConfig(f);
            if (cfg.isEmpty() || !cfg.containsKey("name")) {
                Log.warn("Skipping '" + f.getName() + "': missing module.yml or 'name' field.");
                continue;
            }
            configs.put(((String) cfg.get("name")).toLowerCase(), cfg);
            try {
                urls.add(f.toURI().toURL());
            } catch (Exception ignored) {}
        }

        this.loader = new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
        List<Map<String, Object>> sorted = new ArrayList<>();
        for (String name : sortDependencies(configs)) {
            sorted.add(configs.get(name));
        }
        return sorted;
    }

    Class<?> load(String path) throws ClassNotFoundException {
        return Class.forName(path, true, loader);
    }

    void close() {
        try {
            if (loader != null) loader.close();
        } catch (Exception ignored) {}
    }

    /** Reads the {@code module.yml} bundled inside a module jar. */
    private static Map<String, Object> readJarConfig(File file) {
        try (JarFile jar = new JarFile(file)) {
            JarEntry entry = jar.getJarEntry("module.yml");
            if (entry == null) return Collections.emptyMap();
            try (InputStream is = jar.getInputStream(entry)) {
                Map<String, Object> data = YAML.load(is);
                return data != null ? data : Collections.emptyMap();
            }
        } catch (Exception e) {
            Log.error("Failed to read module.yml from " + file.getName() + " -> " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** Topologically orders modules by their declared dependencies, warning about cycles. */
    private static List<String> sortDependencies(Map<String, Map<String, Object>> configs) {
        List<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String name : configs.keySet()) {
            resolve(name.toLowerCase(), configs, sorted, visited, visiting);
        }
        return sorted;
    }

    @SuppressWarnings("unchecked")
    private static void resolve(String name, Map<String, Map<String, Object>> configs, List<String> sorted, Set<String> visited, Set<String> visiting) {
        if (visited.contains(name)) return;
        if (visiting.contains(name)) {
            Log.warn("Circular dependency detected for module: " + name);
            return;
        }

        Map<String, Object> cfg = configs.get(name);
        if (cfg == null) {
            Log.warn("Module dependency '" + name + "' is not present; skipping it.");
            visited.add(name);
            return;
        }

        visiting.add(name);
        List<String> depends = (List<String>) cfg.get("depend");
        if (depends != null) {
            for (String dep : depends) {
                resolve(dep.toLowerCase(), configs, sorted, visited, visiting);
            }
        }

        visiting.remove(name);
        visited.add(name);
        sorted.add(name);
    }
}