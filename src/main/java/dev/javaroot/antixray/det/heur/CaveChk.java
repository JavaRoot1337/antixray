package dev.javaroot.antixray.det.heur;

import dev.javaroot.antixray.cfg.sec.SusCfg;
import dev.javaroot.antixray.plat.world.RegGuard;
import dev.javaroot.antixray.sess.Sess;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class CaveChk {

    private CaveChk() {
    }

    public static boolean isExposed(Block ore, Sess sess, long nowMs, SusCfg cfg) {
        if (!cfg.caveFilter()) {
            return false;
        }
        int r = cfg.caveCheckRadius();
        int ox = ore.getX();
        int oy = ore.getY();
        int oz = ore.getZ();
        World w = ore.getWorld();
        int minY = w.getMinHeight();
        int maxY = w.getMaxHeight();

        int open = 0;
        for (int dx = -r; dx <= r; dx++) {
            int nx = ox + dx;
            for (int dz = -r; dz <= r; dz++) {
                int nz = oz + dz;
                if (!RegGuard.isAccess(w, nx, nz)) {
                    continue;
                }
                for (int dy = -r; dy <= r; dy++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    int ny = oy + dy;
                    if (ny < minY || ny >= maxY) {
                        continue;
                    }
                    Block nb = w.getBlockAt(nx, ny, nz);
                    Material mat = nb.getType();
                    if (!mat.isAir() && !nb.isLiquid() && mat.isSolid()) {
                        continue;
                    }
                    if (sess.broken().isRecent(nx, ny, nz, nowMs)) {
                        continue;
                    }
                    open++;
                    if (open >= cfg.caveMinAir()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
