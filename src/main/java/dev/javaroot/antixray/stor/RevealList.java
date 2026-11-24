package dev.javaroot.antixray.stor;

import dev.javaroot.antixray.sched.Exec;
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

    private record HeightRange(int minY, int maxYExclusive) {
    }

    private final Supplier<StorCfg> cfgSupplier;
    private final HideMap hideMap;
    private final Exec exec;
    private final Map<String, HeightRange> worldHeights = new ConcurrentHashMap<>();

    public RevealList(Supplier<StorCfg> cfgSupplier, HideMap hideMap, Exec exec) {
        this.cfgSupplier = cfgSupplier;
        this.hideMap = hideMap;
        this.exec = exec;
        for (World world : Bukkit.getWorlds()) {
            cacheWorld(world);
        }
    }

    public int worldMinY(String worldName, int fallback) {
        HeightRange range = worldHeights.get(worldName);
        return range != null ? range.minY() : fallback;
    }

    public int worldMaxYExclusive(String worldName, int fallback) {
        HeightRange range = worldHeights.get(worldName);
        return range != null ? range.maxYExclusive() : fallback;
    }

    private void cacheWorld(World world) {
        worldHeights.put(world.getName(), new HeightRange(world.getMinHeight(), world.getMaxHeight()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        cacheWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        hideMap.clearPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        hideMap.clearPlayerWorld(event.getPlayer().getUniqueId(), event.getFrom().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        StorCfg cfg = cfgSupplier.get();
        if (!cfg.enabled || !cfg.reveal.onBlockBreak()) {
            return;
        }
        Block block = event.getBlock();
        revealAround(block.getWorld(), block.getX(), block.getY(), block.getZ(), cfg.reveal.radius());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        StorCfg cfg = cfgSupplier.get();
        if (!cfg.enabled || !cfg.reveal.onBlockBurn()) {
            return;
        }
        Block block = event.getBlock();
        revealAround(block.getWorld(), block.getX(), block.getY(), block.getZ(), cfg.reveal.radius());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        StorCfg cfg = cfgSupplier.get();
        if (!cfg.enabled || !cfg.reveal.onExplosion() || event.getLocation().getWorld() == null) {
            return;
        }
        revealList(event.getLocation().getWorld(), event.blockList(), cfg.reveal.radius());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        StorCfg cfg = cfgSupplier.get();
        if (!cfg.enabled || !cfg.reveal.onExplosion()) {
            return;
        }
        revealList(event.getBlock().getWorld(), event.blockList(), cfg.reveal.radius());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        StorCfg cfg = cfgSupplier.get();
        if (!cfg.enabled || !cfg.reveal.onPiston()) {
            return;
        }
        revealPiston(event.getBlock().getWorld(), event.getBlocks(), event.getDirection(), cfg.reveal.radius());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        StorCfg cfg = cfgSupplier.get();
        if (!cfg.enabled || !cfg.reveal.onPiston()) {
            return;
        }
        revealPiston(event.getBlock().getWorld(), event.getBlocks(), event.getDirection(), cfg.reveal.radius());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        StorCfg cfg = cfgSupplier.get();
        if (!cfg.enabled || !cfg.reveal.onInteract()) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        World world = clicked.getWorld();
        int x = clicked.getX();
        int y = clicked.getY();
        int z = clicked.getZ();
        if (hideMap.isHidden(event.getPlayer().getUniqueId(), world.getName(), x, y, z)) {
            revealAt(world, x, y, z);
        }
    }

    public void revealAround(World world, int bx, int by, int bz, int radius) {
        String worldName = world.getName();
        if (radius <= 1) {
            if (!hideMap.hasHiddenNear(worldName, bx, by, bz)) {
                return;
            }
            revealAt(world, bx + 1, by, bz);
            revealAt(world, bx - 1, by, bz);
            revealAt(world, bx, by + 1, bz);
            revealAt(world, bx, by - 1, bz);
            revealAt(world, bx, by, bz + 1);
            revealAt(world, bx, by, bz - 1);
            return;
        }
        if (!hideMap.hasHiddenInArea(worldName, bx, bz, radius)) {
            return;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    revealAt(world, bx + dx, by + dy, bz + dz);
                }
            }
        }
    }

    private void revealList(World world, List<Block> blocks, int radius) {
        for (Block block : blocks) {
            revealAround(world, block.getX(), block.getY(), block.getZ(), radius);
        }
    }

    private void revealPiston(World world, List<Block> blocks, BlockFace dir, int radius) {
        for (Block block : blocks) {
            revealAround(world, block.getX(), block.getY(), block.getZ(), radius);
            Block moved = block.getRelative(dir);
            revealAround(world, moved.getX(), moved.getY(), moved.getZ(), radius);
        }
    }

    private void revealAt(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return;
        }
        Set<UUID> viewers = hideMap.popPlayersAt(world.getName(), x, y, z);
        if (viewers.isEmpty()) {
            return;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        if (Bukkit.isOwnedByCurrentRegion(world, cx, cz)) {
            sendRealBlock(world, x, y, z, viewers, true);
        } else {
            exec.runRegion(world, cx, cz, () -> sendRealBlock(world, x, y, z, viewers, true));
        }
    }

    private void sendRealBlock(World world, int x, int y, int z, Set<UUID> viewers, boolean checkDoubleChest) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return;
        }
        StorCfg cfg = cfgSupplier.get();
        Block block = world.getBlockAt(x, y, z);
        BlockData data = block.getBlockData();
        Location loc = block.getLocation();
        TileState tileState = cfg.reveal.sendTileEntityData() && block.getState(false) instanceof TileState ts
                ? ts
                : null;

        for (UUID uuid : viewers) {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer == null || !viewer.isOnline() || !viewer.getWorld().equals(world)) {
                continue;
            }
            if (tileState != null) {
                viewer.sendBlockUpdate(loc, tileState);
            } else {
                viewer.sendBlockChange(loc, data);
            }
        }

        if (checkDoubleChest && cfg.reveal.doubleChestPartner()
                && data instanceof Chest chest && chest.getType() != Chest.Type.SINGLE) {
            Block partner = doubleChestPartner(block, chest.getFacing(), chest.getType());
            if (partner != null) {
                Set<UUID> partnerViewers = hideMap.popPlayersAt(
                        world.getName(), partner.getX(), partner.getY(), partner.getZ());
                if (!partnerViewers.isEmpty()) {
                    int pcx = partner.getX() >> 4;
                    int pcz = partner.getZ() >> 4;
                    if (Bukkit.isOwnedByCurrentRegion(world, pcx, pcz)) {
                        sendRealBlock(world, partner.getX(), partner.getY(), partner.getZ(), partnerViewers, false);
                    } else {
                        exec.runRegion(world, pcx, pcz, () -> sendRealBlock(
                                world, partner.getX(), partner.getY(), partner.getZ(), partnerViewers, false));
                    }
                }
            }
        }
    }

    private static Block doubleChestPartner(Block block, BlockFace facing, Chest.Type type) {
        int sign = type == Chest.Type.LEFT ? 1 : -1;
        return switch (facing) {
            case NORTH -> block.getRelative(sign, 0, 0);
            case SOUTH -> block.getRelative(-sign, 0, 0);
            case EAST -> block.getRelative(0, 0, sign);
            case WEST -> block.getRelative(0, 0, -sign);
            default -> null;
        };
    }
}
