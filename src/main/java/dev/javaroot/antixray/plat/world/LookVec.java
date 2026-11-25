package dev.javaroot.antixray.plat.world;

public final class LookVec {

    private LookVec() {
    }

    public static double angle3dDeg(float yaw1, float pitch1, float yaw2, float pitch2) {
        double y1 = Math.toRadians(yaw1);
        double p1 = Math.toRadians(pitch1);
        double c1 = Math.cos(p1);
        double x1 = -c1 * Math.sin(y1);
        double vy1 = -Math.sin(p1);
        double z1 = c1 * Math.cos(y1);

        double y2 = Math.toRadians(yaw2);
        double p2 = Math.toRadians(pitch2);
        double c2 = Math.cos(p2);
        double x2 = -c2 * Math.sin(y2);
        double vy2 = -Math.sin(p2);
        double z2 = c2 * Math.cos(y2);

        double dot = Math.max(-1.0, Math.min(1.0, x1 * x2 + vy1 * vy2 + z1 * z2));
        return Math.toDegrees(Math.acos(dot));
    }

    public static double aimErrorDeg(float yaw, float pitch, double dx, double dy, double dz, double dist) {
        if (dist <= 1e-6) {
            return 0.0;
        }
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        double cp = Math.cos(pr);
        double lx = -cp * Math.sin(yr);
        double ly = -Math.sin(pr);
        double lz = cp * Math.cos(yr);

        double dot = Math.max(-1.0, Math.min(1.0, (lx * dx + ly * dy + lz * dz) / dist));
        return Math.toDegrees(Math.acos(dot));
    }

    public static double absYawDiff(float yaw1, float yaw2) {
        double diff = Math.abs(yaw1 - yaw2) % 360.0;
        return diff > 180.0 ? 360.0 - diff : diff;
    }
}
