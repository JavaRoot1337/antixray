package dev.javaroot.antixray.det.heur;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.sec.ReactCfg;
import dev.javaroot.antixray.plat.world.LookVec;
import dev.javaroot.antixray.plat.world.RegGuard;
import dev.javaroot.antixray.sess.Sess;
import dev.javaroot.antixray.sess.track.TurnAnchor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

public final class ReactChk {

    @FunctionalInterface
    public interface SolidChk {
        boolean isSolid(int x, int y, int z);
    }

    private ReactChk() {
    }

    public static double eval(Player p, Sess sess, Block ore, long nowMs, Cfg cfg) {
        if (!cfg.react().enabled()) {
            return 0.0;
        }
        World w = ore.getWorld();
        int minY = w.getMinHeight();
        int maxY = w.getMaxHeight();
        return eval(sess, ore.getX(), ore.getY(), ore.getZ(), nowMs, p.getPing(),
                (x, y, z) -> RegGuard.isSolidBlock(w, minY, maxY, x, y, z), cfg);
    }

    public static double eval(
            Sess sess, int ox, int oy, int oz, long nowMs, int pingMs, SolidChk solidChk, Cfg cfg
    ) {
        ReactCfg rc = cfg.react();
        if (!rc.enabled()) {
            return 0.0;
        }

        long minMs = pingMs > 250 ? Math.max(400L, rc.minReactMs() / 2L) : rc.minReactMs();
        long maxMs = pingMs > 250 ? rc.maxReactMs() + 5000L : rc.maxReactMs();

        List<TurnAnchor> anchors = sess.rot().anchorsSnap(nowMs, maxMs);
        if (anchors.isEmpty()) {
            return 0.0;
        }

        int memSec = cfg.sus().brokenMemSec();
        long adjBreakMs = sess.broken().latestNeighborMs(ox, oy, oz, nowMs, memSec);

        for (int i = anchors.size() - 1; i >= 0; i--) {
            TurnAnchor a = anchors.get(i);
            double dx = (ox + 0.5) - a.x();
            double dy = (oy + 0.5) - a.eyeY();
            double dz = (oz + 0.5) - a.z();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (dist < 3.5 || dist < rc.minDist() || dist > rc.maxDist()) {
                continue;
            }

            long dtMs = Math.round(nowMs - a.stabilizedAtMs() - (pingMs / 2.0));
            if (dtMs < minMs || dtMs > maxMs) {
                continue;
            }

            if (LookVec.aimErrorDeg(a.postYaw(), a.postPitch(), dx, dy, dz, dist) > rc.maxAimErrDeg()) {
                continue;
            }
            if (LookVec.aimErrorDeg(a.preYaw(), a.prePitch(), dx, dy, dz, dist) <= rc.maxAimErrDeg()) {
                continue;
            }

            if (adjBreakMs > 0L && a.startedAtMs() >= adjBreakMs) {
                long sinceAdj = a.startedAtMs() - adjBreakMs;
                if (sinceAdj >= 180L && sinceAdj <= 600L) {
                    return 0.0;
                }
            }

            if (!hasSolidRay(sess, a, ox, oy, oz, dx, dy, dz, dist, solidChk, rc, memSec)) {
                continue;
            }

            double score = rc.weight();
            double dYaw = LookVec.absYawDiff(a.preYaw(), a.postYaw());
            double dPitch = Math.abs(a.postPitch() - a.prePitch());
            boolean ortho = Math.abs(dYaw - 90.0) <= rc.orthoTolDeg()
                    && dPitch < 5.0
                    && Math.abs(oy - Math.floor(a.eyeY())) <= 1;

            if (ortho) {
                score *= rc.orthoMult();
            } else if (sess.rot().recReactHit(nowMs, rc.streakWindowSec()) >= 2) {
                score *= rc.streakMult();
            }
            return score;
        }
        return 0.0;
    }

    private static boolean hasSolidRay(
            Sess sess, TurnAnchor a, int ox, int oy, int oz,
            double dx, double dy, double dz, double dist,
            SolidChk solidChk, ReactCfg rc, int memSec
    ) {
        double stepSize = Math.max(0.5, dist / 18.0);
        int steps = Math.min(18, (int) Math.floor(dist / stepSize));
        int lastBx = Integer.MIN_VALUE;
        int lastBy = Integer.MIN_VALUE;
        int lastBz = Integer.MIN_VALUE;
        int solid = 0;

        for (int s = 1; s < steps; s++) {
            double t = (s * stepSize) / dist;
            int bx = (int) Math.floor(a.x() + dx * t);
            int by = (int) Math.floor(a.eyeY() + dy * t);
            int bz = (int) Math.floor(a.z() + dz * t);
            if (bx == lastBx && by == lastBy && bz == lastBz) {
                continue;
            }
            lastBx = bx;
            lastBy = by;
            lastBz = bz;
            if (bx == ox && by == oy && bz == oz) {
                continue;
            }
            if (solidChk.isSolid(bx, by, bz) && sess.broken().wasIntactAt(bx, by, bz, a.startedAtMs(), memSec)) {
                solid++;
                if (solid >= rc.minSolidOnRay()) {
                    return true;
                }
            }
        }
        return false;
    }
}
