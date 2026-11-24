package dev.javaroot.antixray.mdl;

import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Session {

    private final UUID uuid;
    private volatile String username;

    private double suspicionScore;
    private long watchModeUntilMs;

    private final Deque<Step> steps = new ArrayDeque<>();
    private int straightStoneCount;
    private double straightDirX;
    private double straightDirZ;
    private boolean hasStraightDir;

    private long analyzedBlockIndex;
    private final Deque<Long> buriedVeinIndices = new ArrayDeque<>();

    private Material lastTrackedOre;
    private int lastOreX;
    private int lastOreY;
    private int lastOreZ;
    private long lastOreAtMs;

    private boolean hasLastBuriedVein;
    private double lastBuriedX;
    private double lastBuriedY;
    private double lastBuriedZ;
    private long lastBuriedVeinAtMs;

    private int phantomHitCount;
    private long lastPhantomHitAtMs;
    private long nextTrapAllowedAtMs;
    private boolean nextLeftSide = true;
    private volatile int totalPunishments;

    private volatile String currentWorld;
    private final Map<Long, Long> playerBrokenBlocks = new ConcurrentHashMap<>();

    public Session(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
    }

    public UUID uuid() {
        return uuid;
    }

    public String username() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public synchronized double suspicionScore() {
        return suspicionScore;
    }

    public synchronized double addSuspicion(double delta, double maxScore, double triggerThreshold, int watchDurationSeconds, long nowMs) {
        if (delta <= 0.0) {
            return suspicionScore;
        }
        suspicionScore = Math.min(maxScore, suspicionScore + delta);
        if (suspicionScore >= triggerThreshold) {
            watchModeUntilMs = Math.max(watchModeUntilMs, nowMs + watchDurationSeconds * 1000L);
        }
        return suspicionScore;
    }

    public synchronized void decaySuspicion(double amount, long nowMs) {
        if (amount <= 0.0 || suspicionScore <= 0.0) {
            return;
        }
        suspicionScore = Math.max(0.0, suspicionScore - amount);
        if (suspicionScore == 0.0 && nowMs >= watchModeUntilMs) {
            watchModeUntilMs = 0L;
        }
    }

    public synchronized boolean isWatchMode(long nowMs, double triggerThreshold) {
        return suspicionScore >= triggerThreshold || nowMs < watchModeUntilMs;
    }

    public synchronized long watchRemainingSeconds(long nowMs) {
        if (watchModeUntilMs <= nowMs) {
            return 0L;
        }
        return (watchModeUntilMs - nowMs + 999L) / 1000L;
    }

    public synchronized void pushStep(Step step, int maxBufferSize) {
        steps.addLast(step);
        while (steps.size() > maxBufferSize) {
            steps.removeFirst();
        }
        analyzedBlockIndex++;
    }

    public synchronized Deque<Step> stepsSnapshot() {
        return new ArrayDeque<>(steps);
    }

    public synchronized boolean recordStraightStone(double dirX, double dirZ, int requiredBlocks) {
        if (!hasStraightDir) {
            straightDirX = dirX;
            straightDirZ = dirZ;
            hasStraightDir = true;
            straightStoneCount = 1;
            return false;
        }
        double dot = straightDirX * dirX + straightDirZ * dirZ;
        if (dot >= 0.8) {
            straightStoneCount++;
            if (straightStoneCount >= requiredBlocks) {
                straightStoneCount = 0;
                return true;
            }
        } else {
            straightDirX = dirX;
            straightDirZ = dirZ;
            straightStoneCount = 1;
        }
        return false;
    }

    public synchronized void resetStraightStone() {
        straightStoneCount = 0;
        hasStraightDir = false;
    }

    public synchronized int recordBuriedVeinInWindow(int windowBlocks) {
        buriedVeinIndices.addLast(analyzedBlockIndex);
        long cutoff = analyzedBlockIndex - windowBlocks;
        while (!buriedVeinIndices.isEmpty() && buriedVeinIndices.peekFirst() < cutoff) {
            buriedVeinIndices.removeFirst();
        }
        return buriedVeinIndices.size();
    }

    public synchronized boolean isSameVein(Material ore, int x, int y, int z, int radius, long mergeWindowMs, long nowMs) {
        if (lastTrackedOre != ore || nowMs - lastOreAtMs > mergeWindowMs) {
            return false;
        }
        int dx = Math.abs(x - lastOreX);
        int dy = Math.abs(y - lastOreY);
        int dz = Math.abs(z - lastOreZ);
        return dx <= radius && dy <= radius && dz <= radius;
    }

    public synchronized void updateLastOre(Material ore, int x, int y, int z, long nowMs) {
        this.lastTrackedOre = ore;
        this.lastOreX = x;
        this.lastOreY = y;
        this.lastOreZ = z;
        this.lastOreAtMs = nowMs;
    }

    public synchronized boolean checkRapidConsecutiveVein(
            double x,
            double y,
            double z,
            long maxGapMs,
            double minDistance,
            long nowMs
    ) {
        boolean rapid = false;
        if (hasLastBuriedVein && (nowMs - lastBuriedVeinAtMs) <= maxGapMs) {
            double dx = x - lastBuriedX;
            double dy = y - lastBuriedY;
            double dz = z - lastBuriedZ;
            if ((dx * dx + dy * dy + dz * dz) >= (minDistance * minDistance)) {
                rapid = true;
            }
        }
        hasLastBuriedVein = true;
        lastBuriedX = x;
        lastBuriedY = y;
        lastBuriedZ = z;
        lastBuriedVeinAtMs = nowMs;
        return rapid;
    }

    public synchronized int incrementPhantomHit(long nowMs, int resetAfterMinutes) {
        if (resetAfterMinutes > 0 && lastPhantomHitAtMs > 0L) {
            long resetMs = resetAfterMinutes * 60_000L;
            if (nowMs - lastPhantomHitAtMs >= resetMs) {
                phantomHitCount = 0;
            }
        }
        phantomHitCount++;
        lastPhantomHitAtMs = nowMs;
        return phantomHitCount;
    }

    public synchronized int phantomHitCount(long nowMs, int resetAfterMinutes) {
        if (resetAfterMinutes > 0 && phantomHitCount > 0 && lastPhantomHitAtMs > 0L) {
            long resetMs = resetAfterMinutes * 60_000L;
            if (nowMs - lastPhantomHitAtMs >= resetMs) {
                phantomHitCount = 0;
            }
        }
        return phantomHitCount;
    }

    public synchronized void resetPhantomHitCount() {
        phantomHitCount = 0;
        lastPhantomHitAtMs = 0L;
    }

    public synchronized int rawPhantomHitCount() {
        return phantomHitCount;
    }

    public synchronized long lastPhantomHitAtMs() {
        return lastPhantomHitAtMs;
    }

    public synchronized long watchModeUntilMs() {
        return watchModeUntilMs;
    }

    public synchronized void restoreProfile(
            int totalPunishments,
            double suspicionScore,
            long watchModeUntilMs,
            int phantomHitCount,
            long lastPhantomHitAtMs
    ) {
        this.totalPunishments = totalPunishments;
        if (suspicionScore > 0.0) {
            this.suspicionScore = Math.max(this.suspicionScore, suspicionScore);
        }
        if (watchModeUntilMs > 0L) {
            this.watchModeUntilMs = Math.max(this.watchModeUntilMs, watchModeUntilMs);
        }
        if (phantomHitCount > 0) {
            this.phantomHitCount += phantomHitCount;
            this.lastPhantomHitAtMs = Math.max(this.lastPhantomHitAtMs, lastPhantomHitAtMs);
        }
    }

    public synchronized boolean canSpawnTrap(long nowMs) {
        return nowMs >= nextTrapAllowedAtMs;
    }

    public synchronized void setNextTrapCooldown(long nowMs, int cooldownSeconds) {
        nextTrapAllowedAtMs = Math.max(nextTrapAllowedAtMs, nowMs + cooldownSeconds * 1000L);
    }

    public synchronized void forceTrapCooldown(long nowMs, int cooldownSeconds) {
        nextTrapAllowedAtMs = nowMs + cooldownSeconds * 1000L;
    }

    public synchronized boolean takeNextSide(boolean alternateSides) {
        boolean side = nextLeftSide;
        if (alternateSides) {
            nextLeftSide = !nextLeftSide;
        }
        return side;
    }

    public int totalPunishments() {
        return totalPunishments;
    }

    public void setTotalPunishments(int totalPunishments) {
        this.totalPunishments = totalPunishments;
    }

    public synchronized void incrementTotalPunishments() {
        this.totalPunishments++;
    }

    public synchronized void resetAll() {
        suspicionScore = 0.0;
        watchModeUntilMs = 0L;
        steps.clear();
        straightStoneCount = 0;
        hasStraightDir = false;
        analyzedBlockIndex = 0L;
        buriedVeinIndices.clear();
        lastTrackedOre = null;
        lastOreAtMs = 0L;
        hasLastBuriedVein = false;
        lastBuriedVeinAtMs = 0L;
        phantomHitCount = 0;
        lastPhantomHitAtMs = 0L;
        nextTrapAllowedAtMs = 0L;
        playerBrokenBlocks.clear();
    }

    public String currentWorld() {
        return currentWorld;
    }

    public synchronized void markWorld(String worldName) {
        if (worldName == null) {
            return;
        }
        String prev = this.currentWorld;
        if (!worldName.equals(prev)) {
            this.currentWorld = worldName;
            playerBrokenBlocks.clear();
            steps.clear();
            straightStoneCount = 0;
            hasStraightDir = false;
            lastTrackedOre = null;
            hasLastBuriedVein = false;
        }
    }

    public void recordBrokenBlock(int x, int y, int z, long nowMs, int memorySeconds) {
        long key = packBlock(x, y, z);
        playerBrokenBlocks.put(key, nowMs + memorySeconds * 1000L);
        if (playerBrokenBlocks.size() > 512) {
            pruneBrokenBlocks(nowMs);
        }
    }

    public boolean isRecentlyBrokenByPlayer(int x, int y, int z, long nowMs) {
        Long expiresAt = playerBrokenBlocks.get(packBlock(x, y, z));
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < nowMs) {
            playerBrokenBlocks.remove(packBlock(x, y, z), expiresAt);
            return false;
        }
        return true;
    }

    public void pruneBrokenBlocks(long nowMs) {
        Iterator<Map.Entry<Long, Long>> it = playerBrokenBlocks.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < nowMs) {
                it.remove();
            }
        }
    }

    public static long packBlock(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | ((long) y & 0xFFFL);
    }
}
