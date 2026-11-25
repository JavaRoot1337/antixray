package dev.javaroot.antixray.stor.cfg;

import dev.javaroot.antixray.cfg.MatParse;
import dev.javaroot.antixray.cfg.mig.CfgMig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class StorCfg {

    public static final String FILE_NAME = "basefinder.yml";

    public record BypassCfg(boolean perm, boolean creative, boolean spec, boolean specMod) {
    }

    public record MaskCfg(boolean skipBorder, boolean bothHalves, Set<Material> extraOcc, Set<Material> nonOcc) {
    }

    public record PktCfg(boolean chunkData, boolean blockEnt, boolean blockAct, boolean blockChg, boolean multiChg, boolean unload) {
    }

    public record RevealCfg(
            int radius, boolean startDig, boolean blockBreak, boolean explode,
            boolean piston, boolean interact, boolean burn, boolean tileData, boolean chestPartner
    ) {
    }

    public record WorldRule(
            boolean enabled, int minY, int maxY, int deepslateY, Material aboveRep, Material belowRep,
            boolean camo, Set<Material> camoWhitelist, Set<Material> extraHidden, Set<Material> ignored
    ) {
        public Material fallbackY(int y) {
            return y >= deepslateY ? aboveRep : belowRep;
        }

        public boolean hasOverrides() {
            return !extraHidden.isEmpty() || !ignored.isEmpty();
        }
    }

    private final boolean enabled;
    private final int ver;
    private final BypassCfg bypass;
    private final MaskCfg mask;
    private final PktCfg pkt;
    private final RevealCfg reveal;
    private final Set<Material> hidden;
    private final boolean autoUnlisted;
    private final WorldRule defWorld;
    private final Map<String, WorldRule> worlds;

    private StorCfg(
            boolean enabled, int ver, BypassCfg bypass, MaskCfg mask, PktCfg pkt, RevealCfg reveal,
            Set<Material> hidden, boolean autoUnlisted, WorldRule defWorld, Map<String, WorldRule> worlds
    ) {
        this.enabled = enabled;
        this.ver = ver;
        this.bypass = bypass;
        this.mask = mask;
        this.pkt = pkt;
        this.reveal = reveal;
        this.hidden = hidden;
        this.autoUnlisted = autoUnlisted;
        this.defWorld = defWorld;
        this.worlds = worlds;
    }

    public static StorCfg load(Plugin plug) {
        CfgMig.ensureUpdated(plug, FILE_NAME, Set.of("worlds"));
        return load(YamlConfiguration.loadConfiguration(new File(plug.getDataFolder(), FILE_NAME)), plug.getLogger());
    }

    public static StorCfg load(FileConfiguration raw, Logger log) {
        if (raw == null) {
            return disabledDefaults();
        }

        boolean enabled = raw.getBoolean("enabled", true);
        int ver = raw.getInt("config-version", 1);

        BypassCfg bypass = new BypassCfg(
                raw.getBoolean("bypass.permission-enabled", true),
                raw.getBoolean("bypass.ignore-creative", true),
                raw.getBoolean("bypass.ignore-spectator", false),
                raw.getBoolean("bypass.ignore-spectator-moderators", true)
        );

        Set<Material> extraOcc = MatParse.parsePatterns(
                raw.getStringList("masking.extra-occluding-blocks"), "basefinder.yml (masking.extra-occluding-blocks)", log);
        Set<Material> nonOcc = MatParse.parsePatterns(
                raw.getStringList("masking.non-occluding-overrides"), "basefinder.yml (masking.non-occluding-overrides)", log);
        MaskCfg mask = new MaskCfg(
                raw.getBoolean("masking.skip-chunk-border", true),
                raw.getBoolean("masking.require-both-double-chest-halves-buried", true),
                extraOcc,
                nonOcc
        );

        PktCfg pkt = new PktCfg(
                raw.getBoolean("packets.filter-chunk-data", true),
                raw.getBoolean("packets.block-entity-data", true),
                raw.getBoolean("packets.block-action", true),
                raw.getBoolean("packets.filter-block-change", true),
                raw.getBoolean("packets.filter-multi-block-change", true),
                raw.getBoolean("packets.clear-on-unload-chunk", true)
        );

        RevealCfg reveal = new RevealCfg(
                Math.max(1, Math.min(3, raw.getInt("reveal.radius", 1))),
                raw.getBoolean("reveal.on-start-dig", true),
                raw.getBoolean("reveal.on-block-break", true),
                raw.getBoolean("reveal.on-explosion", true),
                raw.getBoolean("reveal.on-piston", true),
                raw.getBoolean("reveal.on-interact", true),
                raw.getBoolean("reveal.on-block-burn", true),
                raw.getBoolean("reveal.send-tile-entity-data", true),
                raw.getBoolean("reveal.double-chest-partner", true)
        );

        Set<Material> hidden = raw.contains("hidden-blocks")
                ? MatParse.parsePatterns(raw.getStringList("hidden-blocks"), "basefinder.yml (hidden-blocks)", log)
                : defHidden();

        ConfigurationSection defSec = raw.getConfigurationSection("default-world");
        boolean autoUnlisted = defSec != null && defSec.getBoolean("enabled-for-unlisted-worlds", false);
        WorldRule defWorld = parseRule(defSec, "default-world", autoUnlisted, null, log);
        Map<String, WorldRule> worlds = parseWorlds(raw.getConfigurationSection("worlds"), defWorld, log);

        return new StorCfg(enabled, ver, bypass, mask, pkt, reveal, hidden, autoUnlisted, defWorld, worlds);
    }

    private static StorCfg disabledDefaults() {
        WorldRule fb = new WorldRule(false, -64, 319, 0, Material.STONE, Material.DEEPSLATE,
                true, Set.of(), Set.of(), Set.of());
        return new StorCfg(
                false, 1,
                new BypassCfg(true, true, false, true),
                new MaskCfg(true, true, Set.of(), Set.of()),
                new PktCfg(true, true, true, true, true, true),
                new RevealCfg(1, true, true, true, true, true, true, true, true),
                Set.of(), false, fb, Map.of()
        );
    }

    public boolean enabled() { return enabled; }
    public int ver() { return ver; }
    public BypassCfg bypass() { return bypass; }
    public MaskCfg mask() { return mask; }
    public PktCfg pkt() { return pkt; }
    public RevealCfg reveal() { return reveal; }
    public Set<Material> hidden() { return hidden; }
    public boolean autoUnlisted() { return autoUnlisted; }
    public WorldRule defWorld() { return defWorld; }
    public Map<String, WorldRule> worlds() { return worlds; }

    public WorldRule world(String wName) {
        if (wName == null) {
            return null;
        }
        WorldRule rule = worlds.get(wName);
        if (rule != null) {
            return rule;
        }
        return autoUnlisted ? defWorld : null;
    }

    public boolean isHiddenMat(Material mat) {
        return mat != null && hidden.contains(mat);
    }

    private static Map<String, WorldRule> parseWorlds(ConfigurationSection sec, WorldRule fb, Logger log) {
        if (sec == null) {
            return Collections.emptyMap();
        }
        Map<String, WorldRule> res = new HashMap<>();
        for (String wName : sec.getKeys(false)) {
            ConfigurationSection ws = sec.getConfigurationSection(wName);
            if (ws != null) {
                res.put(wName, parseRule(ws, "worlds." + wName, true, fb, log));
            }
        }
        return Collections.unmodifiableMap(res);
    }

    private static WorldRule parseRule(
            ConfigurationSection ws, String path, boolean defOn, WorldRule base, Logger log
    ) {
        int defMinY = base != null ? base.minY() : -64;
        int defMaxY = base != null ? base.maxY() : 319;
        int defThr = base != null ? base.deepslateY() : 0;
        Material defAbove = base != null ? base.aboveRep() : Material.STONE;
        Material defBelow = base != null ? base.belowRep() : Material.DEEPSLATE;
        boolean defCamo = base == null || base.camo();

        if (ws == null) {
            return new WorldRule(defOn, defMinY, defMaxY, defThr, defAbove, defBelow, defCamo, Set.of(), Set.of(), Set.of());
        }

        int minY = ws.getInt("min-y", defMinY);
        String ctx = "basefinder.yml (" + path;
        return new WorldRule(
                ws.getBoolean("enabled", defOn),
                minY,
                Math.max(minY + 1, ws.getInt("max-y", defMaxY)),
                ws.getInt("deepslate-y-threshold", defThr),
                MatParse.matchBlock(ws.getString("above-replacement"), defAbove, ctx + ".above-replacement)", log),
                MatParse.matchBlock(ws.getString("below-replacement"), defBelow, ctx + ".below-replacement)", log),
                ws.getBoolean("use-neighbor-camouflage", defCamo),
                MatParse.parsePatterns(ws.getStringList("camouflage-whitelist"), ctx + ".camouflage-whitelist)", log),
                MatParse.parsePatterns(ws.getStringList("extra-hidden-blocks"), ctx + ".extra-hidden-blocks)", log),
                MatParse.parsePatterns(ws.getStringList("ignored-blocks"), ctx + ".ignored-blocks)", log)
        );
    }

    private static Set<Material> defHidden() {
        EnumSet<Material> out = EnumSet.of(
                Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.ENDER_CHEST,
                Material.HOPPER, Material.DISPENSER, Material.DROPPER, Material.FURNACE,
                Material.BLAST_FURNACE, Material.SMOKER, Material.BREWING_STAND, Material.SPAWNER,
                Material.CRAFTER, Material.DECORATED_POT, Material.CHISELED_BOOKSHELF
        );
        for (Material mat : Material.values()) {
            if (!mat.isLegacy() && mat.name().endsWith("_SHULKER_BOX")) {
                out.add(mat);
            }
        }
        addOpt(out, "TRIAL_SPAWNER");
        addOpt(out, "VAULT");
        return Collections.unmodifiableSet(out);
    }

    private static void addOpt(EnumSet<Material> set, String name) {
        Material mat = Material.matchMaterial(name);
        if (mat != null) {
            set.add(mat);
        }
    }
}
