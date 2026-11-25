package dev.javaroot.antixray.det.heur;

import dev.javaroot.antixray.cfg.sec.SusCfg;
import dev.javaroot.antixray.sess.Sess;
import dev.javaroot.antixray.sess.track.MineTrack;
import org.bukkit.Material;

public final class VeinTrack {

    private VeinTrack() {
    }

    public static boolean isNewVein(Sess sess, Material ore, int x, int y, int z, long nowMs, SusCfg cfg) {
        MineTrack mine = sess.mine();
        boolean same = mine.isSameVein(ore, x, y, z, cfg.veinRadius(), cfg.veinMergeWindowSec() * 1000L, nowMs);
        mine.updateLastOre(ore, x, y, z, nowMs);
        return !same;
    }
}
