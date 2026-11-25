package dev.javaroot.antixray.cfg.sec;

import org.bukkit.configuration.file.FileConfiguration;

public record ReactCfg(
        boolean enabled,
        int bufSize,
        double minTurnDeg,
        int turnWindowMs,
        double stabDegPerSec,
        double minDist,
        double maxDist,
        long minReactMs,
        long maxReactMs,
        double maxAimErrDeg,
        int minSolidOnRay,
        double orthoTolDeg,
        double orthoMult,
        double weight,
        int streakWindowSec,
        double streakMult
) {
    public static ReactCfg load(FileConfiguration raw) {
        String base = "suspicion.heuristics.reaction-to-ore.";
        double minDist = Math.max(1.0, raw.getDouble(base + "min-distance-blocks", 4.5));
        long minMs = Math.max(200L, raw.getLong(base + "min-reaction-ms", 1200L));

        return new ReactCfg(
                raw.getBoolean(base + "enabled", true),
                Math.max(16, raw.getInt(base + "rotation-buffer-size", 64)),
                Math.max(5.0, raw.getDouble(base + "min-turn-angle-deg", 28.0)),
                Math.max(100, raw.getInt(base + "turn-window-ms", 550)),
                Math.max(1.0, raw.getDouble(base + "stabilize-deg-per-sec", 35.0)),
                minDist,
                Math.max(minDist, raw.getDouble(base + "max-distance-blocks", 16.0)),
                minMs,
                Math.max(minMs, raw.getLong(base + "max-reaction-ms", 18000L)),
                Math.max(1.0, raw.getDouble(base + "max-aim-error-deg", 16.0)),
                Math.max(1, raw.getInt(base + "min-solid-blocks-on-ray", 3)),
                Math.max(0.0, raw.getDouble(base + "orthogonal-turn-tolerance-deg", 6.0)),
                Math.max(0.0, raw.getDouble(base + "orthogonal-multiplier", 0.4)),
                Math.max(0.0, raw.getDouble(base + "weight", 55.0)),
                Math.max(1, raw.getInt(base + "repeat-streak-window-seconds", 180)),
                Math.max(1.0, raw.getDouble(base + "repeat-streak-multiplier", 1.4))
        );
    }
}
