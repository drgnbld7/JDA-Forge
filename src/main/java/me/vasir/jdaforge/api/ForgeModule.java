package me.vasir.jdaforge.api;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

/**
 * Base class every JDA-Forge module extends. Register commands and listeners inside {@link #onEnable()}
 * and open configs with {@link #config(String)}. Metadata and the {@link JDA} handle are injected by
 * the framework.
 */
public abstract class ForgeModule {

    private Object infoRef; // internal ModuleInfo, read reflectively to keep it off the public API
    private JDA jda;
    private final List<CommandData> commands = new CopyOnWriteArrayList<>();
    private final List<Object> listeners = new CopyOnWriteArrayList<>();

    public abstract void onEnable();
    public void onDisable() {}

    public final JDA jda() { return jda; }
    public final Placeholder placeholders() { return Placeholder.getInstance(); }

    public final String name() { return invokeInfoMethod("name"); }
    public final String version() { return invokeInfoMethod("version"); }
    public final String author() { return invokeInfoMethod("author"); }

    protected final void registerCommand(CommandData command) {
        if (command != null) commands.add(command);
    }

    protected final void registerListener(Object listener) {
        if (listener != null) listeners.add(listener);
    }

    protected final <T> void subscribe(Class<T> type, Consumer<T> listener) {
        Event.subscribe(type, listener);
    }

    protected final Config config(String file) {
        return Config.register(getClass(), null, file, true);
    }

    protected final Config config(String file, boolean recreate) {
        return Config.register(getClass(), null, file, recreate);
    }

    protected final Config config(String subPath, String file, boolean recreate) {
        return Config.register(getClass(), subPath, file, recreate);
    }

    // Framework-internal accessors used during module injection.
    public final List<CommandData> getCommands() { return commands; }
    public final List<Object> getListeners() { return listeners; }
    public final void setInternalInfo(Object info) { this.infoRef = info; }
    public final void setJda(JDA jda) { this.jda = jda; }

    private String invokeInfoMethod(String methodName) {
        if (infoRef == null) return "Unknown";
        try {
            Method method = infoRef.getClass().getMethod(methodName);
            method.setAccessible(true);
            return String.valueOf(method.invoke(infoRef));
        } catch (Exception e) {
            return "Unknown";
        }
    }
}