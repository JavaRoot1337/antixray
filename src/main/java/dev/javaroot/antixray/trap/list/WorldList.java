package dev.javaroot.antixray.trap.list;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.trap.TrapMgr;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.function.Supplier;

public final class WorldList implements Listener {

    private final Supplier<Cfg> cfgSup;
    private final TrapMgr trapMgr;

    public WorldList(Supplier<Cfg> cfgSup, TrapMgr trapMgr) {
        this.cfgSup = cfgSup;
        this.trapMgr = trapMgr;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (e.getLocation().getWorld() != null) {
            clearBlocks(e.getLocation().getWorld().getName(), e.blockList());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        clearBlocks(e.getBlock().getWorld().getName(), e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        handlePiston(e.getBlock().getWorld().getName(), e.getBlocks(), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        handlePiston(e.getBlock().getWorld().getName(), e.getBlocks(), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getWorld() == null || to.getWorld() == null) {
            return;
        }
        if (!from.getWorld().equals(to.getWorld())) {
            trapMgr.clearPlayer(e.getPlayer().getUniqueId(), false);
            return;
        }
        double maxDist = cfgSup.get().trap().despawnDist();
        if (from.distanceSquared(to) > maxDist * maxDist) {
            trapMgr.clearPlayer(e.getPlayer().getUniqueId(), true);
        }
    }

    private void clearBlocks(String wName, List<Block> blocks) {
        for (Block b : blocks) {
            trapMgr.handleBlockChange(wName, b.getX(), b.getY(), b.getZ());
        }
    }

    private void handlePiston(String wName, List<Block> blocks, BlockFace dir) {
        for (Block b : blocks) {
            trapMgr.handleBlockChange(wName, b.getX(), b.getY(), b.getZ());
            Block moved = b.getRelative(dir);
            trapMgr.handleBlockChange(wName, moved.getX(), moved.getY(), moved.getZ());
        }
    }
}
