package me.vasir.jdaforge.util;

public final class Discords {

    private Discords() {}

    /** Tests whether the id is a valid Discord snowflake. */
    public static boolean isValidSnowflake(String id) {
        if (Checks.isEmpty(id)) return false;
        return id.matches("\\d{17,19}");
    }

    /** Strips user/channel/role mentions (e.g. <@123>) from text. */
    public static String cleanMentions(String text) {
        if (text == null) return "";
        return text.replaceAll("<@!?\\d+>|<#\\d+>|<@&\\d+>", "");
    }
}