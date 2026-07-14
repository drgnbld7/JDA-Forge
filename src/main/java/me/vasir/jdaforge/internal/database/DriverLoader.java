package me.vasir.jdaforge.internal.database;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.sql.DriverManager;
import me.vasir.jdaforge.api.Log;
import me.vasir.jdaforge.util.Files;

public final class DriverLoader {

    private static DriverShim registeredShim;

    private DriverLoader() {}

    public static URLClassLoader loadAndRegister(String driverClass, ClassLoader parentLoader) throws Exception {
        if (!Files.ensureDirectoryExists("drivers")) {
            Log.error("Could not allocate the 'drivers' directory!");
            return null;
        }
        File[] files = new File("drivers").listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null || files.length == 0) {
            Log.warn("No driver .jar files found in 'drivers/'. Place the JDBC driver for '" + driverClass + "' there.");
            return null;
        }
        URL[] urls = new URL[files.length];
        for (int i = 0; i < files.length; i++) urls[i] = files[i].toURI().toURL();

        URLClassLoader loader = new URLClassLoader(urls, parentLoader);
        Class<?> clazz;
        try {
            clazz = Class.forName(driverClass, true, loader);
        } catch (ClassNotFoundException e) {
            Log.warn("Configured driver '" + driverClass + "' was not found in 'drivers/'. Ensure the jar is present.");
            loader.close();
            return null;
        }

        Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
        unregisterSafely();
        registeredShim = new DriverShim(driver);
        DriverManager.registerDriver(registeredShim);
        Log.done("Dynamic SQL database driver active: " + driverClass);
        return loader;
    }

    public static void unregisterSafely() {
        if (registeredShim == null) return;
        try {
            DriverManager.deregisterDriver(registeredShim);
            registeredShim = null;
        } catch (Exception e) {
            Log.error("Failed to cleanly flush database driver hooks: " + e.getMessage());
        }
    }
}