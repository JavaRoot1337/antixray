package dev.javaroot.antixray.sess.track;

public record Step(
        String worldName,
        int x,
        int y,
        int z,
        double dirX,
        double dirZ,
        boolean ore,
        long tsMs
) {
}
