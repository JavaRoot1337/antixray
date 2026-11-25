package dev.javaroot.antixray.trap.mdl;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import dev.javaroot.antixray.plat.world.Pos;
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
            return Pos.packChunk(x >> 4, z >> 4);
        }
    }

    private final UUID id;
    private final UUID plrId;
    private final String world;
    private final Material ore;
    private final List<BlockEntry> blocks;
    private final int cx;
    private final int cy;
    private final int cz;
    private final long createdAtMs;
    private final long expiresAtMs;
    private final boolean leftSide;
    private final AtomicBoolean triggered = new AtomicBoolean(false);

    public Vein(
            UUID id,
            UUID plrId,
            String world,
            Material ore,
            List<BlockEntry> blocks,
            long createdAtMs,
            long expiresAtMs,
            boolean leftSide
    ) {
        this.id = id;
        this.plrId = plrId;
        this.world = world;
        this.ore = ore;
        this.blocks = List.copyOf(blocks);
        this.createdAtMs = createdAtMs;
        this.expiresAtMs = expiresAtMs;
        this.leftSide = leftSide;

        int sx = 0;
        int sy = 0;
        int sz = 0;
        for (BlockEntry e : this.blocks) {
            sx += e.x();
            sy += e.y();
            sz += e.z();
        }
        int szCnt = Math.max(1, this.blocks.size());
        this.cx = Math.round((float) sx / szCnt);
        this.cy = Math.round((float) sy / szCnt);
        this.cz = Math.round((float) sz / szCnt);
    }

    public UUID id() { return id; }
    public UUID plrId() { return plrId; }
    public String world() { return world; }
    public Material ore() { return ore; }
    public List<BlockEntry> blocks() { return blocks; }
    public int cx() { return cx; }
    public int cy() { return cy; }
    public int cz() { return cz; }
    public long createdAtMs() { return createdAtMs; }
    public long expiresAtMs() { return expiresAtMs; }
    public boolean leftSide() { return leftSide; }

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
        return entryAt(x, y, z) != null;
    }

    public BlockEntry entryAt(int x, int y, int z) {
        for (BlockEntry e : blocks) {
            if (e.x() == x && e.y() == y && e.z() == z) {
                return e;
            }
        }
        return null;
    }

    public boolean overlapsChunk(int chunkX, int chunkZ) {
        for (BlockEntry e : blocks) {
            if ((e.x() >> 4) == chunkX && (e.z() >> 4) == chunkZ) {
                return true;
            }
        }
        return false;
    }

    public double distSq(double px, double py, double pz) {
        double dx = (cx + 0.5) - px;
        double dy = (cy + 0.5) - py;
        double dz = (cz + 0.5) - pz;
        return dx * dx + dy * dy + dz * dz;
    }
}
