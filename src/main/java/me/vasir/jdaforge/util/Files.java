package me.vasir.jdaforge.util;

import me.vasir.jdaforge.api.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** File-system and compression helpers. */
public final class Files {

    private Files() {}

    /** Normalizes separators: backslash -> slash, collapses repeated slashes. */
    public static String cleanPath(String path) {
        if (Checks.isEmpty(path)) return "";
        return path.replace("\\", "/").replaceAll("/+", "/");
    }

    public static boolean ensureDirectoryExists(String path) {
        if (Checks.isEmpty(path)) return false;
        try {
            java.nio.file.Files.createDirectories(Paths.get(cleanPath(path)));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void deleteSafely(File file) {
        if (file == null || !file.exists()) return;
        try {
            java.nio.file.Files.deleteIfExists(file.toPath());
        } catch (IOException ignored) {}
    }

    /** Compresses a single file into the target .zip archive. */
    public static void zip(File source, File target) {
        if (source == null || !source.exists() || target == null) return;

        Path sourcePath = source.toPath();
        Path targetPath = target.toPath();

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(java.nio.file.Files.newOutputStream(targetPath)));
             BufferedInputStream bis = new BufferedInputStream(java.nio.file.Files.newInputStream(sourcePath))) {

            zos.putNextEntry(new ZipEntry(source.getName()));
            bis.transferTo(zos);
            zos.closeEntry();
        } catch (IOException e) {
            Log.error("Failed to archive file: " + source.getName());
        }
    }
}