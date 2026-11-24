package dev.javaroot.antixray.det;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.mdl.Session;
import org.bukkit.Material;

public final class VeinTrack {

    private VeinTrack() {
    }

    public static boolean isNewVein(Session session, Material ore, int x, int y, int z, long nowMs, Cfg cfg) {
        boolean same = session.isSameVein(
                ore,
                x,
                y,
                z,
                cfg.veinGroupingRadius,
                cfg.veinMergeWindowSeconds * 1000L,
                nowMs
        );
        session.updateLastOre(ore, x, y, z, nowMs);
        return !same;
    }
}
