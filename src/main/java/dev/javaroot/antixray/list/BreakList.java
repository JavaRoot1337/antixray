package dev.javaroot.antixray.list;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.WorldCfg;
import dev.javaroot.antixray.det.SusEngine;
import dev.javaroot.antixray.mdl.Session;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.function.Supplier;

public final class BreakList implements Listener {

    public static final String PERM_BYPASS = "antixray.bypass";

    private final Supplier<Cfg> cfgSupplier;
    private final SessList sessList;
    private final SusEngine susEngine;

    public BreakList(Supplier<Cfg> cfgSupplier, SessList sessList, SusEngine susEngine) {
        this.cfgSupplier = cfgSupplier;
        this.sessList = sessList;
        this.susEngine = susEngine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Cfg cfg = cfgSupplier.get();

        if (cfg.bypassPermissionEnabled && player.hasPermission(PERM_BYPASS)) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (cfg.ignoreCreative && mode == GameMode.CREATIVE) {
            return;
        }
        if (cfg.ignoreSpectator && mode == GameMode.SPECTATOR) {
            return;
        }

        Block block = event.getBlock();
        WorldCfg worldCfg = cfg.world(block.getWorld().getName());
        if (worldCfg == null || !worldCfg.enabled()) {
            return;
        }

        Session session = sessList.getOrCreate(player);
        susEngine.onBlockBreak(player, session, block, worldCfg);
    }
}
