package dev.javaroot.antixray.cfg;

import org.bukkit.Material;

import java.util.List;
import java.util.Set;

public record WorldCfg(
        String name,
        boolean enabled,
        int maxY,
        int minY,
        Set<Material> replaceable,
        Set<Material> trackedOres,
        int deepslateY,
        List<Material> aboveOres,
        List<Material> belowOres
) {
    public boolean isInRange(int y) {
        return y >= minY && y <= maxY;
    }

    public boolean isReplaceable(Material mat) {
        return replaceable.contains(mat);
    }

    public boolean isTrackedOre(Material mat) {
        return trackedOres.contains(mat);
    }

    public List<Material> phantomOresForY(int y) {
        return y >= deepslateY ? aboveOres : belowOres;
    }
}
