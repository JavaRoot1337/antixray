package dev.javaroot.antixray.det.list;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.WorldCfg;
import dev.javaroot.antixray.cfg.sec.GenCfg;
import dev.javaroot.antixray.det.SusEng;
import dev.javaroot.antixray.sess.Sess;
import dev.javaroot.antixray.sess.SessList;
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

    private final Supplier<Cfg> cfgSup;
    private final SessList sessList;
    private final SusEng susEng;

    public BreakList(Supplier<Cfg> cfgSup, SessList sessList, SusEng susEng) {
        this.cfgSup = cfgSup;
        this.sessList = sessList;
        this.susEng = susEng;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Cfg cfg = cfgSup.get();
        GenCfg gen = cfg.gen();

        if (gen.bypassPerm() && p.hasPermission(PERM_BYPASS)) {
            return;
        }
        GameMode mode = p.getGameMode();
        if (gen.ignoreCreative() && mode == GameMode.CREATIVE) {
            return;
        }
        if (gen.ignoreSpectator() && mode == GameMode.SPECTATOR) {
            return;
        }

        Block b = e.getBlock();
        WorldCfg wCfg = cfg.world(b.getWorld().getName());
        if (wCfg == null || !wCfg.enabled()) {
            return;
        }

        Sess sess = sessList.getOrCreate(p);
        sess.setGameMode(mode);
        susEng.onBreak(p, sess, b, wCfg);
    }
}
