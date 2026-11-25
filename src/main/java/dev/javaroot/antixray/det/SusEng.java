package dev.javaroot.antixray.det;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.WorldCfg;
import dev.javaroot.antixray.cfg.sec.SusCfg;
import dev.javaroot.antixray.det.heur.CaveChk;
import dev.javaroot.antixray.det.heur.ReactChk;
import dev.javaroot.antixray.det.heur.VeinTrack;
import dev.javaroot.antixray.sess.Sess;
import dev.javaroot.antixray.sess.track.Step;
import dev.javaroot.antixray.trap.TrapSpawn;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

public final class SusEng {

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final Supplier<Cfg> cfgSup;
    private final TrapSpawn trapSpawn;

    public SusEng(Supplier<Cfg> cfgSup, TrapSpawn trapSpawn) {
        this.cfgSup = cfgSup;
        this.trapSpawn = trapSpawn;
    }

    public void onBreak(Player p, Sess sess, Block b, WorldCfg wCfg) {
        Cfg cfg = cfgSup.get();
        SusCfg sc = cfg.sus();
        int x = b.getX();
        int y = b.getY();
        int z = b.getZ();
        if (!wCfg.isInRange(y)) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        sess.markWorld(b.getWorld().getName());
        sess.broken().record(x, y, z, nowMs, sc.brokenMemSec());

        // Horizontal facing from yaw prevents (0, 0) collapse when looking straight up/down
        double yawRad = Math.toRadians(p.getLocation().getYaw());
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);

        Material mat = b.getType();
        if (!wCfg.isTrackedOre(mat)) {
            sess.mine().pushStep(new Step(wCfg.name(), x, y, z, dirX, dirZ, false, nowMs), sc.ringBufferSize());
            if (wCfg.isReplaceable(mat) && sess.mine().recStraight(dirX, dirZ, sc.straightStoneBlocks())) {
                sess.sus().decay(sc.straightStoneDecay(), nowMs);
            }
            return;
        }

        sess.mine().pushStep(new Step(wCfg.name(), x, y, z, dirX, dirZ, true, nowMs), sc.ringBufferSize());
        sess.mine().resetStraight();

        if (!VeinTrack.isNewVein(sess, mat, x, y, z, nowMs, sc)) {
            return;
        }

        boolean exposed = CaveChk.isExposed(b, sess, nowMs, sc);
        double mult = exposed ? sc.exposedOreMult() : 1.0;
        if (mult <= 0.0) {
            return;
        }

        double gain = evalHeuristics(p, sess, b, y, dirX, dirZ, nowMs, exposed, cfg, sc);
        if (gain > 0.0) {
            sess.sus().add(gain * mult, sc.maxScore(), sc.triggerThreshold(), sc.watchDurationSec(), nowMs);
        }
        if (sess.sus().isWatch(nowMs, sc.triggerThreshold())) {
            trapSpawn.tryAuto(p, sess, wCfg);
        }
    }

    private static double evalHeuristics(
            Player p, Sess sess, Block b, int y, double dirX, double dirZ,
            long nowMs, boolean exposed, Cfg cfg, SusCfg sc
    ) {
        double gain = 0.0;
        if (sc.sharpTurnEnabled() && hasSharpTurn(sess.mine().stepsSnap(), y, dirX, dirZ, sc)) {
            gain += sc.sharpTurnWeight();
        }
        if (exposed) {
            return gain;
        }
        if (sc.rapidVeinsEnabled() && sess.mine().checkRapidVein(
                b.getX(), y, b.getZ(), sc.maxSecBetweenVeins() * 1000L, sc.minDistBetweenVeins(), nowMs
        )) {
            gain += sc.rapidVeinsWeight();
        }
        if (sc.oreRatioEnabled() && sess.mine().recBuriedWindow(sc.oreRatioWindow()) >= sc.minBuriedVeins()) {
            gain += sc.oreRatioWeight();
        }
        if (sc.darknessEnabled() && isDark(p, b, sc)) {
            gain += sc.darknessBonus();
        }
        if (cfg.react().enabled()) {
            gain += ReactChk.eval(p, sess, b, nowMs, cfg);
        }
        return gain;
    }

    private static boolean hasSharpTurn(Deque<Step> steps, int oreY, double curX, double curZ, SusCfg sc) {
        if (steps.size() < sc.lookbackMin() + 1) {
            return false;
        }
        List<Step> list = new ArrayList<>(steps);
        int checked = 0;
        for (int i = list.size() - 2; i >= 0 && checked < sc.lookbackMax(); i--) {
            Step step = list.get(i);
            if (step.ore()) {
                continue;
            }
            checked++;
            if (checked < sc.lookbackMin()) {
                continue;
            }
            if (Math.abs(oreY - step.y()) >= sc.minPitchDeltaY()) {
                return true;
            }
            double dot = Math.max(-1.0, Math.min(1.0, step.dirX() * curX + step.dirZ() * curZ));
            if (Math.toDegrees(Math.acos(dot)) >= sc.minYawChangeDeg()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDark(Player p, Block ore, SusCfg sc) {
        if (sc.ignoreNightVision() && p.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            return false;
        }
        int maxLight = p.getEyeLocation().getBlock().getLightLevel();
        for (BlockFace f : FACES) {
            int light = ore.getRelative(f).getLightLevel();
            if (light > maxLight) {
                maxLight = light;
            }
        }
        return maxLight <= sc.maxLightLevel();
    }
}
