package me.vasir.jdaforge.util;

import java.awt.Color;

public final class Colors {

    private Colors() {}

    /** Parses hex color strings (e.g. "0xFF5555", "#FF5555", "FF5555") into a Color, falling back to white. */
    public static Color fromHex(String hex) {
        if (Checks.isEmpty(hex)) return Color.WHITE;
        String clean = hex.replace("#", "").replace("0x", "").trim();
        try {
            long colorVal = Long.parseLong(clean, 16);
            if (clean.length() == 8) {
                return new Color((int) colorVal, true);
            }
            return new Color((int) colorVal);
        } catch (IllegalArgumentException e) {
            return Color.WHITE;
        }
    }
}