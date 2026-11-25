package dev.javaroot.antixray.cfg.sec;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record PunCfg(
        int triggerCount,
        int resetAfterMin,
        boolean resetOnPunish,
        boolean alertFirstHit,
        boolean alertSecondHit,
        boolean alertPunish,
        boolean logConsole,
        List<String> cmds
) {
    public static PunCfg load(FileConfiguration raw) {
        return new PunCfg(
                Math.max(1, raw.getInt("punishment.trigger-count", 3)),
                Math.max(0, raw.getInt("punishment.count-reset-after-minutes", 60)),
                raw.getBoolean("punishment.reset-count-on-punish", true),
                raw.getBoolean("punishment.alert-on-first-hit", true),
                raw.getBoolean("punishment.alert-on-second-hit", true),
                raw.getBoolean("punishment.alert-on-punish", true),
                raw.getBoolean("punishment.log-to-console", true),
                List.copyOf(raw.getStringList("punishment.commands"))
        );
    }
}
