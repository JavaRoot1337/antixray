package dev.javaroot.antixray.stor;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class OccTbl {

    private record RuleSnapshot(
            Set<StateType> occludingTypes,
            Set<StateType> globalTargetTypes,
            Set<StateType> chestTypes,
            Map<String, Set<StateType>> worldTargetOverrides,
            Map<String, Set<StateType>> worldCamouflageWhitelists
    ) {
    }

    private volatile RuleSnapshot snapshot;
    private final Map<ClientVersion, Palette> palettes = new ConcurrentHashMap<>();

    public OccTbl(StorCfg cfg) {
        this.snapshot = buildSnapshot(cfg);
    }

    public void reload(StorCfg cfg) {
        this.snapshot = buildSnapshot(cfg);
        this.palettes.clear();
    }

    public Palette palette(ClientVersion version) {
        ClientVersion key = version != null ? version : ClientVersion.getLatest();
        return palettes.computeIfAbsent(key, v -> buildPalette(v, snapshot));
    }

    private static RuleSnapshot buildSnapshot(StorCfg cfg) {
        Set<StateType> occluding = collectOccludingTypes(cfg);
        Set<StateType> globalTargets = resolveStateTypes(cfg.hiddenBlocks);
        Set<StateType> chests = Set.of(StateTypes.CHEST, StateTypes.TRAPPED_CHEST);

        Map<String, Set<StateType>> worldTargets = new HashMap<>();
        Map<String, Set<StateType>> worldCamo = new HashMap<>();

        for (Map.Entry<String, StorCfg.WorldRule> entry : cfg.configuredWorlds().entrySet()) {
            String worldName = entry.getKey();
            StorCfg.WorldRule rule = entry.getValue();
            if (rule.hasTargetOverrides()) {
                Set<StateType> specific = new HashSet<>(globalTargets);
                specific.addAll(resolveStateTypes(rule.extraHiddenBlocks()));
                specific.removeAll(resolveStateTypes(rule.ignoredBlocks()));
                worldTargets.put(worldName, Set.copyOf(specific));
            }
            if (!rule.camouflageWhitelist().isEmpty()) {
                worldCamo.put(worldName, resolveStateTypes(rule.camouflageWhitelist()));
            }
        }

        if (cfg.autoEnableUnlistedWorlds && !cfg.defaultWorld.camouflageWhitelist().isEmpty()) {
            worldCamo.putIfAbsent("*", resolveStateTypes(cfg.defaultWorld.camouflageWhitelist()));
        }

        return new RuleSnapshot(
                occluding,
                globalTargets,
                chests,
                Collections.unmodifiableMap(worldTargets),
                Collections.unmodifiableMap(worldCamo)
        );
    }

    private static Set<StateType> collectOccludingTypes(StorCfg cfg) {
        Set<StateType> set = new HashSet<>();
        if (Bukkit.getServer() != null) {
            for (Material mat : Material.values()) {
                if (mat.isLegacy() || !mat.isBlock() || !mat.isOccluding()) {
                    continue;
                }
                StateType type = resolveStateType(mat);
                if (type != null) {
                    set.add(type);
                }
            }
        } else {
            // Headless fallback for unit tests without Paper RegistryAccess
            for (StateType type : StateTypes.values()) {
                if (!type.isAir() && type.isSolid() && type.isBlocking() && !type.exceedsCube()) {
                    String name = type.getName();
                    if (!name.contains("chest") && !name.contains("shulker") && !name.contains("spawner")
                            && !name.contains("slab") && !name.contains("stairs") && !name.contains("glass")) {
                        set.add(type);
                    }
                }
            }
        }

        set.addAll(resolveStateTypes(cfg.masking.extraOccludingBlocks()));
        set.removeAll(resolveStateTypes(cfg.masking.nonOccludingOverrides()));
        return Set.copyOf(set);
    }

    private static Set<StateType> resolveStateTypes(Set<Material> materials) {
        if (materials == null || materials.isEmpty()) {
            return Set.of();
        }
        Set<StateType> set = new HashSet<>();
        for (Material mat : materials) {
            StateType type = resolveStateType(mat);
            if (type != null) {
                set.add(type);
            }
        }
        return Set.copyOf(set);
    }

    private static StateType resolveStateType(Material mat) {
        String lower = mat.name().toLowerCase(Locale.ROOT);
        StateType byKey = StateTypes.getByName("minecraft:" + lower);
        return byKey != null ? byKey : StateTypes.getByName(lower);
    }

    private static Palette buildPalette(ClientVersion version, RuleSnapshot snap) {
        // Global block state IDs in 1.13+ are contiguous; stop after an empty tail
        int maxId = 0;
        int airStreak = 0;
        for (int id = 1; id < 65536; id++) {
            WrappedBlockState state = WrappedBlockState.getByGlobalId(version, id, false);
            if (state.getType() == StateTypes.AIR) {
                airStreak++;
                if (id > 1024 && airStreak >= 512) {
                    break;
                }
            } else {
                airStreak = 0;
                maxId = id;
            }
        }

        boolean[] occluding = new boolean[maxId + 1];
        boolean[] target = new boolean[maxId + 1];
        boolean[] chest = new boolean[maxId + 1];

        Map<String, boolean[]> worldTargets = new HashMap<>();
        for (String worldName : snap.worldTargetOverrides().keySet()) {
            worldTargets.put(worldName, new boolean[maxId + 1]);
        }

        Map<String, boolean[]> worldCamo = new HashMap<>();
        for (String worldName : snap.worldCamouflageWhitelists().keySet()) {
            worldCamo.put(worldName, new boolean[maxId + 1]);
        }

        for (int id = 1; id <= maxId; id++) {
            WrappedBlockState state = WrappedBlockState.getByGlobalId(version, id, false);
            StateType type = state.getType();
            if (type == StateTypes.AIR) {
                continue;
            }
            occluding[id] = snap.occludingTypes().contains(type);
            target[id] = snap.globalTargetTypes().contains(type);
            chest[id] = snap.chestTypes().contains(type);

            for (Map.Entry<String, Set<StateType>> entry : snap.worldTargetOverrides().entrySet()) {
                worldTargets.get(entry.getKey())[id] = entry.getValue().contains(type);
            }
            for (Map.Entry<String, Set<StateType>> entry : snap.worldCamouflageWhitelists().entrySet()) {
                worldCamo.get(entry.getKey())[id] = occluding[id] && entry.getValue().contains(type);
            }
        }

        return new Palette(
                version,
                occluding,
                target,
                chest,
                Collections.unmodifiableMap(worldTargets),
                Collections.unmodifiableMap(worldCamo)
        );
    }

    public static final class Palette {

        private final ClientVersion version;
        private final boolean[] occluding;
        private final boolean[] target;
        private final boolean[] chest;
        private final Map<String, boolean[]> worldTargets;
        private final Map<String, boolean[]> worldCamouflage;
        private final Map<Material, Integer> replacementIds = new ConcurrentHashMap<>();

        private Palette(
                ClientVersion version,
                boolean[] occluding,
                boolean[] target,
                boolean[] chest,
                Map<String, boolean[]> worldTargets,
                Map<String, boolean[]> worldCamouflage
        ) {
            this.version = version;
            this.occluding = occluding;
            this.target = target;
            this.chest = chest;
            this.worldTargets = worldTargets;
            this.worldCamouflage = worldCamouflage;
        }

        public boolean isOccluding(int globalId) {
            return globalId > 0 && globalId < occluding.length && occluding[globalId];
        }

        public boolean isTarget(String worldName, int globalId) {
            if (globalId <= 0 || globalId >= target.length) {
                return false;
            }
            boolean[] override = worldName != null ? worldTargets.get(worldName) : null;
            return override != null ? override[globalId] : target[globalId];
        }

        public boolean isChest(int globalId) {
            return globalId > 0 && globalId < chest.length && chest[globalId];
        }

        public boolean isAllowedCamouflage(String worldName, int globalId) {
            if (globalId <= 0 || globalId >= occluding.length) {
                return false;
            }
            boolean[] whitelist = worldName != null ? worldCamouflage.get(worldName) : null;
            if (whitelist == null) {
                whitelist = worldCamouflage.get("*");
            }
            return whitelist != null ? whitelist[globalId] : occluding[globalId];
        }

        public int replacementId(Material material) {
            return replacementIds.computeIfAbsent(material, mat -> {
                StateType type = resolveStateType(mat);
                if (type == null) {
                    type = StateTypes.STONE;
                }
                return WrappedBlockState.getDefaultState(version, type, false).getGlobalId();
            });
        }
    }
}
