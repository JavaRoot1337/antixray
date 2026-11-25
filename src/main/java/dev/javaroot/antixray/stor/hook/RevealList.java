package dev.javaroot.antixray.stor.hook;

import dev.javaroot.antixray.plat.sched.Exec;
import dev.javaroot.antixray.plat.world.Pos;
import dev.javaroot.antixray.stor.cfg.StorCfg;
import dev.javaroot.antixray.stor.mask.ChestHelper;
import dev.javaroot.antixray.stor.mask.HideMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class RevealList implements Listener {

    private record HeightRange(int minY, int maxYExcl) {
    }

    private final Supplier<StorCfg> cfgSup;
    private final HideMap hideMap;
    private final Exec exec;
    private final Map<String, HeightRange> wHeights = new ConcurrentHashMap<>();

    public RevealList(Supplier<StorCfg> cfgSup, HideMap hideMap, Exec exec) {
        this.cfgSup = cfgSup;
        this.hideMap = hideMap;
        this.exec = exec;
        for (World w : Bukkit.getWorlds()) {
            cacheWorld(w);
        }
    }

    public int minY(String wName, int fb) {
        HeightRange r = wHeights.get(wName);
        return r != null ? r.minY() : fb;
    }

    public int maxYExcl(String wName, int fb) {
        HeightRange r = wHeights.get(wName);
        return r != null ? r.maxYExcl() : fb;
    }

    private void cacheWorld(World w) {
        wHeights.put(w.getName(), new HeightRange(w.getMinHeight(), w.getMaxHeight()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent e) {
        cacheWorld(e.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        hideMap.clearPlayer(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        hideMap.clearWorld(e.getPlayer().getUniqueId(), e.getFrom().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        StorCfg cfg = cfgSup.get();
        if (cfg.enabled() && cfg.reveal().blockBreak()) {
            Block b = e.getBlock();
            revealAround(b.getWorld(), b.getX(), b.getY(), b.getZ(), cfg.reveal().radius());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        StorCfg cfg = cfgSup.get();
        if (cfg.enabled() && cfg.reveal().burn()) {
            Block b = e.getBlock();
            revealAround(b.getWorld(), b.getX(), b.getY(), b.getZ(), cfg.reveal().radius());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        StorCfg cfg = cfgSup.get();
        if (cfg.enabled() && cfg.reveal().explode() && e.getLocation().getWorld() != null) {
            revealList(e.getLocation().getWorld(), e.blockList(), cfg.reveal().radius());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        StorCfg cfg = cfgSup.get();
        if (cfg.enabled() && cfg.reveal().explode()) {
            revealList(e.getBlock().getWorld(), e.blockList(), cfg.reveal().radius());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        StorCfg cfg = cfgSup.get();
        if (cfg.enabled() && cfg.reveal().piston()) {
            revealPiston(e.getBlock().getWorld(), e.getBlocks(), e.getDirection(), cfg.reveal().radius());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        StorCfg cfg = cfgSup.get();
        if (cfg.enabled() && cfg.reveal().piston()) {
            revealPiston(e.getBlock().getWorld(), e.getBlocks(), e.getDirection(), cfg.reveal().radius());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        StorCfg cfg = cfgSup.get();
        if (!cfg.enabled() || !cfg.reveal().interact()) {
            return;
        }
        Action act = e.getAction();
        if (act != Action.LEFT_CLICK_BLOCK && act != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = e.getClickedBlock();
        if (clicked == null) {
            return;
        }
        World w = clicked.getWorld();
        if (hideMap.isHidden(e.getPlayer().getUniqueId(), w.getName(), clicked.getX(), clicked.getY(), clicked.getZ())) {
            revealAt(w, clicked.getX(), clicked.getY(), clicked.getZ());
        }
    }

    public void revealAround(World w, int bx, int by, int bz, int radius) {
        String wName = w.getName();
        if (radius <= 1) {
            if (!hideMap.hasNear(wName, bx, by, bz)) {
                return;
            }
            for (int[] off : Pos.DIRS) {
                revealAt(w, bx + off[0], by + off[1], bz + off[2]);
            }
            return;
        }
        if (!hideMap.hasInArea(wName, bx, bz, radius)) {
            return;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        revealAt(w, bx + dx, by + dy, bz + dz);
                    }
                }
            }
        }
    }

    private void revealList(World w, List<Block> blocks, int radius) {
        for (Block b : blocks) {
            revealAround(w, b.getX(), b.getY(), b.getZ(), radius);
        }
    }

    private void revealPiston(World w, List<Block> blocks, BlockFace dir, int radius) {
        for (Block b : blocks) {
            revealAround(w, b.getX(), b.getY(), b.getZ(), radius);
            Block moved = b.getRelative(dir);
            revealAround(w, moved.getX(), moved.getY(), moved.getZ(), radius);
        }
    }

    private void revealAt(World w, int x, int y, int z) {
        if (y < w.getMinHeight() || y >= w.getMaxHeight()) {
            return;
        }
        Set<UUID> viewers = hideMap.popAt(w.getName(), x, y, z);
        if (viewers.isEmpty()) {
            return;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        if (Bukkit.isOwnedByCurrentRegion(w, cx, cz)) {
            sendReal(w, x, y, z, viewers, true);
        } else {
            exec.runRegion(w, cx, cz, () -> sendReal(w, x, y, z, viewers, true));
        }
    }

    private void sendReal(World w, int x, int y, int z, Set<UUID> viewers, boolean checkPartner) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
            return;
        }
        StorCfg cfg = cfgSup.get();
        Block b = w.getBlockAt(x, y, z);
        BlockData data = b.getBlockData();
        Location loc = b.getLocation();
        TileState ts = cfg.reveal().tileData() && b.getState(false) instanceof TileState state ? state : null;

        for (UUID id : viewers) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer == null || !viewer.isOnline() || !viewer.getWorld().equals(w)) {
                continue;
            }
            if (ts != null) {
                viewer.sendBlockUpdate(loc, ts);
            } else {
                viewer.sendBlockChange(loc, data);
            }
        }

        if (checkPartner && cfg.reveal().chestPartner()
                && data instanceof Chest chest && chest.getType() != Chest.Type.SINGLE) {
            int[] off = ChestHelper.offset(chest.getFacing(), chest.getType());
            if (off == null) {
                return;
            }
            Block partner = b.getRelative(off[0], 0, off[1]);
            Set<UUID> pViewers = hideMap.popAt(w.getName(), partner.getX(), partner.getY(), partner.getZ());
            if (!pViewers.isEmpty()) {
                int pcx = partner.getX() >> 4;
                int pcz = partner.getZ() >> 4;
                if (Bukkit.isOwnedByCurrentRegion(w, pcx, pcz)) {
                    sendReal(w, partner.getX(), partner.getY(), partner.getZ(), pViewers, false);
                } else {
                    exec.runRegion(w, pcx, pcz, () -> sendReal(w, partner.getX(), partner.getY(), partner.getZ(), pViewers, false));
                }
            }
        }
    }
}
