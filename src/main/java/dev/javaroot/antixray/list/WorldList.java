package dev.javaroot.antixray.list;

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

    private final Supplier<Cfg> cfgSupplier;
    private final TrapMgr trapMgr;

    public WorldList(Supplier<Cfg> cfgSupplier, TrapMgr trapMgr) {
        this.cfgSupplier = cfgSupplier;
        this.trapMgr = trapMgr;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getLocation().getWorld() != null) {
            clearBlocks(event.getLocation().getWorld().getName(), event.blockList());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        clearBlocks(event.getBlock().getWorld().getName(), event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        handlePiston(event.getBlock().getWorld().getName(), event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        handlePiston(event.getBlock().getWorld().getName(), event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() == null || to.getWorld() == null) {
            return;
        }
        if (!from.getWorld().equals(to.getWorld())) {
            trapMgr.clearPlayer(event.getPlayer().getUniqueId(), false);
            return;
        }
        double maxDist = cfgSupplier.get().despawnDistance;
        if (from.distanceSquared(to) > maxDist * maxDist) {
            trapMgr.clearPlayer(event.getPlayer().getUniqueId(), true);
        }
    }

    private void clearBlocks(String worldName, List<Block> blocks) {
        for (Block block : blocks) {
            trapMgr.handleBlockChange(worldName, block.getX(), block.getY(), block.getZ());
        }
    }

    private void handlePiston(String worldName, List<Block> blocks, BlockFace dir) {
        for (Block block : blocks) {
            trapMgr.handleBlockChange(worldName, block.getX(), block.getY(), block.getZ());
            Block moved = block.getRelative(dir);
            trapMgr.handleBlockChange(worldName, moved.getX(), moved.getY(), moved.getZ());
        }
    }
}
