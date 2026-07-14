package me.vasir.jdaforge.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.ShardManager;

/**
 * Central registry for translating percent-bounded placeholders (e.g., %bot_ping%).
 * Ships with baseline built-in placeholders and allows modules to register custom extensions.
 */
public final class Placeholder {

    private static final Placeholder INSTANCE = new Placeholder();
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%");

    private final Map<String, Object> registry = Collections.synchronizedMap(new LinkedHashMap<>());
    private Object activeJdaCore; // injected once the gateway connection is up

    private Placeholder() {
        // Built-in placeholders shipped with the framework.
        register("%bot_ping%", ctx -> {
            JDA jda = getTargetJda();
            return jda != null ? jda.getGatewayPing() + "ms" : "0ms";
        });

        register("%member_count%", ctx -> {
            long total = 0;
            List<Guild> guilds = getPlatformGuilds();
            if (guilds != null) {
                for (Guild g : guilds) total += g.getMemberCount();
            }
            return total;
        });

        register("%online_count%", ctx -> {
            long onlineCount = 0;
            List<Guild> guilds = getPlatformGuilds();
            if (guilds != null) {
                for (Guild g : guilds) {
                    onlineCount += g.getMembers().stream()
                            .filter(m -> m.getOnlineStatus() != OnlineStatus.OFFLINE && m.getOnlineStatus() != OnlineStatus.INVISIBLE)
                            .count();
                }
            }
            return onlineCount;
        });
    }

    public static Placeholder getInstance() {
        return INSTANCE;
    }

    /** Registers a {@code Function<Object, Object>} (context -> value) for a tag. */
    public void register(String tag, Function<Object, Object> functionalValue) {
        register(tag, (Object) functionalValue);
    }

    /** Registers a constant value for a placeholder tag. */
    public void register(String tag, Object value) {
        if (tag == null || value == null) return;
        String lowerTag = tag.toLowerCase();
        if (registry.containsKey(lowerTag)) {
            Log.warn("Placeholder '" + tag + "' is being overwritten.");
        }
        registry.put(lowerTag, value);
    }

    public void unregister(String tag) {
        if (tag != null) registry.remove(tag.toLowerCase());
    }

    /** Resolves and replaces every known tag found in {@code input}. */
    @SuppressWarnings("unchecked")
    public String translate(String input, Object context) {
        if (input == null || input.isEmpty()) return input;

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String fullMatch = matcher.group(0);
            String insideTag = matcher.group(1).toLowerCase();
            Object rawValue = null;

            synchronized (registry) {
                if (registry.containsKey(fullMatch.toLowerCase())) {
                    rawValue = registry.get(fullMatch.toLowerCase());
                } else if (registry.containsKey(insideTag)) {
                    rawValue = registry.get(insideTag);
                } else {
                    for (String key : registry.keySet()) {
                        String cleanKey = key.replace("%", "");
                        if (!cleanKey.isEmpty() && insideTag.startsWith(cleanKey)) {
                            rawValue = registry.get(key);
                            break;
                        }
                    }
                }
            }

            if (rawValue != null) {
                String replacement;
                if (rawValue instanceof Function) {
                    Object result = ((Function<Object, Object>) rawValue).apply(context);
                    replacement = result != null ? result.toString() : "";
                } else {
                    replacement = rawValue.toString();
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(fullMatch));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** Internal: links the active gateway core so built-in placeholders can query it. */
    public void setCoreBridge(Object core) {
        this.activeJdaCore = core;
    }

    private JDA getTargetJda() {
        if (activeJdaCore instanceof ShardManager sm) return sm.getShards().isEmpty() ? null : sm.getShards().getFirst();
        if (activeJdaCore instanceof JDA jda) return jda;
        return null;
    }

    private List<Guild> getPlatformGuilds() {
        if (activeJdaCore instanceof ShardManager sm) return sm.getGuilds();
        if (activeJdaCore instanceof JDA jda) return jda.getGuilds();
        return null;
    }
}