package me.vasir.jdaforge.api;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.io.FileInputStream;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.yaml.snakeyaml.Yaml;
import me.vasir.jdaforge.internal.database.Backup;
import me.vasir.jdaforge.internal.database.DriverLoader;

/**
 * Public access point for relational database access through a HikariCP pool and JDBI3. JDBC drivers
 * are loaded at runtime, in isolation, from the {@code drivers/} directory. {@link #getJdbi()} is the
 * query entry point; the lifecycle methods are managed by the framework.
 */
public final class Database {

    private static final Database INSTANCE = new Database();

    private Map<String, Object> rawConfig = Collections.emptyMap();
    private String driverClass;
    private String jdbcUrl;

    private Jdbi jdbi;
    private HikariDataSource dataSource;
    private URLClassLoader driverLoader;
    private ScheduledExecutorService backupScheduler;

    private Database() {}

    /** Internal: loads the driver and opens the HikariCP pool. */
    public static void connect() {
        INSTANCE.loadConfiguration();
        try {
            INSTANCE.driverLoader = DriverLoader.loadAndRegister(INSTANCE.driverClass, Database.class.getClassLoader());
            if (INSTANCE.driverLoader == null) {
                Class.forName(INSTANCE.driverClass);
            }
        } catch (Exception e) {
            Log.error("Failed to load the configured database driver: " + e.getMessage());
            return;
        }

        ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
        try {
            if (INSTANCE.driverLoader != null) {
                Thread.currentThread().setContextClassLoader(INSTANCE.driverLoader);
            }

            HikariConfig hikariConfig = INSTANCE.buildHikariConfig(INSTANCE.driverLoader != null);
            // Give Hikari's worker threads the original application classloader, not the isolated
            // driver loader, so the driver loader can be closed cleanly on shutdown. Daemon threads
            // so a missed disconnect never keeps the JVM alive.
            hikariConfig.setThreadFactory(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setContextClassLoader(oldLoader);
                return t;
            });
            INSTANCE.dataSource = new HikariDataSource(hikariConfig);
            INSTANCE.jdbi = Jdbi.create(INSTANCE.dataSource).installPlugin(new SqlObjectPlugin());

            Log.done("Relational database pool is online (HikariCP + JDBI3).");
        } catch (Exception e) {
            Log.error("HikariCP pool initialization failed: " + e.getMessage());
        } finally {
            Thread.currentThread().setContextClassLoader(oldLoader);
        }
    }

    /** Internal: starts the periodic file-backup task when 'auto-backup.enabled' is true. */
    public static void startAutoBackupTask() {
        Config backup = new Config(INSTANCE.rawConfig).section("auto-backup");
        if (!backup.getBoolean("enabled", false)) return;

        int interval = Math.max(1, backup.getInt("interval-minutes", 240));
        INSTANCE.backupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "JDAForge-Backup-Worker");
            t.setDaemon(true);
            return t;
        });

        INSTANCE.backupScheduler.scheduleAtFixedRate(
                () -> Backup.runBackupSequence(INSTANCE.jdbcUrl),
                interval, interval, TimeUnit.MINUTES
        );
        Log.done("Automatic backups enabled. Interval: " + interval + "m");
    }

    /** Public query to check if the database pool is active. */
    public static boolean isConnected() {
        return INSTANCE.dataSource != null && !INSTANCE.dataSource.isClosed();
    }

    /** Internal: safely stops the backup task, the pool and the driver loader. */
    public static void disconnect() {

        if (!isConnected()) return;

        if (INSTANCE.backupScheduler != null) {
            INSTANCE.backupScheduler.shutdown();
        }
        if (INSTANCE.dataSource != null && !INSTANCE.dataSource.isClosed()) {
            INSTANCE.dataSource.close();
        }
        DriverLoader.unregisterSafely();
        if (INSTANCE.driverLoader != null) {
            try {
                INSTANCE.driverLoader.close();
            } catch (Exception e) {
                Log.error("Failed to release isolated driver classloader: " + e.getMessage());
            }
        }
        Log.done("Database closed down gracefully.");
    }

    /** JDBI instance for queries. May be null when the database is disabled. */
    public static Jdbi getJdbi() {
        return INSTANCE.jdbi;
    }

    private void loadConfiguration() {
        File file = new File("database.yml");
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                Map<String, Object> data = new Yaml().load(in);
                if (data != null) rawConfig = data;
            } catch (Exception e) {
                Log.error("Failed to read database.yml: " + e.getMessage());
            }
        }
        Config rootConfig = new Config(rawConfig);
        this.driverClass = rootConfig.getString("driver", "org.sqlite.JDBC");
        this.jdbcUrl = rootConfig.getString("url", "jdbc:sqlite:./database.db");
    }

    private HikariConfig buildHikariConfig(boolean driverShimmed) {
        HikariConfig hikariConfig = new HikariConfig();
        Config rootConfig = new Config(rawConfig);
        Config pool = rootConfig.section("pool");
        Config perf = rootConfig.section("performance");

        // A driver loaded from drivers/ is registered under the shim's class name, so setting
        // driverClassName here would never match and Hikari would log a misleading warning; let it
        // resolve the driver from the JDBC URL instead. A bundled driver keeps an explicit name.
        if (!driverShimmed) {
            hikariConfig.setDriverClassName(driverClass);
        }

        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(rootConfig.getString("username", ""));
        hikariConfig.setPassword(rootConfig.getString("password", ""));

        hikariConfig.setMinimumIdle(pool.getInt("minimum-idle", 5));
        hikariConfig.setMaximumPoolSize(pool.getInt("maximum-pool-size", 15));
        hikariConfig.setConnectionTimeout(pool.getLong("connection-timeout-ms", 30000L));
        hikariConfig.setLeakDetectionThreshold(2000L);
        hikariConfig.setConnectionTestQuery("SELECT 1");

        hikariConfig.addDataSourceProperty("cachePrepStmts", perf.getBoolean("cache-prep-stmts", true));
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", perf.getInt("prep-stmt-cache-size", 250));
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", perf.getInt("prep-stmt-cache-sql-limit", 2048));
        hikariConfig.addDataSourceProperty("useServerPrepStmts", perf.getBoolean("use-server-prep-stmts", true));
        hikariConfig.addDataSourceProperty("autoReconnect", true);

        return hikariConfig;
    }
}