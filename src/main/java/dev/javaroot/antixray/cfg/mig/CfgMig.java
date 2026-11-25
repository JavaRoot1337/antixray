package dev.javaroot.antixray.cfg.mig;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public final class CfgMig {

    private CfgMig() {
    }

    public static void ensureUpdated(Plugin plug, String fileName, Set<String> dynSections) {
        File file = new File(plug.getDataFolder(), fileName);
        if (!file.exists()) {
            plug.saveResource(fileName, false);
            return;
        }

        String defTxt = readResource(plug, fileName);
        if (defTxt == null || defTxt.isBlank()) {
            return;
        }

        String curTxt;
        try {
            curTxt = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plug.getLogger().log(Level.WARNING, "Failed to read " + fileName + " for migration", ex);
            return;
        }

        String merged = mergeYaml(curTxt, defTxt, dynSections);
        if (merged == null || merged.equals(curTxt)) {
            return;
        }

        try {
            Files.writeString(file.toPath(), merged, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plug.getLogger().log(Level.WARNING, "Failed to save migrated " + fileName, ex);
        }
    }

    public static String mergeYaml(String curTxt, String defTxt, Set<String> dynSections) {
        YamlConfiguration cur = YamlConfiguration.loadConfiguration(new StringReader(curTxt));
        YamlConfiguration def = YamlConfiguration.loadConfiguration(new StringReader(defTxt));

        List<String> missing = collectMissing(cur, def, dynSections);
        if (missing.isEmpty()) {
            return curTxt;
        }

        String lineMerged = YamlPatch.insertBlocks(curTxt, defTxt, missing);
        if (lineMerged != null && YamlPatch.allPresent(lineMerged, missing)) {
            return lineMerged;
        }

        for (String path : missing) {
            cur.set(path, def.get(path));
        }
        return cur.saveToString();
    }

    private static List<String> collectMissing(
            YamlConfiguration cur,
            YamlConfiguration def,
            Set<String> dynSections
    ) {
        Set<String> roots = new LinkedHashSet<>();
        List<String> missing = new ArrayList<>();

        for (String path : def.getKeys(true)) {
            if (underRoot(path, roots) || isDynChild(path, cur, dynSections)) {
                continue;
            }
            if (!cur.contains(path)) {
                missing.add(path);
                roots.add(path);
                continue;
            }
            if (def.get(path) instanceof ConfigurationSection && !(cur.get(path) instanceof ConfigurationSection)) {
                missing.add(path);
                roots.add(path);
            }
        }
        return missing;
    }

    private static boolean isDynChild(String path, YamlConfiguration cur, Set<String> dynSections) {
        if (dynSections == null || dynSections.isEmpty()) {
            return false;
        }
        for (String root : dynSections) {
            if (path.startsWith(root + ".") && cur.isConfigurationSection(root)) {
                return true;
            }
        }
        return false;
    }

    private static boolean underRoot(String path, Set<String> roots) {
        for (String root : roots) {
            if (path.equals(root) || path.startsWith(root + ".")) {
                return true;
            }
        }
        return false;
    }

    private static String readResource(Plugin plug, String fileName) {
        try (InputStream in = plug.getResource(fileName)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plug.getLogger().log(Level.WARNING, "Failed to read default resource " + fileName, ex);
            return null;
        }
    }
}
