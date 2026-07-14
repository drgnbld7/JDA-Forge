package me.vasir.jdaforge.internal.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import me.vasir.jdaforge.api.Config;
import me.vasir.jdaforge.api.Log;
import me.vasir.jdaforge.api.Placeholder;
import me.vasir.jdaforge.api.Bot;
import me.vasir.jdaforge.api.Presence;
import me.vasir.jdaforge.internal.logging.LogAdapter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

/**
 * Opens the Discord gateway connection (a single {@link JDA}, or a {@link ShardManager} when
 * sharded), applies the configured intents, and runs the presence rotator. Internal.
 */
public final class BotEngine {

    private static final BotEngine INSTANCE = new BotEngine();
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Forge-Presence-Rotator");
        t.setDaemon(true);
        return t;
    });

    private final List<Map<String, Object>> activities = new CopyOnWriteArrayList<>();
    private int interval = 60;
    private int index = 0;
    private Object gatewayCore;
    private OnlineStatus currentStatus = OnlineStatus.ONLINE;

    private static final Map<String, GatewayIntent> INTENT_MAP = Map.ofEntries(
            Map.entry("message-content", GatewayIntent.MESSAGE_CONTENT),
            Map.entry("guild-members", GatewayIntent.GUILD_MEMBERS),
            Map.entry("guild-presences", GatewayIntent.GUILD_PRESENCES),
            Map.entry("guild-moderation", GatewayIntent.GUILD_MODERATION),
            Map.entry("guild-webhooks", GatewayIntent.GUILD_WEBHOOKS),
            Map.entry("guild-invites", GatewayIntent.GUILD_INVITES),
            Map.entry("guild-voice-states", GatewayIntent.GUILD_VOICE_STATES),
            Map.entry("guild-messages", GatewayIntent.GUILD_MESSAGES),
            Map.entry("guild-message-reactions", GatewayIntent.GUILD_MESSAGE_REACTIONS),
            Map.entry("guild-message-typing", GatewayIntent.GUILD_MESSAGE_TYPING),
            Map.entry("direct-messages", GatewayIntent.DIRECT_MESSAGES),
            Map.entry("direct-message-reactions", GatewayIntent.DIRECT_MESSAGE_REACTIONS),
            Map.entry("direct-message-typing", GatewayIntent.DIRECT_MESSAGE_TYPING),
            Map.entry("guild-scheduled-events", GatewayIntent.SCHEDULED_EVENTS),
            Map.entry("auto-moderation-configuration", GatewayIntent.AUTO_MODERATION_CONFIGURATION),
            Map.entry("auto-moderation-execution", GatewayIntent.AUTO_MODERATION_EXECUTION)
    );

    private BotEngine() {}

    public static BotEngine getInstance() { return INSTANCE; }

    @SuppressWarnings("unchecked")
    public void boot(Config config) throws Exception {
        if (!STARTED.compareAndSet(false, true)) return;

        Config settings = config.section("settings");
        Config presenceCfg = config.section("presence");

        this.currentStatus = parseStatus(settings.getString("status", "ONLINE"));
        this.interval = Math.max(1, presenceCfg.getInt("update-interval-seconds", 60));

        // Link the public Presence controller to this engine.
        Presence.getInstance().setEngineLink(this, currentStatus);

        for (Object raw : presenceCfg.getList("activities")) {
            if (raw instanceof Map) activities.add((Map<String, Object>) raw);
            else if (raw instanceof String s) activities.add(Map.of("type", "PLAYING", "text", s));
        }

        String token = settings.getString("token");
        Config intents = config.section("intents");
        boolean sharded = settings.getBoolean("sharding.enabled", false);
        boolean debug = settings.getBoolean("debug-mode", false);

        // In debug mode, forward DEBUG-level logs from bridged libraries (JDA, HikariCP, JDBI).
        if (debug) LogAdapter.setThreshold("DEBUG");

        JDA localJda = null;
        ShardManager localSm = null;

        if (sharded) {
            DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.createDefault(token);
            applyIntents(builder, intents);
            applyMemberCaching(builder, intents);
            int total = settings.getInt("sharding.total-shards", -1);
            if (total > 0) builder.setShardsTotal(total);
            localSm = builder.build();
            this.gatewayCore = localSm;
        } else {
            JDABuilder builder = JDABuilder.createDefault(token);
            applyIntents(builder, intents);
            applyMemberCaching(builder, intents);
            localJda = builder.build().awaitReady();
            this.gatewayCore = localJda;
        }

        Bot.init(localJda, localSm, settings.getString("bot-name", "Forge-Bot"), debug);
        Placeholder.getInstance().setCoreBridge(gatewayCore);

        if (!activities.isEmpty()) {
            executor.scheduleAtFixedRate(this::rotate, 5, interval, TimeUnit.SECONDS);
        }
        Log.done(Bot.name() + " connected to the gateway.");
    }

    public void shutdown() {
        executor.shutdown();
        Object core = Bot.core();
        if (core instanceof ShardManager sm) sm.shutdown();
        if (core instanceof JDA jda) jda.shutdown();
        Bot.clear();
        STARTED.set(false);
    }

    /** Invoked reflectively by {@link Presence#status(OnlineStatus)}. */
    @SuppressWarnings("unused")
    private void updateStatusDirectly(OnlineStatus status) {
        this.currentStatus = status;
        if (gatewayCore instanceof ShardManager sm) sm.setStatus(status);
        else if (gatewayCore instanceof JDA jda) jda.getPresence().setStatus(status);
    }

    private void rotate() {
        if (activities.isEmpty()) return;
        Map<String, Object> data = activities.get(index);
        index = (index + 1) % activities.size();

        String text = Placeholder.getInstance().translate(String.valueOf(data.getOrDefault("text", "")), null);
        Activity activity = createActivity(String.valueOf(data.getOrDefault("type", "PLAYING")), text, (String) data.get("url"));

        if (gatewayCore instanceof ShardManager sm) {
            sm.setStatus(currentStatus);
            sm.setActivity(activity);
        } else if (gatewayCore instanceof JDA jda) {
            jda.getPresence().setPresence(currentStatus, activity);
        }
    }

    private static void applyIntents(Object builder, Config intents) {
        INTENT_MAP.forEach((key, intent) -> {
            if (!intents.getBoolean(key, false)) return;
            if (builder instanceof JDABuilder j) j.enableIntents(intent);
            else if (builder instanceof DefaultShardManagerBuilder s) s.enableIntents(intent);
        });
    }

    /**
     * When the member/presence intents are enabled, turn on the caching JDA needs to actually make
     * member and online-status data available (e.g. for the %member_count% / %online_count%
     * placeholders). {@code createDefault} leaves member chunking and the online-status cache off.
     */
    private static void applyMemberCaching(Object builder, Config intents) {
        boolean members = intents.getBoolean("guild-members", false);
        boolean presences = intents.getBoolean("guild-presences", false);

        if (builder instanceof JDABuilder j) {
            if (members) {
                j.setChunkingFilter(ChunkingFilter.ALL);
                j.setMemberCachePolicy(MemberCachePolicy.ALL);
            }
            if (presences) j.enableCache(CacheFlag.ONLINE_STATUS);
        } else if (builder instanceof DefaultShardManagerBuilder s) {
            if (members) {
                s.setChunkingFilter(ChunkingFilter.ALL);
                s.setMemberCachePolicy(MemberCachePolicy.ALL);
            }
            if (presences) s.enableCache(CacheFlag.ONLINE_STATUS);
        }
    }

    private static OnlineStatus parseStatus(String s) {
        try { return OnlineStatus.valueOf(s.trim().toUpperCase()); } catch (Exception e) { return OnlineStatus.ONLINE; }
    }

    private static Activity createActivity(String type, String text, String url) {
        return switch (type.toUpperCase()) {
            case "NONE", "CLEAR" -> null;
            case "STREAMING" -> Activity.streaming(text, url != null ? url : "https://twitch.tv/discord");
            case "LISTENING" -> Activity.listening(text);
            case "WATCHING" -> Activity.watching(text);
            case "COMPETING" -> Activity.competing(text);
            default -> Activity.playing(text);
        };
    }
}