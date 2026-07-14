package me.vasir.jdaforge.internal.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import me.vasir.jdaforge.api.Config;
import me.vasir.jdaforge.api.Log;
import me.vasir.jdaforge.util.Files;
import me.vasir.jdaforge.util.Checks;

/**
 * Creates (under {@code config/}), caches and reads YAML config files for the framework and modules.
 * Internal.
 */
public final class ConfigLoader {

    private static final Map<String, Config> CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Yaml> YAML_PROVIDER = ThreadLocal.withInitial(() -> {
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options);
    });

    private ConfigLoader() {}

    /**
     * Extracts a template from the caller's jar resources into {@code config/} (creating an empty file
     * when no template exists), then loads and caches it as a {@link Config}.
     */
    public static Config register(Class<?> caller, String subPath, String fileName, boolean recreate) {
        if (Checks.isEmpty(fileName)) {
            Log.error("Cannot register a config with a null or empty file name.");
            return Config.empty();
        }

        File baseDir = new File("config");
        String cleanFile = Files.cleanPath(fileName);
        String cleanSub = Checks.isEmpty(subPath) ? "" : Files.cleanPath(subPath);

        File targetDir = cleanSub.isEmpty() ? baseDir : new File(baseDir, cleanSub);
        File targetFile = new File(targetDir, cleanFile);

        String cacheKey = buildCacheKey(cleanSub, cleanFile);

        if (targetFile.exists()) {
            Config config = loadFromFile(targetFile);
            CACHE.put(cacheKey, config);
            return config;
        }

        if (!recreate) {
            Log.warn("Missing expected configuration target: " + targetFile.getName());
            return Config.empty();
        }

        if (!Files.ensureDirectoryExists(targetDir.getPath())) {
            Log.error("Could not create the config directory for: " + cleanFile);
            return Config.empty();
        }

        String resourcePath = cleanSub.isEmpty() ? "/" + cleanFile : "/" + cleanSub + "/" + cleanFile;
        try (InputStream in = caller.getResourceAsStream(resourcePath)) {
            InputStream source = (in != null) ? in : caller.getClassLoader().getResourceAsStream(cleanFile);
            if (source != null) {
                java.nio.file.Files.copy(source, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Log.done("Created config file: " + targetFile.getName());
            } else if (targetFile.createNewFile()) {
                Log.warn("No template found in the jar. Created an empty config: " + targetFile.getName());
            }
        } catch (Exception e) {
            Log.error("Failed to create config file: " + cleanFile);
        }

        Config config = loadFromFile(targetFile);
        CACHE.put(cacheKey, config);
        return config;
    }

    /** Retrieves an already cached configuration view. */
    public static Config get(String subPath, String fileName) {
        String key = buildCacheKey(subPath, fileName);
        return CACHE.getOrDefault(key, Config.empty());
    }

    private static Config loadFromFile(File file) {
        if (!file.exists()) return Config.empty();
        try (FileInputStream in = new FileInputStream(file)) {
            Map<String, Object> data = YAML_PROVIDER.get().load(in);
            return data != null ? new Config(data) : Config.empty();
        } catch (Exception e) {
            Log.error("Failed to read config (" + file.getName() + "): " + e.getMessage());
            return Config.empty();
        }
    }

    private static String buildCacheKey(String subPath, String fileName) {
        String normFile = fileName.replace(".yml", "").toLowerCase().trim();
        if (Checks.isEmpty(subPath)) {
            return normFile;
        }
        return subPath.replace("/", "").toLowerCase().trim() + ":" + normFile;
    }
}