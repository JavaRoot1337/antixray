package dev.javaroot.antixray.cfg.mig;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class YamlPatch {

    private YamlPatch() {
    }

    static String insertBlocks(String curTxt, String defTxt, List<String> missingPaths) {
        String nl = curTxt.contains("\r\n") ? "\r\n" : "\n";
        List<String> curLines = splitLines(curTxt);
        List<String> defLines = splitLines(defTxt);

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
                String parent = path.substring(0, dot);
                int idx = findSecEnd(curLines, parent);
                if (idx < 0) {
                    return null;
                }
                curLines.addAll(idx, block);
            }
        }

        String joined = String.join(nl, curLines);
        if (curTxt.endsWith("\n") && !joined.endsWith("\n")) {
            joined += nl;
        }
        return joined;
    }

    static boolean allPresent(String yamlTxt, List<String> paths) {
        try {
            YamlConfiguration parsed = YamlConfiguration.loadConfiguration(new StringReader(yamlTxt));
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

    private static List<String> extractBlock(List<String> lines, String path) {
        int keyLine = findKeyLine(lines, path);
        if (keyLine < 0) {
            return List.of();
        }
        int indent = leadSpaces(lines.get(keyLine));

        int start = keyLine;
        while (start > 0) {
            String prev = lines.get(start - 1);
            if (prev.trim().startsWith("#") && leadSpaces(prev) == indent) {
                start--;
            } else {
                break;
            }
        }

        int end = keyLine + 1;
        while (end < lines.size()) {
            String line = lines.get(end);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || leadSpaces(line) > indent
                    || (trimmed.startsWith("-") && leadSpaces(line) == indent)) {
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

    private static int findSecEnd(List<String> lines, String parent) {
        int pLine = findKeyLine(lines, parent);
        if (pLine < 0) {
            return -1;
        }
        int indent = leadSpaces(lines.get(pLine));
        int end = pLine + 1;
        int lastNonBlank = pLine + 1;
        while (end < lines.size()) {
            String line = lines.get(end);
            if (line.trim().isEmpty()) {
                end++;
                continue;
            }
            if (leadSpaces(line) <= indent) {
                break;
            }
            end++;
            lastNonBlank = end;
        }
        return lastNonBlank;
    }

    private static int findKeyLine(List<String> lines, String path) {
        String[] segs = path.split("\\.");
        int depth = 0;
        int[] indents = new int[segs.length];
        for (int i = 0; i < indents.length; i++) {
            indents[i] = -1;
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
                continue;
            }
            int indent = leadSpaces(line);
            while (depth > 0 && indent <= indents[depth - 1]) {
                depth--;
            }
            if (depth >= segs.length) {
                continue;
            }
            if (matchesKey(trimmed, segs[depth])) {
                indents[depth] = indent;
                depth++;
                if (depth == segs.length) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean matchesKey(String trimmedLine, String key) {
        String cand = key + ":";
        if (trimmedLine.startsWith(cand)) {
            return trimmedLine.length() == cand.length()
                    || Character.isWhitespace(trimmedLine.charAt(cand.length()));
        }
        return trimmedLine.startsWith("\"" + key + "\":") || trimmedLine.startsWith("'" + key + "':");
    }

    private static List<String> splitLines(String text) {
        String[] arr = text.split("\\r?\\n", -1);
        List<String> list = new ArrayList<>(arr.length);
        Collections.addAll(list, arr);
        if (text.endsWith("\n") && !list.isEmpty() && list.get(list.size() - 1).isEmpty()) {
            list.remove(list.size() - 1);
        }
        return list;
    }

    private static int leadSpaces(String line) {
        int cnt = 0;
        while (cnt < line.length() && line.charAt(cnt) == ' ') {
            cnt++;
        }
        return cnt;
    }
}
