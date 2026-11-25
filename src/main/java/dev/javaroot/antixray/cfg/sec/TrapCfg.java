package dev.javaroot.antixray.cfg.sec;

import org.bukkit.configuration.file.FileConfiguration;

public record TrapCfg(
        DetectMode detectMode,
        int maxActiveVeins,
        int veinMin,
        int veinMax,
        int minDist,
        int maxDist,
        double yawMinDeg,
        double yawMaxDeg,
        int yMin,
        int yMax,
        boolean altSides,
        int maxAttempts,
        int cooldownSec,
        int fastRespawnSec,
        int ttlSec,
        double despawnDist,
        boolean requireSixSolid,
        boolean cancelDig,
        boolean restoreOnHit
) {
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

    public static TrapCfg load(FileConfiguration raw) {
        int vMin = Math.max(1, raw.getInt("trap.vein-size-min", 3));
        int dMin = Math.max(1, raw.getInt("trap.min-distance", 6));
        double yawMin = raw.getDouble("trap.yaw-offset-min-degrees", 35.0);
        int yMin = raw.getInt("trap.y-offset-min", -3);

        return new TrapCfg(
                DetectMode.parse(raw.getString("trap.detect-mode", "ON_START_DIG")),
                Math.max(1, raw.getInt("trap.max-active-veins-per-player", 1)),
                vMin,
                Math.max(vMin, raw.getInt("trap.vein-size-max", 6)),
                dMin,
                Math.max(dMin, raw.getInt("trap.max-distance", 10)),
                yawMin,
                Math.max(yawMin, raw.getDouble("trap.yaw-offset-max-degrees", 85.0)),
                yMin,
                Math.max(yMin, raw.getInt("trap.y-offset-max", 3)),
                raw.getBoolean("trap.alternate-sides", true),
                Math.max(1, raw.getInt("trap.max-spawn-attempts", 25)),
                Math.max(0, raw.getInt("trap.spawn-cooldown-seconds", 25)),
                Math.max(0, raw.getInt("trap.fast-respawn-after-hit-seconds", 10)),
                Math.max(1, raw.getInt("trap.ttl-seconds", 90)),
                Math.max(1.0, raw.getDouble("trap.despawn-distance", 24.0)),
                raw.getBoolean("trap.require-all-six-solid-neighbors", true),
                raw.getBoolean("trap.cancel-dig-packet", true),
                raw.getBoolean("trap.restore-on-hit", true)
        );
    }
}
