package dev.javaroot.antixray.cfg;

import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

public final class MatParse {

    private MatParse() {
    }

    public static boolean isBlockMat(Material mat) {
        // Material#isBlock touches Paper RegistryAccess, which is absent in headless JUnit tests
        return Bukkit.getServer() == null || mat.isBlock();
    }

    public static Material matchBlock(String raw, Material fallback, String ctx, Logger log) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material mat = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (mat == null || !isBlockMat(mat)) {
            if (log != null) {
                log.warning("Unknown block '" + raw + "' in " + ctx);
            }
            return fallback;
        }
        return mat;
    }

    public static Material matchMat(String raw, String ctx, Logger log) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Material mat = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (mat == null && log != null) {
            log.warning("Unknown material '" + raw + "' in " + ctx);
        }
        return mat;
    }

    public static Set<Material> parseSet(List<String> names, String ctx, Logger log) {
        if (names == null || names.isEmpty()) {
            return Set.of();
        }
        EnumSet<Material> set = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material mat = matchMat(name, ctx, log);
            if (mat != null) {
                set.add(mat);
            }
        }
        return set.isEmpty() ? Set.of() : Collections.unmodifiableSet(set);
    }

    public static List<Material> parseList(List<String> names, String ctx, Logger log) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<Material> list = new ArrayList<>(names.size());
        for (String name : names) {
            Material mat = matchMat(name, ctx, log);
            if (mat != null) {
                list.add(mat);
            }
        }
        return list.isEmpty() ? List.of() : Collections.unmodifiableList(list);
    }

    public static Set<Material> parsePatterns(List<String> entries, String ctx, Logger log) {
        if (entries == null || entries.isEmpty()) {
            return Set.of();
        }
        EnumSet<Material> out = EnumSet.noneOf(Material.class);
        for (String raw : entries) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String token = raw.trim().toUpperCase(Locale.ROOT);
            if (token.startsWith("*_")) {
                String suffix = token.substring(1);
                String exact = token.substring(2);
                for (Material mat : Material.values()) {
                    String name = mat.name();
                    if (!mat.isLegacy() && isBlockMat(mat) && (name.endsWith(suffix) || name.equals(exact))) {
                        out.add(mat);
                    }
                }
                continue;
            }
            Material mat = Material.matchMaterial(token);
            if (mat != null && isBlockMat(mat)) {
                out.add(mat);
            } else if (log != null) {
                log.warning("Unknown block '" + raw + "' in " + ctx);
            }
        }
        return out.isEmpty() ? Set.of() : Collections.unmodifiableSet(out);
    }
}
