package dev.javaroot.antixray.det;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.WorldCfg;
import dev.javaroot.antixray.mdl.Session;
import dev.javaroot.antixray.mdl.Step;
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

public final class SusEngine {

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final Supplier<Cfg> cfgSupplier;
    private final TrapSpawn trapSpawn;

    public SusEngine(Supplier<Cfg> cfgSupplier, TrapSpawn trapSpawn) {
        this.cfgSupplier = cfgSupplier;
        this.trapSpawn = trapSpawn;
    }

    public void onBlockBreak(Player player, Session session, Block block, WorldCfg worldCfg) {
        Cfg cfg = cfgSupplier.get();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        if (!worldCfg.isInRange(y)) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        session.markWorld(block.getWorld().getName());
        session.recordBrokenBlock(x, y, z, nowMs, cfg.playerBrokenMemorySeconds);

        // Compute horizontal facing from yaw so looking straight up/down does not collapse to (0, 1)
        double yawRad = Math.toRadians(player.getLocation().getYaw());
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);

        Material type = block.getType();
        if (!worldCfg.isTrackedOre(type)) {
            session.pushStep(new Step(worldCfg.name(), x, y, z, dirX, dirZ, false, nowMs), cfg.ringBufferSize);
            if (worldCfg.isReplaceable(type)
                    && session.recordStraightStone(dirX, dirZ, cfg.straightStoneBlocksForBonusDecay)) {
                session.decaySuspicion(cfg.straightStoneDecayAmount, nowMs);
            }
            return;
        }

        session.pushStep(new Step(worldCfg.name(), x, y, z, dirX, dirZ, true, nowMs), cfg.ringBufferSize);
        session.resetStraightStone();

        if (!VeinTrack.isNewVein(session, type, x, y, z, nowMs, cfg)) {
            return;
        }

        boolean exposed = CaveCheck.isExposedInCave(block, session, nowMs, cfg);
        double multiplier = exposed ? cfg.exposedOreMultiplier : 1.0;
        if (multiplier <= 0.0) {
            return;
        }

        double gain = 0.0;

        if (cfg.sharpTurnEnabled && hasSharpTurnOrStaircase(session.stepsSnapshot(), y, dirX, dirZ, cfg)) {
            gain += cfg.sharpTurnWeight * multiplier;
        }

        if (!exposed && cfg.rapidVeinsEnabled && session.checkRapidConsecutiveVein(
                x,
                y,
                z,
                cfg.maxSecondsBetweenVeins * 1000L,
                cfg.minDistanceBetweenVeins,
                nowMs
        )) {
            gain += cfg.rapidVeinsWeight * multiplier;
        }

        if (!exposed && cfg.oreRatioEnabled) {
            int veinsInWindow = session.recordBuriedVeinInWindow(cfg.oreRatioWindowBlocks);
            if (veinsInWindow >= cfg.minBuriedVeinsInWindow) {
                gain += cfg.oreRatioWeight * multiplier;
            }
        }

        if (!exposed && cfg.darknessEnabled && isDarknessMining(player, block, cfg)) {
            gain += cfg.darknessBonusWeight * multiplier;
        }

        if (gain > 0.0) {
            session.addSuspicion(gain, cfg.maxScore, cfg.triggerThreshold, cfg.watchDurationSeconds, nowMs);
        }

        if (session.isWatchMode(nowMs, cfg.triggerThreshold)) {
            trapSpawn.tryAutoSpawn(player, session, worldCfg);
        }
    }

    private static boolean hasSharpTurnOrStaircase(
            Deque<Step> steps,
            int oreY,
            double curDirX,
            double curDirZ,
            Cfg cfg
    ) {
        if (steps.size() < cfg.lookbackMinSteps + 1) {
            return false;
        }
        List<Step> list = new ArrayList<>(steps);
        // Exclude the ore step itself (last element)
        int endIdx = list.size() - 2;
        int checked = 0;
        for (int i = endIdx; i >= 0 && checked < cfg.lookbackMaxSteps; i--) {
            Step step = list.get(i);
            if (step.ore()) {
                continue;
            }
            checked++;
            if (checked < cfg.lookbackMinSteps) {
                continue;
            }
            if (Math.abs(oreY - step.y()) >= cfg.minPitchVerticalDelta) {
                return true;
            }
            double dot = Math.max(-1.0, Math.min(1.0, step.dirX() * curDirX + step.dirZ() * curDirZ));
            double angleDeg = Math.toDegrees(Math.acos(dot));
            if (angleDeg >= cfg.minYawChangeDegrees) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDarknessMining(Player player, Block oreBlock, Cfg cfg) {
        if (cfg.ignoreWithNightVision && player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            return false;
        }
        int maxLight = player.getEyeLocation().getBlock().getLightLevel();
        for (BlockFace face : FACES) {
            int faceLight = oreBlock.getRelative(face).getLightLevel();
            if (faceLight > maxLight) {
                maxLight = faceLight;
            }
        }
        return maxLight <= cfg.maxBlockLightLevel;
    }
}
