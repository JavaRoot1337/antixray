package dev.javaroot.antixray.sess.state;

import dev.javaroot.antixray.plat.world.Pos;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BrokenMem {

    private final Map<Long, Long> brokenUntil = new ConcurrentHashMap<>();

    public void record(int x, int y, int z, long nowMs, int memSec) {
        long key = Pos.packBlock(x, y, z);
        brokenUntil.put(key, nowMs + memSec * 1000L);
        if (brokenUntil.size() > 512) {
            prune(nowMs);
        }
    }

    public boolean isRecent(int x, int y, int z, long nowMs) {
        long key = Pos.packBlock(x, y, z);
        Long exp = brokenUntil.get(key);
        if (exp == null) {
            return false;
        }
        if (exp < nowMs) {
            brokenUntil.remove(key, exp);
            return false;
        }
        return true;
    }

    public boolean wasIntactAt(int x, int y, int z, long atMs, int memSec) {
        Long exp = brokenUntil.get(Pos.packBlock(x, y, z));
        if (exp == null) {
            return true;
        }
        return exp - memSec * 1000L >= atMs;
    }

    public long latestNeighborMs(int ox, int oy, int oz, long nowMs, int memSec) {
        long latest = 0L;
        long memMs = memSec * 1000L;
        for (int[] off : Pos.DIRS) {
            Long exp = brokenUntil.get(Pos.packBlock(ox + off[0], oy + off[1], oz + off[2]));
            if (exp == null || exp < nowMs) {
                continue;
            }
            long brokenAt = exp - memMs;
            if (brokenAt > latest) {
                latest = brokenAt;
            }
        }
        return latest;
    }

    public void prune(long nowMs) {
        Iterator<Map.Entry<Long, Long>> it = brokenUntil.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < nowMs) {
                it.remove();
            }
        }
    }

    public void clear() {
        brokenUntil.clear();
    }
}
