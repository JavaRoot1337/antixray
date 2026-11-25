package dev.javaroot.antixray.stor.mask;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import dev.javaroot.antixray.stor.cfg.StorCfg;
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

    private record RuleSnap(
            Set<StateType> occTypes,
            Set<StateType> targetTypes,
            Set<StateType> chestTypes,
            Map<String, Set<StateType>> wTargets,
            Map<String, Set<StateType>> wCamo
    ) {
    }

    private volatile RuleSnap snap;
    private final Map<ClientVersion, Palette> palettes = new ConcurrentHashMap<>();

    public OccTbl(StorCfg cfg) {
        this.snap = buildSnap(cfg);
    }

    public void reload(StorCfg cfg) {
        snap = buildSnap(cfg);
        palettes.clear();
    }

    public Palette palette(ClientVersion ver) {
        ClientVersion key = ver != null ? ver : ClientVersion.getLatest();
        return palettes.computeIfAbsent(key, v -> buildPalette(v, snap));
    }

    private static RuleSnap buildSnap(StorCfg cfg) {
        Set<StateType> occ = collectOcc(cfg);
        Set<StateType> targets = resolveTypes(cfg.hidden());
        Set<StateType> chests = Set.of(StateTypes.CHEST, StateTypes.TRAPPED_CHEST);

        Map<String, Set<StateType>> wTargets = new HashMap<>();
        Map<String, Set<StateType>> wCamo = new HashMap<>();

        for (Map.Entry<String, StorCfg.WorldRule> entry : cfg.worlds().entrySet()) {
            String wName = entry.getKey();
            StorCfg.WorldRule rule = entry.getValue();
            if (rule.hasOverrides()) {
                Set<StateType> spec = new HashSet<>(targets);
                spec.addAll(resolveTypes(rule.extraHidden()));
                spec.removeAll(resolveTypes(rule.ignored()));
                wTargets.put(wName, Set.copyOf(spec));
            }
            if (!rule.camoWhitelist().isEmpty()) {
                wCamo.put(wName, resolveTypes(rule.camoWhitelist()));
            }
        }

        if (cfg.autoUnlisted() && !cfg.defWorld().camoWhitelist().isEmpty()) {
            wCamo.putIfAbsent("*", resolveTypes(cfg.defWorld().camoWhitelist()));
        }

        return new RuleSnap(occ, targets, chests, Collections.unmodifiableMap(wTargets), Collections.unmodifiableMap(wCamo));
    }

    private static Set<StateType> collectOcc(StorCfg cfg) {
        Set<StateType> set = new HashSet<>();
        if (Bukkit.getServer() != null) {
            for (Material mat : Material.values()) {
                if (mat.isLegacy() || !mat.isBlock() || !mat.isOccluding()) {
                    continue;
                }
                StateType type = resolveType(mat);
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

        set.addAll(resolveTypes(cfg.mask().extraOcc()));
        set.removeAll(resolveTypes(cfg.mask().nonOcc()));
        return Set.copyOf(set);
    }

    private static Set<StateType> resolveTypes(Set<Material> mats) {
        if (mats == null || mats.isEmpty()) {
            return Set.of();
        }
        Set<StateType> set = new HashSet<>();
        for (Material mat : mats) {
            StateType type = resolveType(mat);
            if (type != null) {
                set.add(type);
            }
        }
        return Set.copyOf(set);
    }

    private static StateType resolveType(Material mat) {
        String lower = mat.name().toLowerCase(Locale.ROOT);
        StateType byKey = StateTypes.getByName("minecraft:" + lower);
        return byKey != null ? byKey : StateTypes.getByName(lower);
    }

    private static Palette buildPalette(ClientVersion ver, RuleSnap snap) {
        // Global block state IDs in 1.13+ are contiguous; stop after an empty tail
        int maxId = 0;
        int airStreak = 0;
        for (int id = 1; id < 65536; id++) {
            if (WrappedBlockState.getByGlobalId(ver, id, false).getType() == StateTypes.AIR) {
                airStreak++;
                if (id > 1024 && airStreak >= 512) {
                    break;
                }
            } else {
                airStreak = 0;
                maxId = id;
            }
        }

        boolean[] occ = new boolean[maxId + 1];
        boolean[] target = new boolean[maxId + 1];
        boolean[] chest = new boolean[maxId + 1];

        Map<String, boolean[]> wTargets = new HashMap<>();
        for (String wName : snap.wTargets().keySet()) {
            wTargets.put(wName, new boolean[maxId + 1]);
        }

        Map<String, boolean[]> wCamo = new HashMap<>();
        for (String wName : snap.wCamo().keySet()) {
            wCamo.put(wName, new boolean[maxId + 1]);
        }

        for (int id = 1; id <= maxId; id++) {
            StateType type = WrappedBlockState.getByGlobalId(ver, id, false).getType();
            if (type == StateTypes.AIR) {
                continue;
            }
            occ[id] = snap.occTypes().contains(type);
            target[id] = snap.targetTypes().contains(type);
            chest[id] = snap.chestTypes().contains(type);

            for (Map.Entry<String, Set<StateType>> entry : snap.wTargets().entrySet()) {
                wTargets.get(entry.getKey())[id] = entry.getValue().contains(type);
            }
            for (Map.Entry<String, Set<StateType>> entry : snap.wCamo().entrySet()) {
                wCamo.get(entry.getKey())[id] = occ[id] && entry.getValue().contains(type);
            }
        }

        return new Palette(ver, occ, target, chest, Collections.unmodifiableMap(wTargets), Collections.unmodifiableMap(wCamo));
    }

    public static final class Palette {

        private final ClientVersion ver;
        private final boolean[] occ;
        private final boolean[] target;
        private final boolean[] chest;
        private final Map<String, boolean[]> wTargets;
        private final Map<String, boolean[]> wCamo;
        private final Map<Material, Integer> repIds = new ConcurrentHashMap<>();

        private Palette(
                ClientVersion ver, boolean[] occ, boolean[] target, boolean[] chest,
                Map<String, boolean[]> wTargets, Map<String, boolean[]> wCamo
        ) {
            this.ver = ver;
            this.occ = occ;
            this.target = target;
            this.chest = chest;
            this.wTargets = wTargets;
            this.wCamo = wCamo;
        }

        public boolean isOcc(int id) {
            return id > 0 && id < occ.length && occ[id];
        }

        public boolean isTarget(String wName, int id) {
            if (id <= 0 || id >= target.length) {
                return false;
            }
            boolean[] ovr = wName != null ? wTargets.get(wName) : null;
            return ovr != null ? ovr[id] : target[id];
        }

        public boolean isChest(int id) {
            return id > 0 && id < chest.length && chest[id];
        }

        public boolean isCamo(String wName, int id) {
            if (id <= 0 || id >= occ.length) {
                return false;
            }
            boolean[] wl = wName != null ? wCamo.get(wName) : null;
            if (wl == null) {
                wl = wCamo.get("*");
            }
            return wl != null ? wl[id] : occ[id];
        }

        public int repId(Material mat) {
            return repIds.computeIfAbsent(mat, m -> {
                StateType type = resolveType(m);
                if (type == null) {
                    type = StateTypes.STONE;
                }
                return WrappedBlockState.getDefaultState(ver, type, false).getGlobalId();
            });
        }
    }
}
