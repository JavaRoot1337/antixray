package dev.javaroot.antixray.cfg;

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

    public static void ensureUpdated(Plugin plugin, String fileName, Set<String> dynamicMapSections) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
            return;
        }

        String defaultContent = readResource(plugin, fileName);
        if (defaultContent == null || defaultContent.isBlank()) {
            return;
        }

        String currentContent;
        try {
            currentContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to read " + fileName + " for migration", ex);
            return;
        }

        String merged = mergeYaml(currentContent, defaultContent, dynamicMapSections);
        if (merged == null || merged.equals(currentContent)) {
            return;
        }

        try {
            Files.writeString(file.toPath(), merged, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save migrated " + fileName, ex);
        }
    }

    static String mergeYaml(String currentContent, String defaultContent, Set<String> dynamicMapSections) {
        YamlConfiguration current = YamlConfiguration.loadConfiguration(new StringReader(currentContent));
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new StringReader(defaultContent));

        List<String> missingPaths = collectMissingPaths(current, defaults, dynamicMapSections);
        if (missingPaths.isEmpty()) {
            return currentContent;
        }

        String lineMerged = insertMissingBlocks(currentContent, defaultContent, missingPaths);
        if (lineMerged != null && allPathsPresent(lineMerged, missingPaths)) {
            return lineMerged;
        }

        for (String path : missingPaths) {
            current.set(path, defaults.get(path));
        }
        return current.saveToString();
    }

    private static List<String> collectMissingPaths(
            YamlConfiguration current,
            YamlConfiguration defaults,
            Set<String> dynamicMapSections
    ) {
        Set<String> coveredRoots = new LinkedHashSet<>();
        List<String> missing = new ArrayList<>();

        for (String path : defaults.getKeys(true)) {
            if (isUnderAnyRoot(path, coveredRoots)) {
                continue;
            }
            if (isDynamicChild(path, current, dynamicMapSections)) {
                continue;
            }
            if (!current.contains(path)) {
                missing.add(path);
                coveredRoots.add(path);
                continue;
            }
            Object defVal = defaults.get(path);
            Object curVal = current.get(path);
            if (defVal instanceof ConfigurationSection && !(curVal instanceof ConfigurationSection)) {
                missing.add(path);
                coveredRoots.add(path);
            }
        }
        return missing;
    }

    private static boolean isDynamicChild(
            String path,
            YamlConfiguration current,
            Set<String> dynamicMapSections
    ) {
        if (dynamicMapSections == null || dynamicMapSections.isEmpty()) {
            return false;
        }
        for (String root : dynamicMapSections) {
            if (path.startsWith(root + ".") && current.isConfigurationSection(root)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnderAnyRoot(String path, Set<String> roots) {
        for (String root : roots) {
            if (path.equals(root) || path.startsWith(root + ".")) {
                return true;
            }
        }
        return false;
    }

    private static String insertMissingBlocks(
            String currentContent,
            String defaultContent,
            List<String> missingPaths
    ) {
        String newline = currentContent.contains("\r\n") ? "\r\n" : "\n";
        List<String> curLines = splitLines(currentContent);
        List<String> defLines = splitLines(defaultContent);

        for (String path : missingPaths) {
            List<String> block = extractBlock(defLines, path);
            if (block.isEmpty()) {
                return null;
            }
            int dot = path.lastIndexOf('.');
            if (dot < 0) {
                if (!curLines.isEmpty() && !curLines.get(curLines.size() - 1).isBlank()) {
                    curLines.add("");
                }
                curLines.addAll(block);
            } else {
                String parentPath = path.substring(0, dot);
                int insertAt = findSectionEnd(curLines, parentPath);
                if (insertAt < 0) {
                    return null;
                }
                curLines.addAll(insertAt, block);
            }
        }

        String joined = String.join(newline, curLines);
        if (currentContent.endsWith("\n") && !joined.endsWith("\n")) {
            joined += newline;
        }
        return joined;
    }

    private static List<String> extractBlock(List<String> lines, String path) {
        int keyLine = findKeyLine(lines, path);
        if (keyLine < 0) {
            return List.of();
        }
        int indent = leadingSpaces(lines.get(keyLine));

        int start = keyLine;
        while (start > 0) {
            String prev = lines.get(start - 1);
            String trimmed = prev.trim();
            if (trimmed.startsWith("#") && leadingSpaces(prev) == indent) {
                start--;
            } else {
                break;
            }
        }

        int end = keyLine + 1;
        while (end < lines.size()) {
            String line = lines.get(end);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                end++;
                continue;
            }
            if (leadingSpaces(line) > indent) {
                end++;
                continue;
            }
            if (trimmed.startsWith("-") && leadingSpaces(line) == indent) {
                end++;
                continue;
            }
            break;
        }
        while (end > keyLine + 1 && lines.get(end - 1).trim().isEmpty()) {
            end--;
        }

        return new ArrayList<>(lines.subList(start, end));
    }

    private static int findSectionEnd(List<String> lines, String parentPath) {
        int parentLine = findKeyLine(lines, parentPath);
        if (parentLine < 0) {
            return -1;
        }
        int indent = leadingSpaces(lines.get(parentLine));
        int end = parentLine + 1;
        int lastNonBlank = parentLine + 1;
        while (end < lines.size()) {
            String line = lines.get(end);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                end++;
                continue;
            }
            if (leadingSpaces(line) <= indent) {
                break;
            }
            end++;
            lastNonBlank = end;
        }
        return lastNonBlank;
    }

    private static int findKeyLine(List<String> lines, String path) {
        String[] segments = path.split("\\.");
        int depth = 0;
        int[] indents = new int[segments.length];
        for (int i = 0; i < indents.length; i++) {
            indents[i] = -1;
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
                continue;
            }
            int indent = leadingSpaces(line);
            while (depth > 0 && indent <= indents[depth - 1]) {
                depth--;
            }
            if (depth >= segments.length) {
                continue;
            }
            if (matchesYamlKey(trimmed, segments[depth])) {
                indents[depth] = indent;
                depth++;
                if (depth == segments.length) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean matchesYamlKey(String trimmedLine, String key) {
        String candidate = key + ":";
        if (trimmedLine.startsWith(candidate)) {
            return trimmedLine.length() == candidate.length()
                    || Character.isWhitespace(trimmedLine.charAt(candidate.length()));
        }
        String quoted = "\"" + key + "\":";
        if (trimmedLine.startsWith(quoted)) {
            return true;
        }
        String singleQuoted = "'" + key + "':";
        return trimmedLine.startsWith(singleQuoted);
    }

    private static boolean allPathsPresent(String yamlText, List<String> paths) {
        try {
            YamlConfiguration parsed = YamlConfiguration.loadConfiguration(new StringReader(yamlText));
            for (String path : paths) {
                if (!parsed.contains(path)) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static List<String> splitLines(String text) {
        String[] arr = text.split("\\r?\\n", -1);
        List<String> list = new ArrayList<>(arr.length);
        for (String line : arr) {
            list.add(line);
        }
        if (text.endsWith("\n") && !list.isEmpty() && list.get(list.size() - 1).isEmpty()) {
            list.remove(list.size() - 1);
        }
        return list;
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String readResource(Plugin plugin, String fileName) {
        try (InputStream in = plugin.getResource(fileName)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to read default resource " + fileName, ex);
            return null;
        }
    }
}
