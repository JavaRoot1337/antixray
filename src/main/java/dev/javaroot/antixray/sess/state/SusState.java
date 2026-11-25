package dev.javaroot.antixray.sess.state;

public final class SusState {

    private double score;
    private long watchUntilMs;

    public synchronized double score() {
        return score;
    }

    public synchronized long watchUntilMs() {
        return watchUntilMs;
    }

    public synchronized double add(double delta, double maxScore, double threshold, int watchSec, long nowMs) {
        if (delta <= 0.0) {
            return score;
        }
        score = Math.min(maxScore, score + delta);
        if (score >= threshold) {
            watchUntilMs = Math.max(watchUntilMs, nowMs + watchSec * 1000L);
        }
        return score;
    }

    public synchronized void decay(double amount, long nowMs) {
        if (amount <= 0.0 || score <= 0.0) {
            return;
        }
        score = Math.max(0.0, score - amount);
        if (score == 0.0 && nowMs >= watchUntilMs) {
            watchUntilMs = 0L;
        }
    }

    public synchronized boolean isWatch(long nowMs, double threshold) {
        return score >= threshold || nowMs < watchUntilMs;
    }

    public synchronized long watchRemSec(long nowMs) {
        if (watchUntilMs <= nowMs) {
            return 0L;
        }
        return (watchUntilMs - nowMs + 999L) / 1000L;
    }

    public synchronized void merge(double restoredScore, long restoredWatchMs) {
        if (restoredScore > 0.0) {
            score = Math.max(score, restoredScore);
        }
        if (restoredWatchMs > 0L) {
            watchUntilMs = Math.max(watchUntilMs, restoredWatchMs);
        }
    }

    public synchronized void reset() {
        score = 0.0;
        watchUntilMs = 0L;
    }
}
