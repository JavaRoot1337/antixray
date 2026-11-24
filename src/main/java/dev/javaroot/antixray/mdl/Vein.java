package dev.javaroot.antixray.mdl;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import org.bukkit.Material;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Vein {

    public record BlockEntry(
            int x,
            int y,
            int z,
            WrappedBlockState realState,
            WrappedBlockState phantomState
    ) {
        public long chunkKey() {
            return (((long) (x >> 4)) << 32) | ((z >> 4) & 0xFFFFFFFFL);
        }
    }

    private final UUID id;
    private final UUID playerId;
    private final String worldName;
    private final Material phantomOre;
    private final List<BlockEntry> blocks;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final long createdAtMs;
    private final long expiresAtMs;
    private final boolean leftSide;
    private final AtomicBoolean triggered = new AtomicBoolean(false);

    public Vein(
            UUID id,
            UUID playerId,
            String worldName,
            Material phantomOre,
            List<BlockEntry> blocks,
            long createdAtMs,
            long expiresAtMs,
            boolean leftSide
    ) {
        this.id = id;
        this.playerId = playerId;
        this.worldName = worldName;
        this.phantomOre = phantomOre;
        this.blocks = List.copyOf(blocks);
        this.createdAtMs = createdAtMs;
        this.expiresAtMs = expiresAtMs;
        this.leftSide = leftSide;

        int sumX = 0;
        int sumY = 0;
        int sumZ = 0;
        for (BlockEntry entry : this.blocks) {
            sumX += entry.x();
            sumY += entry.y();
            sumZ += entry.z();
        }
        int size = Math.max(1, this.blocks.size());
        this.centerX = Math.round((float) sumX / size);
        this.centerY = Math.round((float) sumY / size);
        this.centerZ = Math.round((float) sumZ / size);
    }

    public UUID id() {
        return id;
    }

    public UUID playerId() {
        return playerId;
    }

    public String worldName() {
        return worldName;
    }

    public Material phantomOre() {
        return phantomOre;
    }

    public List<BlockEntry> blocks() {
        return blocks;
    }

    public int centerX() {
        return centerX;
    }

    public int centerY() {
        return centerY;
    }

    public int centerZ() {
        return centerZ;
    }

    public long createdAtMs() {
        return createdAtMs;
    }

    public long expiresAtMs() {
        return expiresAtMs;
    }

    public boolean leftSide() {
        return leftSide;
    }

    public boolean markTriggered() {
        return triggered.compareAndSet(false, true);
    }

    public boolean isTriggered() {
        return triggered.get();
    }

    public boolean isExpired(long nowMs) {
        return nowMs >= expiresAtMs;
    }

    public boolean contains(int x, int y, int z) {
        for (BlockEntry entry : blocks) {
            if (entry.x() == x && entry.y() == y && entry.z() == z) {
                return true;
            }
        }
        return false;
    }

    public BlockEntry entryAt(int x, int y, int z) {
        for (BlockEntry entry : blocks) {
            if (entry.x() == x && entry.y() == y && entry.z() == z) {
                return entry;
            }
        }
        return null;
    }

    public boolean overlapsChunk(int chunkX, int chunkZ) {
        for (BlockEntry entry : blocks) {
            if ((entry.x() >> 4) == chunkX && (entry.z() >> 4) == chunkZ) {
                return true;
            }
        }
        return false;
    }

    public double distanceSquared(double px, double py, double pz) {
        double dx = (centerX + 0.5) - px;
        double dy = (centerY + 0.5) - py;
        double dz = (centerZ + 0.5) - pz;
        return dx * dx + dy * dy + dz * dz;
    }
}
