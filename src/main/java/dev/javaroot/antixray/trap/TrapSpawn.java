package dev.javaroot.antixray.trap;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.WorldCfg;
import dev.javaroot.antixray.cfg.sec.TrapCfg;
import dev.javaroot.antixray.plat.world.Pos;
import dev.javaroot.antixray.plat.world.RegGuard;
import dev.javaroot.antixray.sess.Sess;
import dev.javaroot.antixray.trap.mdl.Vein;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
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

    private final Supplier<Cfg> cfgSup;
    private final TrapMgr trapMgr;

    public TrapSpawn(Supplier<Cfg> cfgSup, TrapMgr trapMgr) {
        this.cfgSup = cfgSup;
        this.trapMgr = trapMgr;
    }

    public boolean tryAuto(Player p, Sess sess, WorldCfg wCfg) {
        TrapCfg tc = cfgSup.get().trap();
        long nowMs = System.currentTimeMillis();
        if (trapMgr.activeCount(p.getUniqueId()) >= tc.maxActiveVeins() || !sess.traps().canSpawn(nowMs)) {
            return false;
        }
        boolean ok = spawnInternal(p, sess, wCfg, null, tc, nowMs);
        if (ok) {
            sess.traps().setCooldown(nowMs, tc.cooldownSec());
        }
        return ok;
    }

    public boolean spawnManual(Player p, Sess sess, WorldCfg wCfg, Material forcedOre) {
        return spawnInternal(p, sess, wCfg, forcedOre, cfgSup.get().trap(), System.currentTimeMillis());
    }

    private boolean spawnInternal(
            Player p, Sess sess, WorldCfg wCfg, Material forcedOre, TrapCfg tc, long nowMs
    ) {
        Location loc = p.getLocation();
        World w = loc.getWorld();
        if (w == null) {
            return false;
        }

        boolean leftSide = sess.traps().takeSide(tc.altSides());
        int targetSize = tc.veinMin();
        if (tc.veinMax() > tc.veinMin()) {
            targetSize = ThreadLocalRandom.current().nextInt(tc.veinMin(), tc.veinMax() + 1);
        }

        for (int attempt = 0; attempt < tc.maxAttempts(); attempt++) {
            TrapGeo.Cand cand = TrapGeo.nextCand(loc, leftSide, tc);
            if (!wCfg.isInRange(cand.y())) {
                continue;
            }
            if (!isValidSpot(w, p.getUniqueId(), cand.x(), cand.y(), cand.z(), wCfg, tc)) {
                continue;
            }

            Material ore = pickOre(forcedOre, wCfg, cand.y());
            if (ore == null) {
                continue;
            }
            WrappedBlockState phantom = SpigotConversionUtil.fromBukkitBlockData(ore.createBlockData());

            List<Vein.BlockEntry> entries = new ArrayList<>(targetSize);
            Block orig = w.getBlockAt(cand.x(), cand.y(), cand.z());
            entries.add(new Vein.BlockEntry(
                    cand.x(), cand.y(), cand.z(),
                    SpigotConversionUtil.fromBukkitBlockData(orig.getBlockData()),
                    phantom
            ));

            if (targetSize > 1) {
                expandVein(w, p.getUniqueId(), wCfg, tc, phantom, entries, targetSize);
            }

            Vein vein = new Vein(
                    UUID.randomUUID(), p.getUniqueId(), w.getName(), ore,
                    entries, nowMs, nowMs + tc.ttlSec() * 1000L, leftSide
            );
            trapMgr.addVein(vein);
            TrapMgr.sendSpawn(p, vein);
            return true;
        }
        return false;
    }

    private void expandVein(
            World w, UUID plrId, WorldCfg wCfg, TrapCfg tc,
            WrappedBlockState phantom, List<Vein.BlockEntry> entries, int targetSize
    ) {
        int[][] dirs = Pos.DIRS;
        for (int idx = 0; idx < entries.size() && entries.size() < targetSize; idx++) {
            Vein.BlockEntry base = entries.get(idx);
            int start = ThreadLocalRandom.current().nextInt(dirs.length);
            for (int step = 0; step < dirs.length && entries.size() < targetSize; step++) {
                int[] d = dirs[(start + step) % dirs.length];
                int nx = base.x() + d[0];
                int ny = base.y() + d[1];
                int nz = base.z() + d[2];
                if (!wCfg.isInRange(ny) || hasCoord(entries, nx, ny, nz)) {
                    continue;
                }
                if (!isValidSpot(w, plrId, nx, ny, nz, wCfg, tc)) {
                    continue;
                }
                Block b = w.getBlockAt(nx, ny, nz);
                entries.add(new Vein.BlockEntry(
                        nx, ny, nz, SpigotConversionUtil.fromBukkitBlockData(b.getBlockData()), phantom
                ));
            }
        }
    }

    private static boolean hasCoord(List<Vein.BlockEntry> entries, int x, int y, int z) {
        for (Vein.BlockEntry e : entries) {
            if (e.x() == x && e.y() == y && e.z() == z) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidSpot(World w, UUID plrId, int x, int y, int z, WorldCfg wCfg, TrapCfg tc) {
        if (y < w.getMinHeight() || y >= w.getMaxHeight() || !RegGuard.isAccess(w, x, z)) {
            return false;
        }
        if (trapMgr.overlapsAny(plrId, w.getName(), x, y, z)) {
            return false;
        }
        if (!wCfg.isReplaceable(w.getBlockAt(x, y, z).getType())) {
            return false;
        }
        if (!tc.requireSixSolid()) {
            return true;
        }
        for (int[] d : Pos.DIRS) {
            int nx = x + d[0];
            int ny = y + d[1];
            int nz = z + d[2];
            if (ny < w.getMinHeight() || ny >= w.getMaxHeight() || !RegGuard.isAccess(w, nx, nz)) {
                return false;
            }
            Material mat = w.getBlockAt(nx, ny, nz).getType();
            if (!mat.isSolid() || !mat.isOccluding()) {
                return false;
            }
        }
        return true;
    }

    private static Material pickOre(Material forcedOre, WorldCfg wCfg, int y) {
        if (forcedOre != null) {
            return forcedOre;
        }
        List<Material> pool = wCfg.phantomOresForY(y);
        if (pool.isEmpty()) {
            return null;
        }
        return pool.size() == 1 ? pool.get(0) : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
