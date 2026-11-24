package dev.javaroot.antixray.update;

import dev.javaroot.antixray.cfg.Cfg;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.Supplier;

public final class UpdList implements Listener {

    private static final String PERM_UPDATE = "antixray.update";
    private static final long NOTIFY_DELAY_TICKS = 40L;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final Supplier<Cfg> cfgSupplier;
    private final UpdChecker checker;

    public UpdList(Plugin plugin, Supplier<Cfg> cfgSupplier, UpdChecker checker) {
        this.plugin = plugin;
        this.cfgSupplier = cfgSupplier;
        this.checker = checker;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Cfg cfg = cfgSupplier.get();
        if (cfg == null || !cfg.updateChecker.enabled() || !cfg.updateChecker.notifyPlayers()) {
            return;
        }
        if (!checker.hasUpdate()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(PERM_UPDATE)) {
            return;
        }
        List<String> lines = cfg.updateChecker.message();
        if (lines.isEmpty()) {
            return;
        }
        String current = checker.currentVersion();
        String latest = checker.latestVersion();
        String url = checker.downloadUrl();
        if (latest == null || url == null) {
            return;
        }
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            for (String line : lines) {
                String formatted = line
                        .replace("%current%", current)
                        .replace("%latest%", latest)
                        .replace("%url%", url);
                player.sendMessage(MINI.deserialize(formatted));
            }
        }, null, NOTIFY_DELAY_TICKS);
    }
}
