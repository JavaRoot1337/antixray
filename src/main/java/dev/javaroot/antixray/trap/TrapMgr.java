package dev.javaroot.antixray.trap;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.plat.sched.Exec;
import dev.javaroot.antixray.trap.mdl.Vein;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public final class TrapMgr {

    private final Supplier<Cfg> cfgSup;
    private final Exec exec;
    private final Map<UUID, CopyOnWriteArrayList<Vein>> active = new ConcurrentHashMap<>();

    public TrapMgr(Supplier<Cfg> cfgSup, Exec exec) {
        this.cfgSup = cfgSup;
        this.exec = exec;
    }

    public void addVein(Vein vein) {
        active.compute(vein.plrId(), (id, cur) -> {
            CopyOnWriteArrayList<Vein> list = cur != null ? cur : new CopyOnWriteArrayList<>();
            list.add(vein);
            return list;
        });
    }

    public int activeCount(UUID plrId) {
        List<Vein> list = active.get(plrId);
        return list == null ? 0 : list.size();
    }

    public List<Vein> activeVeins(UUID plrId) {
        List<Vein> list = active.get(plrId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public boolean overlapsAny(UUID plrId, String world, int x, int y, int z) {
        return findVeinAt(plrId, world, x, y, z) != null;
    }

    public Vein findVeinAt(UUID plrId, String world, int x, int y, int z) {
        List<Vein> list = active.get(plrId);
        if (list == null || list.isEmpty()) {
            return null;
        }
        for (Vein v : list) {
            if (v.world().equals(world) && v.contains(x, y, z)) {
                return v;
            }
        }
        return null;
    }

    public boolean removeVein(Vein vein, boolean restore) {
        boolean[] removed = {false};
        active.computeIfPresent(vein.plrId(), (id, list) -> {
            removed[0] = list.remove(vein);
            return list.isEmpty() ? null : list;
        });
        if (removed[0] && restore) {
            Player p = Bukkit.getPlayer(vein.plrId());
            if (p != null && p.isOnline()) {
                sendRestore(p, vein);
            }
        }
        return removed[0];
    }

    public void clearPlayer(UUID plrId, boolean restore) {
        CopyOnWriteArrayList<Vein> list = active.remove(plrId);
        if (list == null || list.isEmpty() || !restore) {
            return;
        }
        Player p = Bukkit.getPlayer(plrId);
        if (p == null || !p.isOnline()) {
            return;
        }
        for (Vein v : list) {
            sendRestore(p, v);
        }
    }

    public void clearAll(boolean restore) {
        List<UUID> ids = new ArrayList<>(active.keySet());
        for (UUID id : ids) {
            clearPlayer(id, restore);
        }
    }

    public void handleBlockChange(String world, int x, int y, int z) {
        for (CopyOnWriteArrayList<Vein> list : active.values()) {
            for (Vein v : list) {
                if (v.world().equals(world) && v.contains(x, y, z) && removeVein(v, false)) {
                    Player p = Bukkit.getPlayer(v.plrId());
                    if (p != null && p.isOnline()) {
                        sendPackets(p, v, false, true, x, y, z);
                    }
                }
            }
        }
    }

    public void handleChunkDesync(UUID plrId, String world, int cx, int cz) {
        CopyOnWriteArrayList<Vein> list = active.get(plrId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Vein v : list) {
            if (v.world().equals(world) && v.overlapsChunk(cx, cz)) {
                removeVein(v, false);
            }
        }
    }

    public void tickClean() {
        double maxDist = cfgSup.get().trap().despawnDist();
        double maxDistSq = maxDist * maxDist;
        long nowMs = System.currentTimeMillis();

        for (Map.Entry<UUID, CopyOnWriteArrayList<Vein>> entry : active.entrySet()) {
            UUID plrId = entry.getKey();
            Player p = Bukkit.getPlayer(plrId);
            if (p == null || !p.isOnline()) {
                active.remove(plrId, entry.getValue());
                continue;
            }
            for (Vein v : entry.getValue()) {
                if (v.isExpired(nowMs)) {
                    removeVein(v, true);
                }
            }
            exec.runEntity(p, () -> {
                if (!p.isOnline()) {
                    clearPlayer(plrId, false);
                    return;
                }
                Location loc = p.getLocation();
                String wName = loc.getWorld() != null ? loc.getWorld().getName() : "";
                for (Vein v : activeVeins(plrId)) {
                    boolean wrongWorld = !v.world().equals(wName);
                    boolean tooFar = !wrongWorld && v.distSq(loc.getX(), loc.getY(), loc.getZ()) > maxDistSq;
                    if (wrongWorld || tooFar) {
                        removeVein(v, !wrongWorld);
                    }
                }
            });
        }
    }

    public static void sendSpawn(Player p, Vein vein) {
        sendPackets(p, vein, true, false, 0, 0, 0);
    }

    public static void sendRestore(Player p, Vein vein) {
        sendPackets(p, vein, false, false, 0, 0, 0);
    }

    private static void sendPackets(
            Player p, Vein vein, boolean phantom, boolean skipOne, int sx, int sy, int sz
    ) {
        var pm = PacketEvents.getAPI().getPlayerManager();
        for (Vein.BlockEntry b : vein.blocks()) {
            if (skipOne && b.x() == sx && b.y() == sy && b.z() == sz) {
                continue;
            }
            int stateId = (phantom ? b.phantomState() : b.realState()).getGlobalId();
            pm.sendPacket(p, new WrapperPlayServerBlockChange(new Vector3i(b.x(), b.y(), b.z()), stateId));
        }
    }
}
