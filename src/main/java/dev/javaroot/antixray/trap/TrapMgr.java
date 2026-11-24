package dev.javaroot.antixray.trap;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.mdl.Vein;
import dev.javaroot.antixray.sched.Exec;
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

    private final Supplier<Cfg> cfgSupplier;
    private final Exec exec;
    private final Map<UUID, CopyOnWriteArrayList<Vein>> activeByPlayer = new ConcurrentHashMap<>();

    public TrapMgr(Supplier<Cfg> cfgSupplier, Exec exec) {
        this.cfgSupplier = cfgSupplier;
        this.exec = exec;
    }

    public void addVein(Vein vein) {
        activeByPlayer.compute(vein.playerId(), (id, existing) -> {
            CopyOnWriteArrayList<Vein> list = existing != null ? existing : new CopyOnWriteArrayList<>();
            list.add(vein);
            return list;
        });
    }

    public int activeCount(UUID playerId) {
        List<Vein> list = activeByPlayer.get(playerId);
        return list == null ? 0 : list.size();
    }

    public List<Vein> activeVeins(UUID playerId) {
        List<Vein> list = activeByPlayer.get(playerId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public boolean overlapsAny(UUID playerId, String worldName, int x, int y, int z) {
        List<Vein> list = activeByPlayer.get(playerId);
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (Vein vein : list) {
            if (vein.worldName().equals(worldName) && vein.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public Vein findVeinAt(UUID playerId, String worldName, int x, int y, int z) {
        List<Vein> list = activeByPlayer.get(playerId);
        if (list == null || list.isEmpty()) {
            return null;
        }
        for (Vein vein : list) {
            if (vein.worldName().equals(worldName) && vein.contains(x, y, z)) {
                return vein;
            }
        }
        return null;
    }

    public boolean removeVein(Vein vein, boolean restoreToClient) {
        boolean[] removed = {false};
        activeByPlayer.computeIfPresent(vein.playerId(), (id, list) -> {
            removed[0] = list.remove(vein);
            return list.isEmpty() ? null : list;
        });
        if (removed[0] && restoreToClient) {
            Player player = Bukkit.getPlayer(vein.playerId());
            if (player != null && player.isOnline()) {
                sendRestorePackets(player, vein);
            }
        }
        return removed[0];
    }

    public void clearPlayer(UUID playerId, boolean restoreToClient) {
        CopyOnWriteArrayList<Vein> list = activeByPlayer.remove(playerId);
        if (list == null || list.isEmpty() || !restoreToClient) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        for (Vein vein : list) {
            sendRestorePackets(player, vein);
        }
    }

    public void clearAll(boolean restoreToClient) {
        List<UUID> ids = new ArrayList<>(activeByPlayer.keySet());
        for (UUID id : ids) {
            clearPlayer(id, restoreToClient);
        }
    }

    public void handleBlockChange(String worldName, int x, int y, int z) {
        for (CopyOnWriteArrayList<Vein> list : activeByPlayer.values()) {
            for (Vein vein : list) {
                if (vein.worldName().equals(worldName) && vein.contains(x, y, z)) {
                    boolean removed = removeVein(vein, false);
                    if (removed) {
                        Player player = Bukkit.getPlayer(vein.playerId());
                        if (player != null && player.isOnline()) {
                            sendRestorePacketsExcluding(player, vein, x, y, z);
                        }
                    }
                }
            }
        }
    }

    public void handleChunkDesync(UUID playerId, String worldName, int chunkX, int chunkZ) {
        CopyOnWriteArrayList<Vein> list = activeByPlayer.get(playerId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Vein vein : list) {
            if (vein.worldName().equals(worldName) && vein.overlapsChunk(chunkX, chunkZ)) {
                removeVein(vein, false);
            }
        }
    }

    public void tickCleanup() {
        Cfg cfg = cfgSupplier.get();
        double maxDistSq = cfg.despawnDistance * cfg.despawnDistance;
        long nowMs = System.currentTimeMillis();

        for (Map.Entry<UUID, CopyOnWriteArrayList<Vein>> entry : activeByPlayer.entrySet()) {
            UUID playerId = entry.getKey();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                activeByPlayer.remove(playerId, entry.getValue());
                continue;
            }
            for (Vein vein : entry.getValue()) {
                if (vein.isExpired(nowMs)) {
                    removeVein(vein, true);
                }
            }
            // Read player location strictly on the entity's owning Folia scheduler thread
            exec.runEntity(player, () -> {
                if (!player.isOnline()) {
                    clearPlayer(playerId, false);
                    return;
                }
                Location loc = player.getLocation();
                String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "";
                for (Vein vein : activeVeins(playerId)) {
                    boolean wrongWorld = !vein.worldName().equals(worldName);
                    boolean tooFar = !wrongWorld
                            && vein.distanceSquared(loc.getX(), loc.getY(), loc.getZ()) > maxDistSq;
                    if (wrongWorld || tooFar) {
                        removeVein(vein, !wrongWorld);
                    }
                }
            });
        }
    }

    public static void sendSpawnPackets(Player player, Vein vein) {
        var playerMgr = PacketEvents.getAPI().getPlayerManager();
        for (Vein.BlockEntry block : vein.blocks()) {
            WrapperPlayServerBlockChange pkt = new WrapperPlayServerBlockChange(
                    new Vector3i(block.x(), block.y(), block.z()),
                    block.phantomState()
            );
            playerMgr.sendPacket(player, pkt);
        }
    }

    public static void sendRestorePackets(Player player, Vein vein) {
        var playerMgr = PacketEvents.getAPI().getPlayerManager();
        for (Vein.BlockEntry block : vein.blocks()) {
            WrapperPlayServerBlockChange pkt = new WrapperPlayServerBlockChange(
                    new Vector3i(block.x(), block.y(), block.z()),
                    block.realState()
            );
            playerMgr.sendPacket(player, pkt);
        }
    }

    private static void sendRestorePacketsExcluding(Player player, Vein vein, int skipX, int skipY, int skipZ) {
        var playerMgr = PacketEvents.getAPI().getPlayerManager();
        for (Vein.BlockEntry block : vein.blocks()) {
            if (block.x() == skipX && block.y() == skipY && block.z() == skipZ) {
                continue;
            }
            WrapperPlayServerBlockChange pkt = new WrapperPlayServerBlockChange(
                    new Vector3i(block.x(), block.y(), block.z()),
                    block.realState()
            );
            playerMgr.sendPacket(player, pkt);
        }
    }
}
