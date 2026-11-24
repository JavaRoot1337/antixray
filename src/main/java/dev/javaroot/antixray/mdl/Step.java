package dev.javaroot.antixray.mdl;

public record Step(
        String worldName,
        int x,
        int y,
        int z,
        double dirX,
        double dirZ,
        boolean ore,
        long timestampMs
) {
}
