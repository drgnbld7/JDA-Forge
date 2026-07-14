package me.vasir.jdaforge.api;

import java.util.List;
import me.vasir.jdaforge.internal.module.ModuleManager;

/**
 * Public entry point for inspecting and hot-reloading the loaded modules.
 */
public final class Modules {

    private Modules() {}

    /** Snapshot of a loaded module: metadata and whether it enabled successfully. */
    public record Info(String name, String version, String author, boolean enabled) {}

    /** Disables, reloads from {@code modules/} and re-enables every module without restarting the gateway. */
    public static void reload() {
        ModuleManager.reload();
    }

    /** Currently loaded modules and their status. */
    public static List<Info> list() {
        return ModuleManager.listInfo();
    }
}
