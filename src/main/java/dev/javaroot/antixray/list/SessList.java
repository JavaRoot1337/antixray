package dev.javaroot.antixray.list;

import dev.javaroot.antixray.db.LogRepo;
import dev.javaroot.antixray.mdl.Session;
import dev.javaroot.antixray.trap.TrapMgr;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class SessList implements Listener {

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> alertsDisabled = ConcurrentHashMap.newKeySet();
    private final LogRepo logRepo;
    private final TrapMgr trapMgr;

    public SessList(LogRepo logRepo, TrapMgr trapMgr) {
        this.logRepo = logRepo;
        this.trapMgr = trapMgr;
    }

    public Session get(UUID uuid) {
        return sessions.get(uuid);
    }

    public Session getOrCreate(Player player) {
        Session session = sessions.computeIfAbsent(
                player.getUniqueId(),
                id -> {
                    Session created = new Session(id, player.getName());
                    logRepo.loadProfile(id).thenAccept(profile -> created.restoreProfile(
                            profile.totalPunishments(),
                            profile.suspicionScore(),
                            profile.watchUntilMs(),
                            profile.phantomHitCount(),
                            profile.lastPhantomHitAtMs()
                    ));
                    return created;
                }
        );
        session.setUsername(player.getName());
        return session;
    }

    public CompletableFuture<Void> saveSession(Session session) {
        return logRepo.saveSessionState(
                session.uuid(),
                session.username(),
                session.suspicionScore(),
                session.watchModeUntilMs(),
                session.rawPhantomHitCount(),
                session.lastPhantomHitAtMs()
        );
    }

    public void saveAllSync() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Session session : sessions.values()) {
            futures.add(saveSession(session));
        }
        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    public Collection<Session> all() {
        return sessions.values();
    }

    public boolean toggleAlerts(UUID uuid) {
        if (alertsDisabled.remove(uuid)) {
            return true;
        }
        alertsDisabled.add(uuid);
        return false;
    }

    public boolean hasAlertsEnabled(UUID uuid) {
        return !alertsDisabled.contains(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Session session = getOrCreate(player);
        session.markWorld(player.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        trapMgr.clearPlayer(id, false);
        Session session = sessions.remove(id);
        if (session != null) {
            saveSession(session);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        trapMgr.clearPlayer(player.getUniqueId(), false);
        Session session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.markWorld(player.getWorld().getName());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && event.getRespawnLocation().getWorld() != null) {
            session.markWorld(event.getRespawnLocation().getWorld().getName());
        }
    }
}
