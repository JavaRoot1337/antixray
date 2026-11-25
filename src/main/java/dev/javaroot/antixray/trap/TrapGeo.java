package dev.javaroot.antixray.trap;

import dev.javaroot.antixray.cfg.sec.TrapCfg;
import org.bukkit.Location;

import java.util.concurrent.ThreadLocalRandom;

public final class TrapGeo {

    private TrapGeo() {
    }

    public record Cand(int x, int y, int z) {
    }

    public static Cand nextCand(Location loc, boolean leftSide, TrapCfg tc) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Yaw-based horizontal forward angle avoids atan2(0, 0) collapse when looking straight up/down
        double baseRad = Math.toRadians(loc.getYaw()) + Math.PI / 2.0;

        double offDeg = tc.yawMinDeg();
        if (tc.yawMaxDeg() > tc.yawMinDeg()) {
            offDeg = rng.nextDouble(tc.yawMinDeg(), tc.yawMaxDeg());
        }
        double angle = baseRad + Math.toRadians(leftSide ? -offDeg : offDeg);

        double dist = tc.minDist();
        if (tc.maxDist() > tc.minDist()) {
            dist = rng.nextDouble(tc.minDist(), tc.maxDist() + 0.99);
        }

        int yOff = tc.yMin();
        if (tc.yMax() > tc.yMin()) {
            yOff = rng.nextInt(tc.yMin(), tc.yMax() + 1);
        }

        int x = (int) Math.floor(loc.getX() + Math.cos(angle) * dist);
        int y = loc.getBlockY() + 1 + yOff;
        int z = (int) Math.floor(loc.getZ() + Math.sin(angle) * dist);
        return new Cand(x, y, z);
    }
}
