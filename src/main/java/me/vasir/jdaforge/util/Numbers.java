package me.vasir.jdaforge.util;

import java.util.concurrent.ThreadLocalRandom;

public final class Numbers {

    private Numbers() {}

    /** Random int between min and max (both inclusive). */
    public static int randomInt(int min, int max) {
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        return str.matches("-?\\d+(\\.\\d+)?");
    }
}