package dev.javaroot.antixray.sess.track;

import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.Deque;

public final class MineTrack {

    private final Deque<Step> steps = new ArrayDeque<>();
    private int straightCnt;
    private double straightX;
    private double straightZ;
    private boolean hasStraight;

    private long blockIdx;
    private final Deque<Long> buriedIndices = new ArrayDeque<>();

    private Material lastOre;
    private int lastOreX;
    private int lastOreY;
    private int lastOreZ;
    private long lastOreMs;

    private boolean hasLastBuried;
    private double lastBuriedX;
    private double lastBuriedY;
    private double lastBuriedZ;
    private long lastBuriedMs;

    public synchronized void pushStep(Step step, int maxBuf) {
        steps.addLast(step);
        while (steps.size() > maxBuf) {
            steps.removeFirst();
        }
        blockIdx++;
    }

    public synchronized Deque<Step> stepsSnap() {
        return new ArrayDeque<>(steps);
    }

    public synchronized boolean recStraight(double dx, double dz, int req) {
        if (!hasStraight) {
            straightX = dx;
            straightZ = dz;
            hasStraight = true;
            straightCnt = 1;
            return false;
        }
        if (straightX * dx + straightZ * dz >= 0.8) {
            straightCnt++;
            if (straightCnt >= req) {
                straightCnt = 0;
                return true;
            }
        } else {
            straightX = dx;
            straightZ = dz;
            straightCnt = 1;
        }
        return false;
    }

    public synchronized void resetStraight() {
        straightCnt = 0;
        hasStraight = false;
    }

    public synchronized int recBuriedWindow(int winBlocks) {
        buriedIndices.addLast(blockIdx);
        long cutoff = blockIdx - winBlocks;
        while (!buriedIndices.isEmpty() && buriedIndices.peekFirst() < cutoff) {
            buriedIndices.removeFirst();
        }
        return buriedIndices.size();
    }

    public synchronized boolean isSameVein(Material ore, int x, int y, int z, int radius, long mergeMs, long nowMs) {
        if (lastOre != ore || nowMs - lastOreMs > mergeMs) {
            return false;
        }
        return Math.abs(x - lastOreX) <= radius
                && Math.abs(y - lastOreY) <= radius
                && Math.abs(z - lastOreZ) <= radius;
    }

    public synchronized void updateLastOre(Material ore, int x, int y, int z, long nowMs) {
        lastOre = ore;
        lastOreX = x;
        lastOreY = y;
        lastOreZ = z;
        lastOreMs = nowMs;
    }

    public synchronized boolean checkRapidVein(double x, double y, double z, long maxGapMs, double minDist, long nowMs) {
        boolean rapid = false;
        if (hasLastBuried && nowMs - lastBuriedMs <= maxGapMs) {
            double dx = x - lastBuriedX;
            double dy = y - lastBuriedY;
            double dz = z - lastBuriedZ;
            if (dx * dx + dy * dy + dz * dz >= minDist * minDist) {
                rapid = true;
            }
        }
        hasLastBuried = true;
        lastBuriedX = x;
        lastBuriedY = y;
        lastBuriedZ = z;
        lastBuriedMs = nowMs;
        return rapid;
    }

    public synchronized void resetWorld() {
        steps.clear();
        straightCnt = 0;
        hasStraight = false;
        lastOre = null;
        hasLastBuried = false;
    }

    public synchronized void resetAll() {
        resetWorld();
        blockIdx = 0L;
        buriedIndices.clear();
        lastOreMs = 0L;
        lastBuriedMs = 0L;
    }
}
