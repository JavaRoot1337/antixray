package dev.javaroot.antixray.upd;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.sec.UpdCfg;
import dev.javaroot.antixray.plat.sched.Exec;
import dev.javaroot.antixray.plat.txt.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class UpdList implements Listener {

    private static final String PERM_UPDATE = "antixray.update";
    private static final long DELAY_TICKS = 40L;

    private final Supplier<Cfg> cfgSup;
    private final Supplier<Msg> msgSup;
    private final UpdSvc updSvc;
    private final Exec exec;

    public UpdList(Supplier<Cfg> cfgSup, Supplier<Msg> msgSup, UpdSvc updSvc, Exec exec) {
        this.cfgSup = cfgSup;
        this.msgSup = msgSup;
        this.updSvc = updSvc;
        this.exec = exec;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Cfg cfg = cfgSup.get();
        if (cfg == null) {
            return;
        }
        UpdCfg uc = cfg.upd();
        if (!uc.enabled() || !uc.notifyPlayers() || !updSvc.hasUpd()) {
            return;
        }
        Player p = e.getPlayer();
        if (!p.hasPermission(PERM_UPDATE)) {
            return;
        }
        List<String> lines = uc.message();
        String latest = updSvc.latestVer();
        String url = updSvc.dlUrl();
        if (lines.isEmpty() || latest == null || url == null) {
            return;
        }

        Map<String, String> vars = Map.of(
                "%current%", updSvc.curVer(),
                "%latest%", latest,
                "%url%", url
        );
        exec.runEntityDelayed(p, () -> {
            if (p.isOnline()) {
                msgSup.get().sendLines(p, lines, vars);
            }
        }, DELAY_TICKS);
    }
}
