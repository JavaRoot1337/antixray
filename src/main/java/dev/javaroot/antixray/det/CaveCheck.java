package dev.javaroot.antixray.det;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.mdl.Session;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class CaveCheck {

    private CaveCheck() {
    }

    public static boolean isExposedInCave(Block oreBlock, Session session, long nowMs, Cfg cfg) {
        if (!cfg.caveFilterEnabled) {
            return false;
        }
        int r = cfg.caveCheckRadius;
        int ox = oreBlock.getX();
        int oy = oreBlock.getY();
        int oz = oreBlock.getZ();
        World world = oreBlock.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        int foreignOpen = 0;
        for (int dx = -r; dx <= r; dx++) {
            int nx = ox + dx;
            for (int dz = -r; dz <= r; dz++) {
                int nz = oz + dz;
                int chunkX = nx >> 4;
                int chunkZ = nz >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
                    continue;
                }
                for (int dy = -r; dy <= r; dy++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    int ny = oy + dy;
                    if (ny < minY || ny >= maxY) {
                        continue;
                    }
                    Block neighbor = world.getBlockAt(nx, ny, nz);
                    Material mat = neighbor.getType();
                    if (!mat.isAir() && !neighbor.isLiquid() && mat.isSolid()) {
                        continue;
                    }
                    if (session.isRecentlyBrokenByPlayer(nx, ny, nz, nowMs)) {
                        continue;
                    }
                    foreignOpen++;
                    if (foreignOpen >= cfg.caveMinForeignAirBlocks) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
