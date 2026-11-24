package dev.javaroot.antixray.trap;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.WorldCfg;
import dev.javaroot.antixray.mdl.Session;
import dev.javaroot.antixray.mdl.Vein;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class TrapSpawn {

    private final Supplier<Cfg> cfgSupplier;
    private final TrapMgr trapMgr;

    public TrapSpawn(Supplier<Cfg> cfgSupplier, TrapMgr trapMgr) {
        this.cfgSupplier = cfgSupplier;
        this.trapMgr = trapMgr;
    }

    public boolean tryAutoSpawn(Player player, Session session, WorldCfg worldCfg) {
        Cfg cfg = cfgSupplier.get();
        long nowMs = System.currentTimeMillis();
        if (trapMgr.activeCount(player.getUniqueId()) >= cfg.maxActiveVeinsPerPlayer) {
            return false;
        }
        if (!session.canSpawnTrap(nowMs)) {
            return false;
        }
        boolean spawned = spawnInternal(player, session, worldCfg, null, cfg, nowMs);
        if (spawned) {
            session.setNextTrapCooldown(nowMs, cfg.spawnCooldownSeconds);
        }
        return spawned;
    }

    public boolean spawnManual(Player player, Session session, WorldCfg worldCfg, Material forcedOre) {
        Cfg cfg = cfgSupplier.get();
        long nowMs = System.currentTimeMillis();
        return spawnInternal(player, session, worldCfg, forcedOre, cfg, nowMs);
    }

    private boolean spawnInternal(
            Player player,
            Session session,
            WorldCfg worldCfg,
            Material forcedOre,
            Cfg cfg,
            long nowMs
    ) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }

        boolean leftSide = session.takeNextSide(cfg.alternateSides);
        int targetSize = cfg.veinSizeMin;
        if (cfg.veinSizeMax > cfg.veinSizeMin) {
            targetSize = ThreadLocalRandom.current().nextInt(cfg.veinSizeMin, cfg.veinSizeMax + 1);
        }

        for (int attempt = 0; attempt < cfg.maxSpawnAttempts; attempt++) {
            TrapGeo.Candidate cand = TrapGeo.nextCandidate(loc, leftSide, cfg);
            if (!worldCfg.isInRange(cand.y())) {
                continue;
            }
            if (!isValidTrapSpot(world, player.getUniqueId(), cand.x(), cand.y(), cand.z(), worldCfg, cfg)) {
                continue;
            }

            Material ore = pickOre(forcedOre, worldCfg, cand.y());
            if (ore == null) {
                continue;
            }
            WrappedBlockState phantomState = SpigotConversionUtil.fromBukkitBlockData(ore.createBlockData());

            List<Vein.BlockEntry> entries = new ArrayList<>(targetSize);
            Block origin = world.getBlockAt(cand.x(), cand.y(), cand.z());
            entries.add(new Vein.BlockEntry(
                    cand.x(),
                    cand.y(),
                    cand.z(),
                    SpigotConversionUtil.fromBukkitBlockData(origin.getBlockData()),
                    phantomState
            ));

            if (targetSize > 1) {
                expandVein(world, player.getUniqueId(), worldCfg, cfg, phantomState, entries, targetSize);
            }

            Vein vein = new Vein(
                    UUID.randomUUID(),
                    player.getUniqueId(),
                    world.getName(),
                    ore,
                    entries,
                    nowMs,
                    nowMs + cfg.ttlSeconds * 1000L,
                    leftSide
            );
            trapMgr.addVein(vein);
            TrapMgr.sendSpawnPackets(player, vein);
            return true;
        }
        return false;
    }

    private void expandVein(
            World world,
            UUID playerId,
            WorldCfg worldCfg,
            Cfg cfg,
            WrappedBlockState phantomState,
            List<Vein.BlockEntry> entries,
            int targetSize
    ) {
        int[][] offsets = TrapGeo.NEIGHBOR_OFFSETS;
        for (int idx = 0; idx < entries.size() && entries.size() < targetSize; idx++) {
            Vein.BlockEntry base = entries.get(idx);
            int start = ThreadLocalRandom.current().nextInt(offsets.length);
            for (int step = 0; step < offsets.length && entries.size() < targetSize; step++) {
                int[] d = offsets[(start + step) % offsets.length];
                int nx = base.x() + d[0];
                int ny = base.y() + d[1];
                int nz = base.z() + d[2];
                if (!worldCfg.isInRange(ny) || containsCoord(entries, nx, ny, nz)) {
                    continue;
                }
                if (!isValidTrapSpot(world, playerId, nx, ny, nz, worldCfg, cfg)) {
                    continue;
                }
                Block b = world.getBlockAt(nx, ny, nz);
                entries.add(new Vein.BlockEntry(
                        nx,
                        ny,
                        nz,
                        SpigotConversionUtil.fromBukkitBlockData(b.getBlockData()),
                        phantomState
                ));
            }
        }
    }

    private static boolean containsCoord(List<Vein.BlockEntry> entries, int x, int y, int z) {
        for (Vein.BlockEntry entry : entries) {
            if (entry.x() == x && entry.y() == y && entry.z() == z) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidTrapSpot(
            World world,
            UUID playerId,
            int x,
            int y,
            int z,
            WorldCfg worldCfg,
            Cfg cfg
    ) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return false;
        }
        if (!isBlockAccessible(world, x, z)) {
            return false;
        }
        if (trapMgr.overlapsAny(playerId, world.getName(), x, y, z)) {
            return false;
        }
        Block target = world.getBlockAt(x, y, z);
        if (!worldCfg.isReplaceable(target.getType())) {
            return false;
        }
        if (!cfg.requireAllSixSolidNeighbors) {
            return true;
        }
        for (int[] d : TrapGeo.NEIGHBOR_OFFSETS) {
            int nx = x + d[0];
            int ny = y + d[1];
            int nz = z + d[2];
            if (ny < world.getMinHeight() || ny >= world.getMaxHeight() || !isBlockAccessible(world, nx, nz)) {
                return false;
            }
            Material neighborType = world.getBlockAt(nx, ny, nz).getType();
            if (!neighborType.isSolid() || !neighborType.isOccluding()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlockAccessible(World world, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        return world.isChunkLoaded(chunkX, chunkZ) && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ);
    }

    private static Material pickOre(Material forcedOre, WorldCfg worldCfg, int y) {
        if (forcedOre != null) {
            return forcedOre;
        }
        List<Material> pool = worldCfg.phantomOresForY(y);
        if (pool.isEmpty()) {
            return null;
        }
        if (pool.size() == 1) {
            return pool.get(0);
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
