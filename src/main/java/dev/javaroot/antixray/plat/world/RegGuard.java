package dev.javaroot.antixray.plat.world;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class RegGuard {

    private RegGuard() {
    }

    public static boolean isAccess(World w, int bx, int bz) {
        int cx = bx >> 4;
        int cz = bz >> 4;
        return w.isChunkLoaded(cx, cz) && Bukkit.isOwnedByCurrentRegion(w, cx, cz);
    }

    public static boolean isSolidBlock(World w, int minY, int maxY, int x, int y, int z) {
        if (y < minY || y >= maxY || !isAccess(w, x, z)) {
            return false;
        }
        Block b = w.getBlockAt(x, y, z);
        Material mat = b.getType();
        return !mat.isAir() && !b.isLiquid() && mat.isSolid();
    }
}
