package dev.javaroot.antixray.stor.mask;

import dev.javaroot.antixray.plat.world.Pos;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class HideMap {

    public record HiddenEntry(int x, int y, int z, int maskId) {
    }

    private record WorldChunk(String world, long chunkKey) {
    }

    // world -> chunkKey -> packedBlock -> (plrId -> maskId)
    private final Map<String, Map<Long, Map<Long, Map<UUID, Integer>>>> byWorld = new ConcurrentHashMap<>();
    private final Map<UUID, Set<WorldChunk>> plrChunks = new ConcurrentHashMap<>();

    public void replaceChunk(UUID plrId, String world, int cx, int cz, List<HiddenEntry> entries) {
        clearChunk(plrId, world, cx, cz);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        long cKey = Pos.packChunk(cx, cz);
        Map<Long, Map<Long, Map<UUID, Integer>>> wMap = byWorld.computeIfAbsent(world, w -> new ConcurrentHashMap<>());

        wMap.compute(cKey, (k, existing) -> {
            Map<Long, Map<UUID, Integer>> cMap = existing != null ? existing : new ConcurrentHashMap<>();
            for (HiddenEntry e : entries) {
                long bKey = Pos.packBlock(e.x(), e.y(), e.z());
                cMap.computeIfAbsent(bKey, bk -> new ConcurrentHashMap<>()).put(plrId, e.maskId());
            }
            return cMap;
        });

        plrChunks.computeIfAbsent(plrId, k -> ConcurrentHashMap.newKeySet()).add(new WorldChunk(world, cKey));
    }

    public boolean isHidden(UUID plrId, String world, int x, int y, int z) {
        return maskId(plrId, world, x, y, z) != null;
    }

    public Integer maskId(UUID plrId, String world, int x, int y, int z) {
        Map<Long, Map<Long, Map<UUID, Integer>>> wMap = byWorld.get(world);
        if (wMap == null) {
            return null;
        }
        Map<Long, Map<UUID, Integer>> cMap = wMap.get(Pos.packChunk(x >> 4, z >> 4));
        if (cMap == null) {
            return null;
        }
        Map<UUID, Integer> plrs = cMap.get(Pos.packBlock(x, y, z));
        return plrs == null ? null : plrs.get(plrId);
    }

    public boolean hasInChunk(UUID plrId, String world, int cx, int cz) {
        Set<WorldChunk> chunks = plrChunks.get(plrId);
        return chunks != null && chunks.contains(new WorldChunk(world, Pos.packChunk(cx, cz)));
    }

    public boolean hasNear(String world, int x, int y, int z) {
        Map<Long, Map<Long, Map<UUID, Integer>>> wMap = byWorld.get(world);
        if (wMap == null || wMap.isEmpty()) {
            return false;
        }
        if (hasBlock(wMap, x, y, z)) {
            return true;
        }
        for (int[] off : Pos.DIRS) {
            if (hasBlock(wMap, x + off[0], y + off[1], z + off[2])) {
                return true;
            }
        }
        return false;
    }

    public boolean hasInArea(String world, int x, int z, int radius) {
        Map<Long, Map<Long, Map<UUID, Integer>>> wMap = byWorld.get(world);
        if (wMap == null || wMap.isEmpty()) {
            return false;
        }
        int minCx = (x - radius) >> 4;
        int maxCx = (x + radius) >> 4;
        int minCz = (z - radius) >> 4;
        int maxCz = (z + radius) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                Map<Long, Map<UUID, Integer>> cMap = wMap.get(Pos.packChunk(cx, cz));
                if (cMap != null && !cMap.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasBlock(Map<Long, Map<Long, Map<UUID, Integer>>> wMap, int x, int y, int z) {
        Map<Long, Map<UUID, Integer>> cMap = wMap.get(Pos.packChunk(x >> 4, z >> 4));
        if (cMap == null || cMap.isEmpty()) {
            return false;
        }
        Map<UUID, Integer> plrs = cMap.get(Pos.packBlock(x, y, z));
        return plrs != null && !plrs.isEmpty();
    }

    public Set<UUID> popAt(String world, int x, int y, int z) {
        Map<Long, Map<Long, Map<UUID, Integer>>> wMap = byWorld.get(world);
        if (wMap == null) {
            return Set.of();
        }
        long cKey = Pos.packChunk(x >> 4, z >> 4);
        long bKey = Pos.packBlock(x, y, z);
        AtomicReference<Set<UUID>> popped = new AtomicReference<>(Set.of());
        boolean[] emptied = {false};

        wMap.computeIfPresent(cKey, (k, cMap) -> {
            Map<UUID, Integer> rem = cMap.remove(bKey);
            if (rem == null || rem.isEmpty()) {
                return cMap;
            }
            popped.set(new HashSet<>(rem.keySet()));
            if (cMap.isEmpty()) {
                emptied[0] = true;
                return null;
            }
            return cMap;
        });

        Set<UUID> viewers = popped.get();
        if (emptied[0] && !viewers.isEmpty()) {
            WorldChunk wc = new WorldChunk(world, cKey);
            for (UUID id : viewers) {
                Set<WorldChunk> set = plrChunks.get(id);
                if (set != null) {
                    set.remove(wc);
                }
            }
        }
        return viewers;
    }

    public void removeEntry(UUID plrId, String world, int x, int y, int z) {
        Map<Long, Map<Long, Map<UUID, Integer>>> wMap = byWorld.get(world);
        if (wMap == null) {
            return;
        }
        long cKey = Pos.packChunk(x >> 4, z >> 4);
        long bKey = Pos.packBlock(x, y, z);
        boolean[] emptied = {false};

        wMap.computeIfPresent(cKey, (k, cMap) -> {
            Map<UUID, Integer> plrs = cMap.get(bKey);
            if (plrs == null) {
                return cMap;
            }
            plrs.remove(plrId);
            if (plrs.isEmpty()) {
                cMap.remove(bKey, plrs);
                if (cMap.isEmpty()) {
                    emptied[0] = true;
                    return null;
                }
            }
            return cMap;
        });

        if (emptied[0]) {
            Set<WorldChunk> set = plrChunks.get(plrId);
            if (set != null) {
                set.remove(new WorldChunk(world, cKey));
            }
        }
    }

    public void clearChunk(UUID plrId, String world, int cx, int cz) {
        long cKey = Pos.packChunk(cx, cz);
        clearChunkByKey(plrId, world, cKey);
        Set<WorldChunk> chunks = plrChunks.get(plrId);
        if (chunks != null) {
            chunks.remove(new WorldChunk(world, cKey));
        }
    }

    public void clearWorld(UUID plrId, String world) {
        Set<WorldChunk> chunks = plrChunks.get(plrId);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        Iterator<WorldChunk> it = chunks.iterator();
        while (it.hasNext()) {
            WorldChunk wc = it.next();
            if (wc.world().equals(world)) {
                it.remove();
                clearChunkByKey(plrId, world, wc.chunkKey());
            }
        }
    }

    public void clearPlayer(UUID plrId) {
        Set<WorldChunk> chunks = plrChunks.remove(plrId);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        for (WorldChunk wc : chunks) {
            clearChunkByKey(plrId, wc.world(), wc.chunkKey());
        }
    }

    private void clearChunkByKey(UUID plrId, String world, long cKey) {
        Map<Long, Map<Long, Map<UUID, Integer>>> wMap = byWorld.get(world);
        if (wMap == null) {
            return;
        }
        wMap.computeIfPresent(cKey, (k, cMap) -> {
            cMap.forEach((bKey, plrs) -> {
                plrs.remove(plrId);
                if (plrs.isEmpty()) {
                    cMap.remove(bKey, plrs);
                }
            });
            return cMap.isEmpty() ? null : cMap;
        });
    }

    public void clearAll() {
        byWorld.clear();
        plrChunks.clear();
    }
}
