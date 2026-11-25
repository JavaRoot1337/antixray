package dev.javaroot.antixray.cfg.sec;

import org.bukkit.configuration.file.FileConfiguration;

public record GenCfg(
        boolean debug,
        boolean ignoreCreative,
        boolean ignoreSpectator,
        boolean bypassPerm,
        String dbFile,
        int retentionDays
) {
    public static GenCfg load(FileConfiguration raw) {
        return new GenCfg(
                raw.getBoolean("general.debug", false),
                raw.getBoolean("general.ignore-creative", true),
                raw.getBoolean("general.ignore-spectator", true),
                raw.getBoolean("general.bypass-permission-enabled", true),
                raw.getString("general.database-file", "data.db"),
                Math.max(0, raw.getInt("general.history-retention-days", 30))
        );
    }
}
