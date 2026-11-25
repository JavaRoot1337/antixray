package dev.javaroot.antixray.sess;

import dev.javaroot.antixray.db.DetRepo;
import dev.javaroot.antixray.db.mdl.Profile;
import dev.javaroot.antixray.trap.TrapMgr;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SessList implements Listener {

    private static final long SAVE_TIMEOUT_SEC = 5L;

    private final Map<UUID, Sess> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> pendingSaves = new ConcurrentHashMap<>();
    private final Set<UUID> alertsOff = ConcurrentHashMap.newKeySet();
    private final DetRepo repo;
    private final TrapMgr trapMgr;
    private final Logger log;

    public SessList(DetRepo repo, TrapMgr trapMgr, Logger log) {
        this.repo = repo;
        this.trapMgr = trapMgr;
        this.log = log;
    }

    public Sess get(UUID uuid) {
        return sessions.get(uuid);
    }

    public Sess getOrCreate(Player p) {
        Sess sess = sessions.computeIfAbsent(p.getUniqueId(), id -> {
            Sess created = new Sess(id, p.getName());
            CompletableFuture<Void> pend = pendingSaves.get(id);
            CompletableFuture<Profile> loadFut = pend != null
                    ? pend.thenCompose(ignored -> repo.loadProfile(id))
                    : repo.loadProfile(id);
            loadFut.thenAccept(created::restore);
            return created;
        });
        sess.setUser(p.getName());
        return sess;
    }

    public CompletableFuture<Void> save(Sess sess) {
        UUID id = sess.uuid();
        CompletableFuture<Void> fut = repo.saveSessState(
                id,
                sess.user(),
                sess.sus().score(),
                sess.sus().watchUntilMs(),
                sess.traps().rawHits(),
                sess.traps().lastHitMs()
        );
        pendingSaves.put(id, fut);
        fut.whenComplete((ignored, ex) -> pendingSaves.remove(id, fut));
        return fut;
    }

    public void saveAllSync() {
        List<CompletableFuture<Void>> futs = new ArrayList<>();
        for (Sess sess : sessions.values()) {
            futs.add(save(sess));
        }
        if (futs.isEmpty()) {
            return;
        }
        try {
            CompletableFuture.allOf(futs.toArray(new CompletableFuture[0]))
                    .get(SAVE_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException | ExecutionException ex) {
            if (log != null) {
                log.log(Level.WARNING, "Timed out or failed while flushing player sessions", ex);
            }
        }
    }

    public Collection<Sess> all() {
        return sessions.values();
    }

    public boolean toggleAlerts(UUID uuid) {
        if (alertsOff.remove(uuid)) {
            return true;
        }
        alertsOff.add(uuid);
        return false;
    }

    public boolean alertsOn(UUID uuid) {
        return !alertsOff.contains(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Sess sess = getOrCreate(p);
        sess.setGameMode(p.getGameMode());
        sess.markWorld(p.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        Sess sess = sessions.get(e.getPlayer().getUniqueId());
        if (sess != null) {
            sess.setGameMode(e.getNewGameMode());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        trapMgr.clearPlayer(id, false);
        Sess sess = sessions.remove(id);
        if (sess != null) {
            save(sess);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        trapMgr.clearPlayer(p.getUniqueId(), false);
        Sess sess = sessions.get(p.getUniqueId());
        if (sess != null) {
            sess.markWorld(p.getWorld().getName());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Sess sess = sessions.get(e.getPlayer().getUniqueId());
        if (sess != null && e.getRespawnLocation().getWorld() != null) {
            sess.markWorld(e.getRespawnLocation().getWorld().getName());
        }
    }
}
