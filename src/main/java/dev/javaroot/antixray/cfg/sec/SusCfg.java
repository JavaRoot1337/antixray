package dev.javaroot.antixray.cfg.sec;

import org.bukkit.configuration.file.FileConfiguration;

public record SusCfg(
        double triggerThreshold,
        double maxScore,
        int watchDurationSec,
        int ringBufferSize,
        int decayIntervalSec,
        double decayAmount,
        int straightStoneBlocks,
        double straightStoneDecay,
        int veinRadius,
        int veinMergeWindowSec,
        boolean caveFilter,
        int caveCheckRadius,
        int caveMinAir,
        int brokenMemSec,
        double exposedOreMult,
        boolean sharpTurnEnabled,
        double minYawChangeDeg,
        int minPitchDeltaY,
        int lookbackMin,
        int lookbackMax,
        double sharpTurnWeight,
        boolean rapidVeinsEnabled,
        int maxSecBetweenVeins,
        double minDistBetweenVeins,
        double rapidVeinsWeight,
        boolean oreRatioEnabled,
        int oreRatioWindow,
        int minBuriedVeins,
        double oreRatioWeight,
        boolean darknessEnabled,
        int maxLightLevel,
        boolean ignoreNightVision,
        double darknessBonus
) {
    public static SusCfg load(FileConfiguration raw) {
        double threshold = Math.max(1.0, raw.getDouble("suspicion.trigger-threshold", 100.0));
        int lbMin = Math.max(1, raw.getInt("suspicion.heuristics.sharp-turn-to-ore.lookback-min-steps", 3));

        return new SusCfg(
                threshold,
                Math.max(threshold, raw.getDouble("suspicion.max-score", 250.0)),
                Math.max(1, raw.getInt("suspicion.watch-duration-seconds", 300)),
                Math.max(4, raw.getInt("suspicion.ring-buffer-size", 20)),
                Math.max(1, raw.getInt("suspicion.decay.interval-seconds", 15)),
                Math.max(0.0, raw.getDouble("suspicion.decay.amount-per-interval", 6.0)),
                Math.max(1, raw.getInt("suspicion.decay.straight-stone-blocks-for-bonus-decay", 15)),
                Math.max(0.0, raw.getDouble("suspicion.decay.straight-stone-decay-amount", 8.0)),
                Math.max(1, raw.getInt("suspicion.vein-grouping.radius", 3)),
                Math.max(1, raw.getInt("suspicion.vein-grouping.merge-window-seconds", 8)),
                raw.getBoolean("suspicion.cave-filter.enabled", true),
                Math.max(1, raw.getInt("suspicion.cave-filter.check-radius", 2)),
                Math.max(1, raw.getInt("suspicion.cave-filter.min-foreign-air-blocks", 3)),
                Math.max(1, raw.getInt("suspicion.cave-filter.player-broken-memory-seconds", 40)),
                Math.max(0.0, raw.getDouble("suspicion.cave-filter.exposed-ore-multiplier", 0.1)),
                raw.getBoolean("suspicion.heuristics.sharp-turn-to-ore.enabled", true),
                raw.getDouble("suspicion.heuristics.sharp-turn-to-ore.min-yaw-change-degrees", 35.0),
                Math.max(1, raw.getInt("suspicion.heuristics.sharp-turn-to-ore.min-pitch-vertical-delta", 2)),
                lbMin,
                Math.max(lbMin, raw.getInt("suspicion.heuristics.sharp-turn-to-ore.lookback-max-steps", 12)),
                Math.max(0.0, raw.getDouble("suspicion.heuristics.sharp-turn-to-ore.weight", 55.0)),
                raw.getBoolean("suspicion.heuristics.rapid-consecutive-veins.enabled", true),
                Math.max(1, raw.getInt("suspicion.heuristics.rapid-consecutive-veins.max-seconds-between-veins", 55)),
                Math.max(1.0, raw.getDouble("suspicion.heuristics.rapid-consecutive-veins.min-distance-between-veins", 8.0)),
                Math.max(0.0, raw.getDouble("suspicion.heuristics.rapid-consecutive-veins.weight", 50.0)),
                raw.getBoolean("suspicion.heuristics.ore-ratio-window.enabled", true),
                Math.max(1, raw.getInt("suspicion.heuristics.ore-ratio-window.window-blocks", 80)),
                Math.max(1, raw.getInt("suspicion.heuristics.ore-ratio-window.min-buried-veins-in-window", 3)),
                Math.max(0.0, raw.getDouble("suspicion.heuristics.ore-ratio-window.weight", 40.0)),
                raw.getBoolean("suspicion.heuristics.darkness-mining.enabled", true),
                Math.max(0, Math.min(15, raw.getInt("suspicion.heuristics.darkness-mining.max-block-light-level", 1))),
                raw.getBoolean("suspicion.heuristics.darkness-mining.ignore-with-night-vision", true),
                Math.max(0.0, raw.getDouble("suspicion.heuristics.darkness-mining.bonus-weight", 15.0))
        );
    }
}
