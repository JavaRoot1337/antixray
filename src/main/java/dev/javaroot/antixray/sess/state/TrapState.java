package dev.javaroot.antixray.sess.state;

public final class TrapState {

    private int hits;
    private long lastHitMs;
    private long nextAllowedMs;
    private boolean nextLeft = true;
    private volatile int totalPunishments;

    public synchronized int incHit(long nowMs, int resetMin) {
        expireIfNeeded(nowMs, resetMin);
        hits++;
        lastHitMs = nowMs;
        return hits;
    }

    public synchronized int hits(long nowMs, int resetMin) {
        expireIfNeeded(nowMs, resetMin);
        return hits;
    }

    public synchronized void resetHits() {
        hits = 0;
        lastHitMs = 0L;
    }

    public synchronized int rawHits() {
        return hits;
    }

    public synchronized long lastHitMs() {
        return lastHitMs;
    }

    public synchronized boolean canSpawn(long nowMs) {
        return nowMs >= nextAllowedMs;
    }

    public synchronized void setCooldown(long nowMs, int cdSec) {
        nextAllowedMs = Math.max(nextAllowedMs, nowMs + cdSec * 1000L);
    }

    public synchronized void forceCooldown(long nowMs, int cdSec) {
        nextAllowedMs = nowMs + cdSec * 1000L;
    }

    public synchronized boolean takeSide(boolean altSides) {
        boolean side = nextLeft;
        if (altSides) {
            nextLeft = !nextLeft;
        }
        return side;
    }

    public int totalPunishments() {
        return totalPunishments;
    }

    public synchronized void incTotalPunishments() {
        totalPunishments++;
    }

    public synchronized void merge(int restoredPuns, int restoredHits, long restoredLastHitMs) {
        totalPunishments = restoredPuns;
        if (restoredHits > 0) {
            hits += restoredHits;
            lastHitMs = Math.max(lastHitMs, restoredLastHitMs);
        }
    }

    public synchronized void resetSess() {
        hits = 0;
        lastHitMs = 0L;
        nextAllowedMs = 0L;
    }

    private void expireIfNeeded(long nowMs, int resetMin) {
        if (resetMin > 0 && hits > 0 && lastHitMs > 0L && nowMs - lastHitMs >= resetMin * 60_000L) {
            hits = 0;
        }
    }
}
