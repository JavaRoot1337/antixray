package dev.javaroot.antixray.stor;

import dev.javaroot.antixray.mdl.Session;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class HideMap {

    public record HiddenEntry(int x, int y, int z, int maskGlobalId) {
    }

    private record WorldChunk(String world, long chunkKey) {
    }

    // world -> chunkKey -> packedBlock -> (playerId -> maskGlobalId)
    private final Map<String, Map<Long, Map<Long, Map<UUID, Integer>>>> byWorld = new ConcurrentHashMap<>();
    private final Map<UUID, Set<WorldChunk>> playerChunks = new ConcurrentHashMap<>();

    public void replaceChunk(UUID playerId, String world, int chunkX, int chunkZ, List<HiddenEntry> entries) {
        clearChunk(playerId, world, chunkX, chunkZ);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        long cKey = chunkKey(chunkX, chunkZ);
        Map<Long, Map<Long, Map<UUID, Integer>>> worldMap =
                byWorld.computeIfAbsent(world, w -> new ConcurrentHashMap<>());

        worldMap.compute(cKey, (k, existing) -> {
            Map<Long, Map<UUID, Integer>> chunkMap = existing != null ? existing : new ConcurrentHashMap<>();
            for (HiddenEntry entry : entries) {
                long bKey = Session.packBlock(entry.x(), entry.y(), entry.z());
                chunkMap.computeIfAbsent(bKey, bk -> new ConcurrentHashMap<>())
                        .put(playerId, entry.maskGlobalId());
            }
            return chunkMap;
        });

        playerChunks.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet())
                .add(new WorldChunk(world, cKey));
    }

    public boolean isHidden(UUID playerId, String world, int x, int y, int z) {
        return maskId(playerId, world, x, y, z) != null;
    }

    public Integer maskId(UUID playerId, String world, int x, int y, int z) {
        Map<Long, Map<Long, Map<UUID, Integer>>> worldMap = byWorld.get(world);
        if (worldMap == null) {
            return null;
        }
        Map<Long, Map<UUID, Integer>> chunkMap = worldMap.get(chunkKey(x >> 4, z >> 4));
        if (chunkMap == null) {
            return null;
        }
        Map<UUID, Integer> players = chunkMap.get(Session.packBlock(x, y, z));
        return players == null ? null : players.get(playerId);
    }

    public boolean hasHiddenInChunk(UUID playerId, String world, int chunkX, int chunkZ) {
        Set<WorldChunk> chunks = playerChunks.get(playerId);
        return chunks != null && chunks.contains(new WorldChunk(world, chunkKey(chunkX, chunkZ)));
    }

    public boolean hasHiddenNear(String world, int x, int y, int z) {
        Map<Long, Map<Long, Map<UUID, Integer>>> worldMap = byWorld.get(world);
        if (worldMap == null || worldMap.isEmpty()) {
            return false;
        }
        return containsBlock(worldMap, x, y, z)
                || containsBlock(worldMap, x + 1, y, z)
                || containsBlock(worldMap, x - 1, y, z)
                || containsBlock(worldMap, x, y + 1, z)
                || containsBlock(worldMap, x, y - 1, z)
                || containsBlock(worldMap, x, y, z + 1)
                || containsBlock(worldMap, x, y, z - 1);
    }

    public boolean hasHiddenInArea(String world, int x, int z, int radius) {
        Map<Long, Map<Long, Map<UUID, Integer>>> worldMap = byWorld.get(world);
        if (worldMap == null || worldMap.isEmpty()) {
            return false;
        }
        int minCx = (x - radius) >> 4;
        int maxCx = (x + radius) >> 4;
        int minCz = (z - radius) >> 4;
        int maxCz = (z + radius) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                Map<Long, Map<UUID, Integer>> chunkMap = worldMap.get(chunkKey(cx, cz));
                if (chunkMap != null && !chunkMap.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsBlock(
            Map<Long, Map<Long, Map<UUID, Integer>>> worldMap,
            int x,
            int y,
            int z
    ) {
        Map<Long, Map<UUID, Integer>> chunkMap = worldMap.get(chunkKey(x >> 4, z >> 4));
        if (chunkMap == null || chunkMap.isEmpty()) {
            return false;
        }
        Map<UUID, Integer> players = chunkMap.get(Session.packBlock(x, y, z));
        return players != null && !players.isEmpty();
    }

    public Set<UUID> popPlayersAt(String world, int x, int y, int z) {
        Map<Long, Map<Long, Map<UUID, Integer>>> worldMap = byWorld.get(world);
        if (worldMap == null) {
            return Set.of();
        }
        long cKey = chunkKey(x >> 4, z >> 4);
        long bKey = Session.packBlock(x, y, z);
        AtomicReference<Set<UUID>> popped = new AtomicReference<>(Set.of());
        boolean[] emptied = {false};

        worldMap.computeIfPresent(cKey, (k, chunkMap) -> {
            Map<UUID, Integer> removed = chunkMap.remove(bKey);
            if (removed == null || removed.isEmpty()) {
                return chunkMap;
            }
            popped.set(new HashSet<>(removed.keySet()));
            if (chunkMap.isEmpty()) {
                emptied[0] = true;
                return null;
            }
            return chunkMap;
        });

        Set<UUID> viewers = popped.get();
        if (emptied[0] && !viewers.isEmpty()) {
            WorldChunk wc = new WorldChunk(world, cKey);
            for (UUID id : viewers) {
                Set<WorldChunk> set = playerChunks.get(id);
                if (set != null) {
                    set.remove(wc);
                }
            }
        }
        return viewers;
    }

    public void removeEntry(UUID playerId, String world, int x, int y, int z) {
        Map<Long, Map<Long, Map<UUID, Integer>>> worldMap = byWorld.get(world);
        if (worldMap == null) {
            return;
        }
        long cKey = chunkKey(x >> 4, z >> 4);
        long bKey = Session.packBlock(x, y, z);
        boolean[] emptied = {false};

        worldMap.computeIfPresent(cKey, (k, chunkMap) -> {
            Map<UUID, Integer> players = chunkMap.get(bKey);
            if (players == null) {
                return chunkMap;
            }
            players.remove(playerId);
            if (players.isEmpty()) {
                chunkMap.remove(bKey, players);
                if (chunkMap.isEmpty()) {
                    emptied[0] = true;
                    return null;
                }
            }
            return chunkMap;
        });

        if (emptied[0]) {
            Set<WorldChunk> set = playerChunks.get(playerId);
            if (set != null) {
                set.remove(new WorldChunk(world, cKey));
            }
        }
    }

    public void clearChunk(UUID playerId, String world, int chunkX, int chunkZ) {
        long cKey = chunkKey(chunkX, chunkZ);
        clearChunkByKey(playerId, world, cKey);
        Set<WorldChunk> chunks = playerChunks.get(playerId);
        if (chunks != null) {
            chunks.remove(new WorldChunk(world, cKey));
        }
    }

    public void clearPlayerWorld(UUID playerId, String world) {
        Set<WorldChunk> chunks = playerChunks.get(playerId);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        Iterator<WorldChunk> it = chunks.iterator();
        while (it.hasNext()) {
            WorldChunk wc = it.next();
            if (wc.world().equals(world)) {
                it.remove();
                clearChunkByKey(playerId, world, wc.chunkKey());
            }
        }
    }

    public void clearPlayer(UUID playerId) {
        Set<WorldChunk> chunks = playerChunks.remove(playerId);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        for (WorldChunk wc : chunks) {
            clearChunkByKey(playerId, wc.world(), wc.chunkKey());
        }
    }

    private void clearChunkByKey(UUID playerId, String world, long cKey) {
        Map<Long, Map<Long, Map<UUID, Integer>>> worldMap = byWorld.get(world);
        if (worldMap == null) {
            return;
        }
        worldMap.computeIfPresent(cKey, (k, chunkMap) -> {
            chunkMap.forEach((bKey, players) -> {
                players.remove(playerId);
                if (players.isEmpty()) {
                    chunkMap.remove(bKey, players);
                }
            });
            return chunkMap.isEmpty() ? null : chunkMap;
        });
    }

    public void clearAll() {
        byWorld.clear();
        playerChunks.clear();
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
