package me.vasir.jdaforge.internal.module;

import java.util.List;
import java.util.Map;

/** Metadata read from a module's {@code module.yml}. Internal. */
record ModuleInfo(String name, String version, String author, String mainClass, List<String> dependencies) {

    @SuppressWarnings("unchecked")
    static ModuleInfo from(Map<String, Object> cfg) {
        return new ModuleInfo(
                String.valueOf(cfg.get("name")),
                String.valueOf(cfg.getOrDefault("version", "1.0")),
                String.valueOf(cfg.getOrDefault("author", "Unknown")),
                String.valueOf(cfg.get("main")),
                (List<String>) cfg.getOrDefault("depend", List.of())
        );
    }
}