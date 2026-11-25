package dev.javaroot.antixray.pun;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.sec.PunCfg;
import dev.javaroot.antixray.db.DetRepo;
import dev.javaroot.antixray.plat.sched.Exec;
import dev.javaroot.antixray.plat.txt.Hex;
import dev.javaroot.antixray.plat.txt.Msg;
import dev.javaroot.antixray.sess.Sess;
import dev.javaroot.antixray.sess.SessList;
import dev.javaroot.antixray.trap.TrapMgr;
import dev.javaroot.antixray.trap.mdl.Vein;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class PunSvc {

    public static final String PERM_ALERTS = "antixray.alerts";

    private final Supplier<Cfg> cfgSup;
    private final Supplier<Msg> msgSup;
    private final SessList sessList;
    private final TrapMgr trapMgr;
    private final DetRepo repo;
    private final Exec exec;
    private final Logger log;

    public PunSvc(
            Supplier<Cfg> cfgSup,
            Supplier<Msg> msgSup,
            SessList sessList,
            TrapMgr trapMgr,
            DetRepo repo,
            Exec exec,
            Logger log
    ) {
        this.cfgSup = cfgSup;
        this.msgSup = msgSup;
        this.sessList = sessList;
        this.trapMgr = trapMgr;
        this.repo = repo;
        this.exec = exec;
        this.log = log;
    }

    public void onTrapHit(Player p, Vein vein, int hx, int hy, int hz) {
        if (!p.isOnline()) {
            return;
        }
        Cfg cfg = cfgSup.get();
        PunCfg pc = cfg.pun();
        Msg msg = msgSup.get();
        Sess sess = sessList.getOrCreate(p);
        long nowMs = System.currentTimeMillis();

        int cnt = sess.traps().incHit(nowMs, pc.resetAfterMin());
        int maxCnt = pc.triggerCount();
        double sus = sess.sus().score();
        double dist = Math.sqrt(vein.distSq(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ()));

        repo.recordDet(p.getUniqueId(), p.getName(), vein.world(), hx, hy, hz, vein.ore().name(), sus, cnt, nowMs);

        double thresh = cfg.sus().triggerThreshold();
        Map<String, String> vars = new HashMap<>();
        vars.put("%player%", p.getName());
        vars.put("%uuid%", p.getUniqueId().toString());
        vars.put("%count%", String.valueOf(cnt));
        vars.put("%max_count%", String.valueOf(maxCnt));
        vars.put("%ore%", msg.oreName(vein.ore()));
        vars.put("%world%", vein.world());
        vars.put("%x%", String.valueOf(hx));
        vars.put("%y%", String.valueOf(hy));
        vars.put("%z%", String.valueOf(hz));
        vars.put("%suspicion%", String.format(Locale.ROOT, "%.1f", sus));
        vars.put("%distance%", String.format(Locale.ROOT, "%.1f", dist));
        vars.put("%threshold%", String.format(Locale.ROOT, "%.1f", thresh));
        vars.put("%watch_mode%", sess.sus().isWatch(nowMs, thresh) ? "ON" : "OFF");
        vars.put("%active_trap%", String.valueOf(trapMgr.activeCount(p.getUniqueId())));
        vars.put("%total_punishments%", String.valueOf(sess.traps().totalPunishments()));

        if (pc.logConsole() && !msg.consoleHit().isEmpty()) {
            log.info(msg.plain(msg.consoleHit(), vars));
        }

        if (cnt >= maxCnt) {
            execPunish(p, vein, hx, hy, hz, cnt, nowMs, pc, msg, sess, vars);
            return;
        }

        sess.traps().forceCooldown(nowMs, cfg.trap().fastRespawnSec());

        boolean alert = (cnt == 1 && pc.alertFirstHit())
                || (cnt == 2 && pc.alertSecondHit())
                || cnt > 2;
        if (alert) {
            broadcast(msg, msg.alertHit(), vars);
        }
    }

    private void execPunish(
            Player p, Vein vein, int hx, int hy, int hz, int cnt, long nowMs,
            PunCfg pc, Msg msg, Sess sess, Map<String, String> vars
    ) {
        List<String> cmds = new ArrayList<>(pc.cmds().size());
        for (String tpl : pc.cmds()) {
            cmds.add(Hex.applyVars(tpl, vars));
        }
        if (!cmds.isEmpty()) {
            exec.runGlobal(() -> {
                for (String cmd : cmds) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            });
        }

        sess.traps().incTotalPunishments();
        vars.put("%total_punishments%", String.valueOf(sess.traps().totalPunishments()));

        repo.recordPun(p.getUniqueId(), p.getName(), vein.world(), hx, hy, hz, cnt, String.join("; ", cmds), nowMs);

        if (pc.logConsole() && !msg.consolePunished().isEmpty()) {
            log.info(msg.plain(msg.consolePunished(), vars));
        }
        if (pc.resetOnPunish()) {
            sess.traps().resetHits();
        }
        if (pc.alertPunish()) {
            broadcast(msg, msg.alertPunished(), vars);
        }
    }

    private void broadcast(Msg msg, List<String> lines, Map<String, String> vars) {
        if (lines.isEmpty()) {
            return;
        }
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!sessList.alertsOn(staff.getUniqueId())) {
                continue;
            }
            exec.runEntity(staff, () -> {
                if (staff.isOnline() && staff.hasPermission(PERM_ALERTS)) {
                    msg.sendLines(staff, lines, vars);
                }
            });
        }
    }
}
