package dev.javaroot.antixray.sess.track;

import dev.javaroot.antixray.plat.world.LookVec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class RotTrack {

    private static final double EYE_H = 1.62;
    private static final long STAB_HOLD_MS = 250L;

    private final Object lock = new Object();
    private float[] yaws = new float[64];
    private float[] pitches = new float[64];
    private double[] xs = new double[64];
    private double[] eyeYs = new double[64];
    private double[] zs = new double[64];
    private long[] ts = new long[64];
    private int head;
    private int cnt;

    private double lastX;
    private double lastY;
    private double lastZ;
    private float lastYaw;
    private float lastPitch;
    private boolean hasPos;
    private boolean hasRot;

    private boolean pendTurn;
    private long pendStartMs;
    private long lastFastMs;
    private float pendPreYaw;
    private float pendPrePitch;
    private double pendAngle;
    private double pendX;
    private double pendEyeY;
    private double pendZ;

    private final Deque<TurnAnchor> anchors = new ArrayDeque<>(16);
    private final Deque<Long> reactHits = new ArrayDeque<>(8);

    public int recReactHit(long nowMs, int winSec) {
        synchronized (lock) {
            reactHits.addLast(nowMs);
            long cutoff = nowMs - winSec * 1000L;
            while (!reactHits.isEmpty() && reactHits.peekFirst() < cutoff) {
                reactHits.removeFirst();
            }
            return reactHits.size();
        }
    }

    public void recPos(
            double x, double y, double z, long nowMs,
            int bufSize, double minTurnDeg, int turnWinMs, double stabDegSec
    ) {
        synchronized (lock) {
            lastX = x;
            lastY = y;
            lastZ = z;
            hasPos = true;
            if (pendTurn && hasRot && nowMs - lastFastMs >= STAB_HOLD_MS) {
                recSampleLocked(x, y + EYE_H, z, lastYaw, lastPitch, nowMs, bufSize, minTurnDeg, turnWinMs, stabDegSec);
            }
        }
    }

    public void recRot(
            float yaw, float pitch, long nowMs,
            int bufSize, double minTurnDeg, int turnWinMs, double stabDegSec
    ) {
        synchronized (lock) {
            double x = hasPos ? lastX : Double.NaN;
            double eyeY = hasPos ? lastY + EYE_H : Double.NaN;
            double z = hasPos ? lastZ : Double.NaN;
            recSampleLocked(x, eyeY, z, yaw, pitch, nowMs, bufSize, minTurnDeg, turnWinMs, stabDegSec);
        }
    }

    public void recPosRot(
            double x, double y, double z, float yaw, float pitch, long nowMs,
            int bufSize, double minTurnDeg, int turnWinMs, double stabDegSec
    ) {
        synchronized (lock) {
            lastX = x;
            lastY = y;
            lastZ = z;
            hasPos = true;
            recSampleLocked(x, y + EYE_H, z, yaw, pitch, nowMs, bufSize, minTurnDeg, turnWinMs, stabDegSec);
        }
    }

    public List<TurnAnchor> anchorsSnap(long nowMs, long maxLookbackMs) {
        synchronized (lock) {
            if (pendTurn && hasRot && !Double.isNaN(pendX) && nowMs - lastFastMs >= STAB_HOLD_MS) {
                emitLocked(lastYaw, lastPitch, lastFastMs + STAB_HOLD_MS);
                pendTurn = false;
            }
            long cutoff = nowMs - maxLookbackMs;
            while (!anchors.isEmpty() && anchors.peekFirst().stabilizedAtMs() < cutoff) {
                anchors.removeFirst();
            }
            return anchors.isEmpty() ? List.of() : new ArrayList<>(anchors);
        }
    }

    public void reset() {
        synchronized (lock) {
            head = 0;
            cnt = 0;
            hasPos = false;
            hasRot = false;
            pendTurn = false;
            anchors.clear();
            reactHits.clear();
        }
    }

    private void recSampleLocked(
            double x, double eyeY, double z, float yaw, float pitch, long nowMs,
            int bufSize, double minTurnDeg, int turnWinMs, double stabDegSec
    ) {
        ensureCapLocked(bufSize);
        int cap = yaws.length;

        float prevYaw = yaw;
        float prevPitch = pitch;
        long prevTs = nowMs;
        if (cnt > 0) {
            int prevIdx = (head - 1 + cap) % cap;
            prevYaw = yaws[prevIdx];
            prevPitch = pitches[prevIdx];
            prevTs = ts[prevIdx];
        }

        yaws[head] = yaw;
        pitches[head] = pitch;
        xs[head] = x;
        eyeYs[head] = eyeY;
        zs[head] = z;
        ts[head] = nowMs;
        head = (head + 1) % cap;
        if (cnt < cap) {
            cnt++;
        }
        lastYaw = yaw;
        lastPitch = pitch;
        hasRot = true;

        if (!pendTurn) {
            detectStartLocked(x, eyeY, z, yaw, pitch, nowMs, cap, minTurnDeg, turnWinMs);
            return;
        }

        long dtMs = nowMs - prevTs;
        double stepDeg = LookVec.angle3dDeg(prevYaw, prevPitch, yaw, pitch);
        double degPerSec = dtMs > 0 ? (stepDeg * 1000.0 / dtMs) : 0.0;
        double totalDeg = LookVec.angle3dDeg(pendPreYaw, pendPrePitch, yaw, pitch);
        if (totalDeg > pendAngle && !Double.isNaN(x)) {
            pendAngle = totalDeg;
            pendX = x;
            pendEyeY = eyeY;
            pendZ = z;
        }

        if (degPerSec > stabDegSec) {
            lastFastMs = nowMs;
        } else if (nowMs - lastFastMs >= STAB_HOLD_MS) {
            if (!Double.isNaN(pendX) && pendAngle >= minTurnDeg) {
                emitLocked(yaw, pitch, nowMs);
            }
            pendTurn = false;
        } else if (nowMs - pendStartMs > 3000L) {
            pendTurn = false;
        }
    }

    private void detectStartLocked(
            double x, double eyeY, double z, float yaw, float pitch, long nowMs,
            int cap, double minTurnDeg, int turnWinMs
    ) {
        double maxDeg = 0.0;
        float preYaw = yaw;
        float prePitch = pitch;
        long startTs = nowMs;
        for (int i = 1; i < cnt; i++) {
            int idx = (head - 1 - i + cap) % cap;
            long sampleTs = ts[idx];
            if (nowMs - sampleTs > turnWinMs) {
                break;
            }
            double deg = LookVec.angle3dDeg(yaws[idx], pitches[idx], yaw, pitch);
            if (deg > maxDeg) {
                maxDeg = deg;
                preYaw = yaws[idx];
                prePitch = pitches[idx];
                startTs = sampleTs;
            }
        }
        if (maxDeg >= minTurnDeg && !Double.isNaN(x)) {
            pendTurn = true;
            pendStartMs = startTs;
            pendPreYaw = preYaw;
            pendPrePitch = prePitch;
            pendAngle = maxDeg;
            pendX = x;
            pendEyeY = eyeY;
            pendZ = z;
            lastFastMs = nowMs;
        }
    }

    private void emitLocked(float postYaw, float postPitch, long stabAtMs) {
        if (anchors.size() >= 16) {
            anchors.removeFirst();
        }
        anchors.addLast(new TurnAnchor(
                pendX, pendEyeY, pendZ, pendPreYaw, pendPrePitch,
                postYaw, postPitch, pendAngle, pendStartMs, stabAtMs
        ));
    }

    private void ensureCapLocked(int desired) {
        if (yaws.length == desired) {
            return;
        }
        yaws = new float[desired];
        pitches = new float[desired];
        xs = new double[desired];
        eyeYs = new double[desired];
        zs = new double[desired];
        ts = new long[desired];
        head = 0;
        cnt = 0;
        pendTurn = false;
    }
}
