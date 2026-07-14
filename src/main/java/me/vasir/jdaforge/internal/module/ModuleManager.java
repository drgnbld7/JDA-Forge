package me.vasir.jdaforge.internal.module;

import java.io.File;
import java.util.*;
import me.vasir.jdaforge.api.Event;
import me.vasir.jdaforge.api.Log;
import me.vasir.jdaforge.api.ForgeModule;
import me.vasir.jdaforge.api.Modules;
import me.vasir.jdaforge.util.Files;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.sharding.ShardManager;

/**
 * Loads, enables, disables and hot-reloads the module jars under {@code modules/}. Internal.
 */
public final class ModuleManager {

    private static final ModuleManager INSTANCE = new ModuleManager();
    private final Map<String, ForgeModule> modules = new LinkedHashMap<>();
    private final Map<String, Boolean> enabledState = new LinkedHashMap<>();
    private JarLoader loader = new JarLoader(new File("modules"));
    private Object activeCoreRef;

    private ModuleManager() {
        Files.ensureDirectoryExists("modules");
    }

    public static void loadModules() {
        INSTANCE.enabledState.clear();
        for (Map<String, Object> cfg : INSTANCE.loader.scan()) {
            try {
                ModuleInfo info = ModuleInfo.from(cfg);
                ForgeModule m = (ForgeModule) INSTANCE.loader.load(info.mainClass()).getDeclaredConstructor().newInstance();

                m.setInternalInfo(info);
                INSTANCE.modules.put(info.name().toLowerCase(), m);
            } catch (Exception e) {
                Log.error("Module load failed: " + e.getMessage());
            }
        }
    }

    public static void enableModules(Object core) {
        INSTANCE.activeCoreRef = core;
        int enabled = 0;
        for (Map.Entry<String, ForgeModule> entry : INSTANCE.modules.entrySet()) {
            ForgeModule module = entry.getValue();
            try {
                module.setJda(core instanceof ShardManager sm ? sm.getShardById(0) : (JDA) core);
                module.onEnable();

                module.getListeners().forEach(l -> {
                    if (core instanceof ShardManager sm) sm.addEventListener(l);
                    else if (core instanceof JDA jda) jda.addEventListener(l);
                });

                INSTANCE.enabledState.put(entry.getKey(), true);
                enabled++;
            } catch (Exception e) {
                INSTANCE.enabledState.put(entry.getKey(), false);
                Log.error("Failed to enable module '" + module.name() + "': " + e.getMessage());
            }
        }
        Log.done(enabled + " module(s) enabled.");
        syncSlashCommands(core);
    }

    public static List<Modules.Info> listInfo() {
        List<Modules.Info> out = new ArrayList<>();
        for (Map.Entry<String, ForgeModule> entry : INSTANCE.modules.entrySet()) {
            ForgeModule m = entry.getValue();
            boolean ok = INSTANCE.enabledState.getOrDefault(entry.getKey(), false);
            out.add(new Modules.Info(m.name(), m.version(), m.author(), ok));
        }
        return out;
    }

    public static void disableModules() {
        for (ForgeModule module : INSTANCE.modules.values()) {
            try {
                module.onDisable();

                if (INSTANCE.activeCoreRef != null) {
                    module.getListeners().forEach(l -> {
                        if (INSTANCE.activeCoreRef instanceof ShardManager sm) sm.removeEventListener(l);
                        else if (INSTANCE.activeCoreRef instanceof JDA jda) jda.removeEventListener(l);
                    });
                }
            } catch (Exception ignored) {}
        }
        Event.clearAllListeners();
        INSTANCE.loader.close();
        INSTANCE.modules.clear();
    }

    /** Disables, reloads from disk and re-enables every module without restarting the gateway. */
    public static void reload() {
        Log.warn("Reloading modules...");
        Object activeCore = INSTANCE.activeCoreRef;
        disableModules();

        // Fresh classloader so updated jars are picked up.
        INSTANCE.loader = new JarLoader(new File("modules"));

        loadModules();
        if (activeCore != null) {
            enableModules(activeCore);
        }
        Log.done("Modules reloaded.");
    }

    public static Collection<ForgeModule> getModules() {
        return Collections.unmodifiableCollection(INSTANCE.modules.values());
    }

    private static void syncSlashCommands(Object core) {
        List<CommandData> allCommands = new ArrayList<>();
        INSTANCE.modules.values().forEach(m -> allCommands.addAll(m.getCommands()));
        if (allCommands.isEmpty()) return;

        JDA targetJda = (core instanceof ShardManager sm)
                ? (sm.getShards().isEmpty() ? null : sm.getShards().getFirst())
                : (JDA) core;
        if (targetJda == null) return;

        targetJda.updateCommands().addCommands(allCommands).queue(
                s -> Log.done("Synchronized " + allCommands.size() + " global slash commands."),
                e -> Log.error("Failed to sync slash commands: " + e.getMessage())
        );
    }
}