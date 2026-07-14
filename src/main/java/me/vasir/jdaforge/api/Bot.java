package me.vasir.jdaforge.api;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.sharding.ShardManager;

/**
 * Read-only view of the running bot: the active {@link JDA} / {@link ShardManager}, name and debug
 * flag. Values are populated once the gateway connection is established.
 */
public final class Bot {

    private static volatile JDA jda;
    private static volatile ShardManager shardManager;
    private static String name = "Forge-Bot";
    private static boolean debugMode = false;

    private Bot() {}

    public static JDA jda() { return jda; }
    public static ShardManager shardManager() { return shardManager; }
    public static Presence presence() { return Presence.getInstance(); }
    public static String name() { return name; }
    public static boolean isDebug() { return debugMode; }

    public static Object core() { return shardManager != null ? shardManager : jda; }

    public static JDA primaryJda() {
        if (jda != null) return jda;
        return shardManager != null ? shardManager.getShardById(0) : null;
    }

    /** Internal: populated by the framework once the connection is up. */
    public static void init(JDA jdaInstance, ShardManager smInstance, String botName, boolean debug) {
        jda = jdaInstance;
        shardManager = smInstance;
        name = botName;
        debugMode = debug;
    }

    public static void clear() {
        jda = null;
        shardManager = null;
    }
}