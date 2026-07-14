package me.vasir.jdaforge.util;

import java.util.Collection;
import java.util.Map;

public final class Checks {

    private Checks() {}

    public static boolean isEmpty(Collection<?> collection) { return collection == null || collection.isEmpty(); }
    public static boolean isEmpty(Map<?, ?> map) { return map == null || map.isEmpty(); }
    public static boolean isEmpty(String str) { return str == null || str.trim().isEmpty(); }
}