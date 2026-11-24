package dev.javaroot.antixray.trap;

import dev.javaroot.antixray.cfg.Cfg;
import org.bukkit.Location;

import java.util.concurrent.ThreadLocalRandom;

public final class TrapGeo {

    public static final int[][] NEIGHBOR_OFFSETS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private TrapGeo() {
    }

    public record Candidate(int x, int y, int z) {
    }

    public static Candidate nextCandidate(Location playerLoc, boolean leftSide, Cfg cfg) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Yaw-based horizontal forward angle avoids atan2(0, 0) collapse when looking straight up/down
        double baseYawRad = Math.toRadians(playerLoc.getYaw()) + Math.PI / 2.0;

        double offsetDeg = cfg.yawOffsetMinDegrees;
        if (cfg.yawOffsetMaxDegrees > cfg.yawOffsetMinDegrees) {
            offsetDeg = rng.nextDouble(cfg.yawOffsetMinDegrees, cfg.yawOffsetMaxDegrees);
        }
        double signedRad = Math.toRadians(leftSide ? -offsetDeg : offsetDeg);
        double targetAngle = baseYawRad + signedRad;

        double dist = cfg.trapMinDistance;
        if (cfg.trapMaxDistance > cfg.trapMinDistance) {
            dist = rng.nextDouble(cfg.trapMinDistance, cfg.trapMaxDistance + 0.99);
        }

        int yOffset = cfg.yOffsetMin;
        if (cfg.yOffsetMax > cfg.yOffsetMin) {
            yOffset = rng.nextInt(cfg.yOffsetMin, cfg.yOffsetMax + 1);
        }

        int x = (int) Math.floor(playerLoc.getX() + Math.cos(targetAngle) * dist);
        int y = playerLoc.getBlockY() + 1 + yOffset;
        int z = (int) Math.floor(playerLoc.getZ() + Math.sin(targetAngle) * dist);
        return new Candidate(x, y, z);
    }
}
