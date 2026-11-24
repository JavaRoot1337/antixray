package dev.javaroot.antixray.cfg;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class Cfg {

    public enum DetectMode {
        ON_START_DIG,
        ON_FINISH_DIG;

        public static DetectMode parse(String raw) {
            if (raw != null && raw.equalsIgnoreCase("ON_FINISH_DIG")) {
                return ON_FINISH_DIG;
            }
            return ON_START_DIG;
        }
    }

    public record UpdateCfg(
            boolean enabled,
            boolean notifyPlayers,
            List<String> message
    ) {
    }

    public final int configVersion;
    public final boolean debug;
    public final boolean ignoreCreative;
    public final boolean ignoreSpectator;
    public final boolean bypassPermissionEnabled;
    public final String databaseFile;
    public final int historyRetentionDays;
    public final UpdateCfg updateChecker;

    public final double triggerThreshold;
    public final double maxScore;
    public final int watchDurationSeconds;
    public final int ringBufferSize;

    public final int decayIntervalSeconds;
    public final double decayAmountPerInterval;
    public final int straightStoneBlocksForBonusDecay;
    public final double straightStoneDecayAmount;

    public final int veinGroupingRadius;
    public final int veinMergeWindowSeconds;

    public final boolean caveFilterEnabled;
    public final int caveCheckRadius;
    public final int caveMinForeignAirBlocks;
    public final int playerBrokenMemorySeconds;
    public final double exposedOreMultiplier;

    public final boolean sharpTurnEnabled;
    public final double minYawChangeDegrees;
    public final int minPitchVerticalDelta;
    public final int lookbackMinSteps;
    public final int lookbackMaxSteps;
    public final double sharpTurnWeight;

    public final boolean rapidVeinsEnabled;
    public final int maxSecondsBetweenVeins;
    public final double minDistanceBetweenVeins;
    public final double rapidVeinsWeight;

    public final boolean oreRatioEnabled;
    public final int oreRatioWindowBlocks;
    public final int minBuriedVeinsInWindow;
    public final double oreRatioWeight;

    public final boolean darknessEnabled;
    public final int maxBlockLightLevel;
    public final boolean ignoreWithNightVision;
    public final double darknessBonusWeight;

    public final DetectMode detectMode;
    public final int maxActiveVeinsPerPlayer;
    public final int veinSizeMin;
    public final int veinSizeMax;
    public final int trapMinDistance;
    public final int trapMaxDistance;
    public final double yawOffsetMinDegrees;
    public final double yawOffsetMaxDegrees;
    public final int yOffsetMin;
    public final int yOffsetMax;
    public final boolean alternateSides;
    public final int maxSpawnAttempts;
    public final int spawnCooldownSeconds;
    public final int fastRespawnAfterHitSeconds;
    public final int ttlSeconds;
    public final double despawnDistance;
    public final boolean requireAllSixSolidNeighbors;
    public final boolean cancelDigPacket;
    public final boolean restoreOnHit;

    public final int punishmentTriggerCount;
    public final int countResetAfterMinutes;
    public final boolean resetCountOnPunish;
    public final boolean alertOnFirstHit;
    public final boolean alertOnSecondHit;
    public final boolean alertOnPunish;
    public final boolean logToConsole;
    public final List<String> punishmentCommands;

    private final Map<String, WorldCfg> worlds;

    private Cfg(FileConfiguration raw, Logger logger) {
        this.configVersion = raw.getInt("config-version", 1);

        this.debug = raw.getBoolean("general.debug", false);
        this.ignoreCreative = raw.getBoolean("general.ignore-creative", true);
        this.ignoreSpectator = raw.getBoolean("general.ignore-spectator", true);
        this.bypassPermissionEnabled = raw.getBoolean("general.bypass-permission-enabled", true);
        this.databaseFile = raw.getString("general.database-file", "data.db");
        this.historyRetentionDays = Math.max(0, raw.getInt("general.history-retention-days", 30));
        this.updateChecker = parseUpdateCfg(raw);

        this.triggerThreshold = Math.max(1.0, raw.getDouble("suspicion.trigger-threshold", 100.0));
        this.maxScore = Math.max(triggerThreshold, raw.getDouble("suspicion.max-score", 250.0));
        this.watchDurationSeconds = Math.max(1, raw.getInt("suspicion.watch-duration-seconds", 300));
        this.ringBufferSize = Math.max(4, raw.getInt("suspicion.ring-buffer-size", 20));

        this.decayIntervalSeconds = Math.max(1, raw.getInt("suspicion.decay.interval-seconds", 15));
        this.decayAmountPerInterval = Math.max(0.0, raw.getDouble("suspicion.decay.amount-per-interval", 6.0));
        this.straightStoneBlocksForBonusDecay = Math.max(1, raw.getInt("suspicion.decay.straight-stone-blocks-for-bonus-decay", 15));
        this.straightStoneDecayAmount = Math.max(0.0, raw.getDouble("suspicion.decay.straight-stone-decay-amount", 8.0));

        this.veinGroupingRadius = Math.max(1, raw.getInt("suspicion.vein-grouping.radius", 3));
        this.veinMergeWindowSeconds = Math.max(1, raw.getInt("suspicion.vein-grouping.merge-window-seconds", 8));

        this.caveFilterEnabled = raw.getBoolean("suspicion.cave-filter.enabled", true);
        this.caveCheckRadius = Math.max(1, raw.getInt("suspicion.cave-filter.check-radius", 2));
        this.caveMinForeignAirBlocks = Math.max(1, raw.getInt("suspicion.cave-filter.min-foreign-air-blocks", 3));
        this.playerBrokenMemorySeconds = Math.max(1, raw.getInt("suspicion.cave-filter.player-broken-memory-seconds", 40));
        this.exposedOreMultiplier = Math.max(0.0, raw.getDouble("suspicion.cave-filter.exposed-ore-multiplier", 0.1));

        this.sharpTurnEnabled = raw.getBoolean("suspicion.heuristics.sharp-turn-to-ore.enabled", true);
        this.minYawChangeDegrees = raw.getDouble("suspicion.heuristics.sharp-turn-to-ore.min-yaw-change-degrees", 35.0);
        this.minPitchVerticalDelta = Math.max(1, raw.getInt("suspicion.heuristics.sharp-turn-to-ore.min-pitch-vertical-delta", 2));
        this.lookbackMinSteps = Math.max(1, raw.getInt("suspicion.heuristics.sharp-turn-to-ore.lookback-min-steps", 3));
        this.lookbackMaxSteps = Math.max(lookbackMinSteps, raw.getInt("suspicion.heuristics.sharp-turn-to-ore.lookback-max-steps", 12));
        this.sharpTurnWeight = Math.max(0.0, raw.getDouble("suspicion.heuristics.sharp-turn-to-ore.weight", 55.0));

        this.rapidVeinsEnabled = raw.getBoolean("suspicion.heuristics.rapid-consecutive-veins.enabled", true);
        this.maxSecondsBetweenVeins = Math.max(1, raw.getInt("suspicion.heuristics.rapid-consecutive-veins.max-seconds-between-veins", 55));
        this.minDistanceBetweenVeins = Math.max(1.0, raw.getDouble("suspicion.heuristics.rapid-consecutive-veins.min-distance-between-veins", 8.0));
        this.rapidVeinsWeight = Math.max(0.0, raw.getDouble("suspicion.heuristics.rapid-consecutive-veins.weight", 50.0));

        this.oreRatioEnabled = raw.getBoolean("suspicion.heuristics.ore-ratio-window.enabled", true);
        this.oreRatioWindowBlocks = Math.max(1, raw.getInt("suspicion.heuristics.ore-ratio-window.window-blocks", 80));
        this.minBuriedVeinsInWindow = Math.max(1, raw.getInt("suspicion.heuristics.ore-ratio-window.min-buried-veins-in-window", 3));
        this.oreRatioWeight = Math.max(0.0, raw.getDouble("suspicion.heuristics.ore-ratio-window.weight", 40.0));

        this.darknessEnabled = raw.getBoolean("suspicion.heuristics.darkness-mining.enabled", true);
        this.maxBlockLightLevel = Math.max(0, Math.min(15, raw.getInt("suspicion.heuristics.darkness-mining.max-block-light-level", 1)));
        this.ignoreWithNightVision = raw.getBoolean("suspicion.heuristics.darkness-mining.ignore-with-night-vision", true);
        this.darknessBonusWeight = Math.max(0.0, raw.getDouble("suspicion.heuristics.darkness-mining.bonus-weight", 15.0));

        this.detectMode = DetectMode.parse(raw.getString("trap.detect-mode", "ON_START_DIG"));
        this.maxActiveVeinsPerPlayer = Math.max(1, raw.getInt("trap.max-active-veins-per-player", 1));
        this.veinSizeMin = Math.max(1, raw.getInt("trap.vein-size-min", 3));
        this.veinSizeMax = Math.max(veinSizeMin, raw.getInt("trap.vein-size-max", 6));
        this.trapMinDistance = Math.max(1, raw.getInt("trap.min-distance", 6));
        this.trapMaxDistance = Math.max(trapMinDistance, raw.getInt("trap.max-distance", 10));
        this.yawOffsetMinDegrees = raw.getDouble("trap.yaw-offset-min-degrees", 35.0);
        this.yawOffsetMaxDegrees = Math.max(yawOffsetMinDegrees, raw.getDouble("trap.yaw-offset-max-degrees", 85.0));
        this.yOffsetMin = raw.getInt("trap.y-offset-min", -3);
        this.yOffsetMax = Math.max(yOffsetMin, raw.getInt("trap.y-offset-max", 3));
        this.alternateSides = raw.getBoolean("trap.alternate-sides", true);
        this.maxSpawnAttempts = Math.max(1, raw.getInt("trap.max-spawn-attempts", 25));
        this.spawnCooldownSeconds = Math.max(0, raw.getInt("trap.spawn-cooldown-seconds", 25));
        this.fastRespawnAfterHitSeconds = Math.max(0, raw.getInt("trap.fast-respawn-after-hit-seconds", 10));
        this.ttlSeconds = Math.max(1, raw.getInt("trap.ttl-seconds", 90));
        this.despawnDistance = Math.max(1.0, raw.getDouble("trap.despawn-distance", 24.0));
        this.requireAllSixSolidNeighbors = raw.getBoolean("trap.require-all-six-solid-neighbors", true);
        this.cancelDigPacket = raw.getBoolean("trap.cancel-dig-packet", true);
        this.restoreOnHit = raw.getBoolean("trap.restore-on-hit", true);

        this.punishmentTriggerCount = Math.max(1, raw.getInt("punishment.trigger-count", 3));
        this.countResetAfterMinutes = Math.max(0, raw.getInt("punishment.count-reset-after-minutes", 60));
        this.resetCountOnPunish = raw.getBoolean("punishment.reset-count-on-punish", true);
        this.alertOnFirstHit = raw.getBoolean("punishment.alert-on-first-hit", true);
        this.alertOnSecondHit = raw.getBoolean("punishment.alert-on-second-hit", true);
        this.alertOnPunish = raw.getBoolean("punishment.alert-on-punish", true);
        this.logToConsole = raw.getBoolean("punishment.log-to-console", true);
        this.punishmentCommands = List.copyOf(raw.getStringList("punishment.commands"));

        this.worlds = parseWorlds(raw.getConfigurationSection("worlds"), logger);
    }

    public static Cfg load(Plugin plugin) {
        CfgMig.ensureUpdated(plugin, "config.yml", java.util.Set.of("worlds"));
        plugin.reloadConfig();
        return load(plugin.getConfig(), plugin.getLogger());
    }

    public static Cfg load(FileConfiguration raw, Logger logger) {
        return new Cfg(raw, logger);
    }

    private static UpdateCfg parseUpdateCfg(FileConfiguration raw) {
        boolean enabled = raw.getBoolean("update-checker.enabled", true);
        boolean notifyPlayers = raw.getBoolean("update-checker.notify-players", true);
        List<String> lines;
        if (raw.isList("update-checker.message")) {
            lines = List.copyOf(raw.getStringList("update-checker.message"));
        } else if (raw.isString("update-checker.message")) {
            String single = raw.getString("update-checker.message", "");
            lines = single.isEmpty() ? List.of() : List.of(single);
        } else {
            lines = List.of();
        }
        return new UpdateCfg(enabled, notifyPlayers, lines);
    }

    public WorldCfg world(String worldName) {
        if (worldName == null) {
            return null;
        }
        return worlds.get(worldName);
    }

    private static Map<String, WorldCfg> parseWorlds(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, WorldCfg> result = new HashMap<>();
        for (String worldName : section.getKeys(false)) {
            ConfigurationSection ws = section.getConfigurationSection(worldName);
            if (ws == null) {
                continue;
            }
            boolean enabled = ws.getBoolean("enabled", true);
            int maxY = ws.getInt("max-y", 16);
            int minY = ws.getInt("min-y", -60);
            Set<Material> replaceable = parseMaterialSet(ws.getStringList("replaceable-blocks"), worldName, logger);
            Set<Material> tracked = parseMaterialSet(ws.getStringList("tracked-ores"), worldName, logger);
            int threshold = ws.getInt("phantom-ores.deepslate-y-threshold", 0);
            List<Material> above = parseMaterialList(ws.getStringList("phantom-ores.above-threshold"), worldName, logger);
            List<Material> below = parseMaterialList(ws.getStringList("phantom-ores.below-threshold"), worldName, logger);

            result.put(worldName, new WorldCfg(
                    worldName,
                    enabled,
                    maxY,
                    minY,
                    replaceable,
                    tracked,
                    threshold,
                    above,
                    below
            ));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Set<Material> parseMaterialSet(List<String> names, String worldName, Logger logger) {
        EnumSet<Material> set = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material mat = matchMaterial(name, worldName, logger);
            if (mat != null) {
                set.add(mat);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    private static List<Material> parseMaterialList(List<String> names, String worldName, Logger logger) {
        List<Material> list = new ArrayList<>(names.size());
        for (String name : names) {
            Material mat = matchMaterial(name, worldName, logger);
            if (mat != null) {
                list.add(mat);
            }
        }
        return Collections.unmodifiableList(list);
    }

    private static Material matchMaterial(String raw, String worldName, Logger logger) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Material mat = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (mat == null && logger != null) {
            logger.warning("Unknown material '" + raw + "' in world '" + worldName + "'");
        }
        return mat;
    }
}
