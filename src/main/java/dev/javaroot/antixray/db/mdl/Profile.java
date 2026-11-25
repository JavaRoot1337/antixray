package dev.javaroot.antixray.db.mdl;

public record Profile(
        int totalPunishments,
        double susScore,
        long watchUntilMs,
        int phantomHits,
        long lastPhantomHitMs
) {
    public static final Profile EMPTY = new Profile(0, 0.0, 0L, 0, 0L);
}
