package dev.javaroot.antixray.clr;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Hex {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern AMP_HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern BARE_HEX = Pattern.compile("(?<![<:])#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY_CODE = Pattern.compile("&([0-9a-fA-Fk-oK-OrR])");
    private static final Pattern PALETTE_TOKEN = Pattern.compile("%([a-zA-Z0-9_-]+)%");

    private final Map<String, String> palette;

    public Hex(Map<String, String> palette) {
        this.palette = palette == null ? Map.of() : Map.copyOf(palette);
    }

    public static Hex empty() {
        return new Hex(Collections.emptyMap());
    }

    public Component parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return MINI.deserialize(toMiniMessage(raw));
    }

    public String toMiniMessage(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = replacePalette(raw);
        text = AMP_HEX.matcher(text).replaceAll("<#$1>");
        text = BARE_HEX.matcher(text).replaceAll("<#$1>");
        return replaceLegacyCodes(text);
    }

    private String replacePalette(String input) {
        if (palette.isEmpty()) {
            return input;
        }
        Matcher matcher = PALETTE_TOKEN.matcher(input);
        StringBuilder out = new StringBuilder(input.length());
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = palette.get(key);
            matcher.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : matcher.group(0)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String replaceLegacyCodes(String input) {
        Matcher matcher = LEGACY_CODE.matcher(input);
        StringBuilder out = new StringBuilder(input.length());
        while (matcher.find()) {
            String tag = legacyTag(Character.toLowerCase(matcher.group(1).charAt(0)));
            matcher.appendReplacement(out, Matcher.quoteReplacement(tag));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String legacyTag(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> "";
        };
    }
}
