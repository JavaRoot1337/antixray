package dev.javaroot.antixray.sess.track;

public record TurnAnchor(
        double x,
        double eyeY,
        double z,
        float preYaw,
        float prePitch,
        float postYaw,
        float postPitch,
        double turnDeg,
        long startedAtMs,
        long stabilizedAtMs
) {
}
