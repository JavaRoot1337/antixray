package dev.javaroot.antixray.stor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class StorCfg {

    public static final String FILE_NAME = "basefinder.yml";

    public record BypassCfg(
            boolean permissionEnabled,
            boolean ignoreCreative,
            boolean ignoreSpectator,
            boolean ignoreSpectatorModerators
    ) {
    }

    public record MaskCfg(
            boolean skipChunkBorder,
            boolean requireBothDoubleChestHalvesBuried,
            Set<Material> extraOccludingBlocks,
            Set<Material> nonOccludingOverrides
    ) {
    }

    public record PacketCfg(
            boolean filterChunkData,
            boolean blockEntityData,
            boolean blockAction,
            boolean filterBlockChange,
            boolean filterMultiBlockChange,
            boolean clearOnUnloadChunk
    ) {
    }

    public record RevealCfg(
            int radius,
            boolean onStartDig,
            boolean onBlockBreak,
            boolean onExplosion,
            boolean onPiston,
            boolean onInteract,
            boolean onBlockBurn,
            boolean sendTileEntityData,
            boolean doubleChestPartner
    ) {
    }

    public record WorldRule(
            boolean enabled,
            int minY,
            int maxY,
            int deepslateYThreshold,
            Material aboveReplacement,
            Material belowReplacement,
            boolean useNeighborCamouflage,
            Set<Material> camouflageWhitelist,
            Set<Material> extraHiddenBlocks,
            Set<Material> ignoredBlocks
    ) {
        public Material fallbackForY(int y) {
            return y >= deepslateYThreshold ? aboveReplacement : belowReplacement;
        }

        public boolean hasTargetOverrides() {
            return !extraHiddenBlocks.isEmpty() || !ignoredBlocks.isEmpty();
        }
    }

    public final boolean enabled;
    public final int configVersion;
    public final BypassCfg bypass;
    public final MaskCfg masking;
    public final PacketCfg packets;
    public final RevealCfg reveal;
    public final Set<Material> hiddenBlocks;
    public final boolean autoEnableUnlistedWorlds;
    public final WorldRule defaultWorld;
    private final Map<String, WorldRule> worlds;

    private StorCfg(
            boolean enabled,
            int configVersion,
            BypassCfg bypass,
            MaskCfg masking,
            PacketCfg packets,
            RevealCfg reveal,
            Set<Material> hiddenBlocks,
            boolean autoEnableUnlistedWorlds,
            WorldRule defaultWorld,
            Map<String, WorldRule> worlds
    ) {
        this.enabled = enabled;
        this.configVersion = configVersion;
        this.bypass = bypass;
        this.masking = masking;
        this.packets = packets;
        this.reveal = reveal;
        this.hiddenBlocks = hiddenBlocks;
        this.autoEnableUnlistedWorlds = autoEnableUnlistedWorlds;
        this.defaultWorld = defaultWorld;
        this.worlds = worlds;
    }

    public static StorCfg load(Plugin plugin) {
        dev.javaroot.antixray.cfg.CfgMig.ensureUpdated(plugin, FILE_NAME, Set.of("worlds"));
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return load(yaml, plugin.getLogger());
    }

    public static StorCfg load(FileConfiguration raw, Logger logger) {
        if (raw == null) {
            return disabledDefaults();
        }

        boolean enabled = raw.getBoolean("enabled", true);
        int configVersion = raw.getInt("config-version", 1);

        BypassCfg bypass = new BypassCfg(
                raw.getBoolean("bypass.permission-enabled", true),
                raw.getBoolean("bypass.ignore-creative", true),
                raw.getBoolean("bypass.ignore-spectator", false),
                raw.getBoolean("bypass.ignore-spectator-moderators", true)
        );

        Set<Material> extraOccluding = parseMaterials(
                raw.getStringList("masking.extra-occluding-blocks"), "masking.extra-occluding-blocks", logger);
        Set<Material> nonOccluding = parseMaterials(
                raw.getStringList("masking.non-occluding-overrides"), "masking.non-occluding-overrides", logger);
        MaskCfg masking = new MaskCfg(
                raw.getBoolean("masking.skip-chunk-border", true),
                raw.getBoolean("masking.require-both-double-chest-halves-buried", true),
                extraOccluding,
                nonOccluding
        );

        PacketCfg packets = new PacketCfg(
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

        Set<Material> hiddenBlocks = raw.contains("hidden-blocks")
                ? parseMaterials(raw.getStringList("hidden-blocks"), "hidden-blocks", logger)
                : defaultHiddenBlocks();

        ConfigurationSection defSec = raw.getConfigurationSection("default-world");
        boolean autoEnableUnlisted = defSec != null && defSec.getBoolean("enabled-for-unlisted-worlds", false);
        WorldRule defaultWorld = parseWorldRule(defSec, "default-world", autoEnableUnlisted, null, logger);
        Map<String, WorldRule> worlds = parseWorlds(raw.getConfigurationSection("worlds"), defaultWorld, logger);

        return new StorCfg(
                enabled,
                configVersion,
                bypass,
                masking,
                packets,
                reveal,
                hiddenBlocks,
                autoEnableUnlisted,
                defaultWorld,
                worlds
        );
    }

    private static StorCfg disabledDefaults() {
        WorldRule fallback = new WorldRule(false, -64, 319, 0, Material.STONE, Material.DEEPSLATE,
                true, Set.of(), Set.of(), Set.of());
        return new StorCfg(
                false,
                1,
                new BypassCfg(true, true, false, true),
                new MaskCfg(true, true, Set.of(), Set.of()),
                new PacketCfg(true, true, true, true, true, true),
                new RevealCfg(1, true, true, true, true, true, true, true, true),
                Set.of(),
                false,
                fallback,
                Map.of()
        );
    }

    public WorldRule world(String worldName) {
        if (worldName == null) {
            return null;
        }
        WorldRule specific = worlds.get(worldName);
        if (specific != null) {
            return specific;
        }
        return autoEnableUnlistedWorlds ? defaultWorld : null;
    }

    public Map<String, WorldRule> configuredWorlds() {
        return worlds;
    }

    public boolean isHiddenMaterial(Material material) {
        return material != null && hiddenBlocks.contains(material);
    }

    private static Map<String, WorldRule> parseWorlds(
            ConfigurationSection section,
            WorldRule fallback,
            Logger logger
    ) {
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, WorldRule> result = new HashMap<>();
        for (String worldName : section.getKeys(false)) {
            ConfigurationSection ws = section.getConfigurationSection(worldName);
            if (ws == null) {
                continue;
            }
            result.put(worldName, parseWorldRule(ws, "worlds." + worldName, true, fallback, logger));
        }
        return Collections.unmodifiableMap(result);
    }

    private static WorldRule parseWorldRule(
            ConfigurationSection ws,
            String path,
            boolean defaultEnabled,
            WorldRule base,
            Logger logger
    ) {
        int defMinY = base != null ? base.minY() : -64;
        int defMaxY = base != null ? base.maxY() : 319;
        int defThreshold = base != null ? base.deepslateYThreshold() : 0;
        Material defAbove = base != null ? base.aboveReplacement() : Material.STONE;
        Material defBelow = base != null ? base.belowReplacement() : Material.DEEPSLATE;
        boolean defCamo = base == null || base.useNeighborCamouflage();

        if (ws == null) {
            return new WorldRule(defaultEnabled, defMinY, defMaxY, defThreshold, defAbove, defBelow,
                    defCamo, Set.of(), Set.of(), Set.of());
        }

        boolean enabled = ws.getBoolean("enabled", defaultEnabled);
        int minY = ws.getInt("min-y", defMinY);
        int maxY = Math.max(minY + 1, ws.getInt("max-y", defMaxY));
        int threshold = ws.getInt("deepslate-y-threshold", defThreshold);
        Material above = matchReplacement(ws.getString("above-replacement"), defAbove, path, logger);
        Material below = matchReplacement(ws.getString("below-replacement"), defBelow, path, logger);
        boolean camouflage = ws.getBoolean("use-neighbor-camouflage", defCamo);
        Set<Material> whitelist = parseMaterials(
                ws.getStringList("camouflage-whitelist"), path + ".camouflage-whitelist", logger);
        Set<Material> extraHidden = parseMaterials(
                ws.getStringList("extra-hidden-blocks"), path + ".extra-hidden-blocks", logger);
        Set<Material> ignored = parseMaterials(
                ws.getStringList("ignored-blocks"), path + ".ignored-blocks", logger);

        return new WorldRule(enabled, minY, maxY, threshold, above, below, camouflage, whitelist, extraHidden, ignored);
    }

    private static Material matchReplacement(String raw, Material fallback, String path, Logger logger) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material mat = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (mat == null || !isBlockMaterial(mat)) {
            if (logger != null) {
                logger.warning("Unknown replacement block '" + raw + "' in basefinder.yml (" + path + ")");
            }
            return fallback;
        }
        return mat;
    }

    private static Set<Material> parseMaterials(List<String> entries, String path, Logger logger) {
        if (entries == null || entries.isEmpty()) {
            return Set.of();
        }
        EnumSet<Material> out = EnumSet.noneOf(Material.class);
        for (String raw : entries) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String token = raw.trim().toUpperCase(Locale.ROOT);
            if (token.startsWith("*_")) {
                String suffix = token.substring(1);
                String exact = token.substring(2);
                for (Material mat : Material.values()) {
                    String name = mat.name();
                    if (!mat.isLegacy() && isBlockMaterial(mat) && (name.endsWith(suffix) || name.equals(exact))) {
                        out.add(mat);
                    }
                }
                continue;
            }
            Material mat = Material.matchMaterial(token);
            if (mat != null && isBlockMaterial(mat)) {
                out.add(mat);
            } else if (logger != null) {
                logger.warning("Unknown block '" + raw + "' in basefinder.yml (" + path + ")");
            }
        }
        return out.isEmpty() ? Set.of() : Collections.unmodifiableSet(out);
    }

    private static boolean isBlockMaterial(Material mat) {
        // Material#isBlock touches Paper RegistryAccess, which is absent in headless JUnit tests
        return Bukkit.getServer() == null || mat.isBlock();
    }

    private static Set<Material> defaultHiddenBlocks() {
        EnumSet<Material> out = EnumSet.of(
                Material.CHEST,
                Material.TRAPPED_CHEST,
                Material.BARREL,
                Material.ENDER_CHEST,
                Material.HOPPER,
                Material.DISPENSER,
                Material.DROPPER,
                Material.FURNACE,
                Material.BLAST_FURNACE,
                Material.SMOKER,
                Material.BREWING_STAND,
                Material.SPAWNER,
                Material.CRAFTER,
                Material.DECORATED_POT,
                Material.CHISELED_BOOKSHELF
        );
        for (Material mat : Material.values()) {
            if (!mat.isLegacy() && mat.name().endsWith("_SHULKER_BOX")) {
                out.add(mat);
            }
        }
        addOptional(out, "TRIAL_SPAWNER");
        addOptional(out, "VAULT");
        return Collections.unmodifiableSet(out);
    }

    private static void addOptional(EnumSet<Material> set, String name) {
        Material mat = Material.matchMaterial(name);
        if (mat != null) {
            set.add(mat);
        }
    }
}
