package me.vasir.jdaforge.internal.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vasir.jdaforge.api.Bot;
import org.yaml.snakeyaml.Yaml;
import me.vasir.jdaforge.api.Log;
import me.vasir.jdaforge.api.Config;
import me.vasir.jdaforge.api.Database;
import me.vasir.jdaforge.internal.module.ModuleManager;
import me.vasir.jdaforge.util.Files;
import me.vasir.jdaforge.util.Threads;
import me.vasir.jdaforge.util.Checks;

/**
 * Orchestrates startup and shutdown: prepares the environment, loads and validates configuration,
 * deploys bundled assets, then boots the gateway, database and modules. Internal.
 */
public final class ForgeEngine {

    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final Yaml YAML = new Yaml();

    private static final String[] BUNDLED_DRIVERS = {
            "h2-2.4.240.jar",
            "mariadb-java-client-3.5.8.jar",
            "mysql-connector-j-9.7.0.jar",
            "postgresql-42.7.11.jar",
            "sqlite-jdbc-3.53.1.0.jar"
    };

    private ForgeEngine() {}

    public static Map<String, Object> start() {
        if (!isRunning.compareAndSet(false, true)) {
            Log.warn("JDAForge engine is already active.");
            return null;
        }

        Log.info("Starting JDA-Forge...");

        Files.ensureDirectoryExists("modules");

        File configFile = new File("jda-forge.yml");
        extractResource("/jda-forge.yml", configFile);

        Map<String, Object> rawMap;
        boolean databaseEnabled;

        try (FileInputStream in = new FileInputStream(configFile)) {
            rawMap = readYaml(in);
            if (rawMap.isEmpty()) {
                Log.error("jda-forge.yml is empty, unreadable, or corrupted.");
                isRunning.set(false);
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> settings = (Map<String, Object>) rawMap.get("settings");
            if (settings == null || settings.get("token") == null) {
                Log.error("Missing 'settings' block or 'token' in jda-forge.yml.");
                isRunning.set(false);
                return null;
            }

            String token = settings.get("token").toString().trim();
            if (Checks.isEmpty(token) || token.equals("YOUR_TOKEN_HERE")) {
                Log.error("Missing or default Discord token in jda-forge.yml. Aborting.");
                isRunning.set(false);
                return null;
            }

            Object loggingOpts = settings.get("logging");
            if (loggingOpts != null) {
                Log.configure(loggingOpts.toString());
            }

            databaseEnabled = Boolean.TRUE.equals(settings.get("database"));
        } catch (Exception e) {
            Log.error("Failed to process configuration: " + e.getMessage());
            isRunning.set(false);
            return null;
        }

        Config platformConfig = new Config(rawMap);

        if (databaseEnabled) {
            extractResource("/database.yml", new File("database.yml"));
            extractDatabaseDrivers();
            Log.info("Connecting to the database...");
            Database.connect();
        }

        try {
            Log.info("Connecting to Discord...");
            BotEngine.getInstance().boot(platformConfig);

            Log.info("Loading modules from modules/ ...");
            ModuleManager.loadModules();
            ModuleManager.enableModules(Bot.core());

            if (databaseEnabled) {
                Database.startAutoBackupTask();
            }

            Log.done("All subsystems online.");
            return rawMap;

        } catch (Exception e) {
            Log.error("Fatal error during startup:");
            Log.error(e);
            stop();
            return null;
        }
    }

    public static void stop() {
        Log.warn("Shutting down...");

        ModuleManager.disableModules();
        BotEngine.getInstance().shutdown();
        Database.disconnect();

        Threads.shutdown();
        isRunning.set(false);
    }

    private static Map<String, Object> readYaml(InputStream in) {
        if (in == null) return Collections.emptyMap();
        try {
            Map<String, Object> data = YAML.load(in);
            return data != null ? data : Collections.emptyMap();
        } catch (Exception e) {
            Log.error("Failed to parse YAML: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** Copies a bundled resource to the target only if it does not already exist. */
    private static void extractResource(String resourcePath, File targetFile) {
        if (targetFile.exists()) return;
        File parent = targetFile.getParentFile();
        if (parent != null && !Files.ensureDirectoryExists(parent.getPath())) return;

        try (InputStream in = ForgeEngine.class.getResourceAsStream(resourcePath)) {
            InputStream source = (in != null) ? in : Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
            if (source != null) {
                java.nio.file.Files.copy(source, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Log.done("Created default file: " + targetFile.getName());
            }
        } catch (Exception e) {
            Log.error("Failed to deploy resource: " + resourcePath);
        }
    }

    /**
     * Deploys the bundled driver jars into {@code drivers/} on first run only. If the folder already
     * exists it is left untouched, so jars the user deliberately removed are not regenerated.
     */
    private static void extractDatabaseDrivers() {
        File dir = new File("drivers");
        if (dir.exists()) {
            File[] jars = dir.listFiles((d, n) -> n.endsWith(".jar"));
            if (jars == null || jars.length == 0) {
                Log.warn("'drivers/' exists but contains no jars. Delete the folder to redeploy the bundled drivers.");
            }
            return;
        }

        if (!Files.ensureDirectoryExists("drivers")) {
            Log.error("Failed to create the 'drivers/' directory.");
            return;
        }

        int count = 0;
        for (String driver : BUNDLED_DRIVERS) {
            File dest = new File(dir, driver);
            try (InputStream in = ForgeEngine.class.getResourceAsStream("/drivers/" + driver)) {
                if (in != null) {
                    java.nio.file.Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
            } catch (Exception ignored) {}
        }
        if (count > 0) {
            Log.done(count + " database drivers deployed to drivers/");
        }
    }
}