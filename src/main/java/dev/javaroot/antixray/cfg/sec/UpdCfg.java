package dev.javaroot.antixray.cfg.sec;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record UpdCfg(
        boolean enabled,
        boolean notifyPlayers,
        List<String> message
) {
    public static UpdCfg load(FileConfiguration raw) {
        boolean enabled = raw.getBoolean("update-checker.enabled", true);
        boolean notify = raw.getBoolean("update-checker.notify-players", true);
        List<String> lines;
        if (raw.isList("update-checker.message")) {
            lines = List.copyOf(raw.getStringList("update-checker.message"));
        } else if (raw.isString("update-checker.message")) {
            String single = raw.getString("update-checker.message", "");
            lines = single.isEmpty() ? List.of() : List.of(single);
        } else {
            lines = List.of();
        }
        return new UpdCfg(enabled, notify, lines);
    }
}
