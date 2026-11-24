package dev.javaroot.antixray.cfg;

import org.bukkit.Material;

import java.util.List;
import java.util.Set;

public record WorldCfg(
        String name,
        boolean enabled,
        int maxY,
        int minY,
        Set<Material> replaceableBlocks,
        Set<Material> trackedOres,
        int deepslateYThreshold,
        List<Material> aboveThresholdOres,
        List<Material> belowThresholdOres
) {
    public boolean isInRange(int y) {
        return y >= minY && y <= maxY;
    }

    public boolean isReplaceable(Material material) {
        return replaceableBlocks.contains(material);
    }

    public boolean isTrackedOre(Material material) {
        return trackedOres.contains(material);
    }

    public List<Material> phantomOresForY(int y) {
        return y >= deepslateYThreshold ? aboveThresholdOres : belowThresholdOres;
    }
}
