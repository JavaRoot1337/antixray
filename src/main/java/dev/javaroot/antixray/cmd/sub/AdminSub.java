package dev.javaroot.antixray.cmd.sub;

import dev.javaroot.antixray.plat.sched.Exec;
import dev.javaroot.antixray.plat.txt.Msg;
import dev.javaroot.antixray.pun.PunSvc;
import dev.javaroot.antixray.sess.Sess;
import dev.javaroot.antixray.sess.SessList;
import dev.javaroot.antixray.db.DetRepo;
import dev.javaroot.antixray.trap.TrapMgr;
import dev.javaroot.antixray.upd.UpdSvc;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;
import java.util.function.Supplier;

public final class AdminSub {

    public static final String PERM_ADMIN = "antixray.admin";
    public static final String PERM_UPDATE = "antixray.update";

    private final Supplier<Msg> msgSup;
    private final Runnable reloadAct;
    private final SessList sessList;
    private final TrapMgr trapMgr;
    private final DetRepo repo;
    private final Exec exec;
    private final UpdSvc updSvc;
    private final File plugJar;
    private final File updDir;

    public AdminSub(
            Supplier<Msg> msgSup,
            Runnable reloadAct,
            SessList sessList,
            TrapMgr trapMgr,
            DetRepo repo,
            Exec exec,
            UpdSvc updSvc,
            File plugJar,
            File updDir
    ) {
        this.msgSup = msgSup;
        this.reloadAct = reloadAct;
        this.sessList = sessList;
        this.trapMgr = trapMgr;
        this.repo = repo;
        this.exec = exec;
        this.updSvc = updSvc;
        this.plugJar = plugJar;
        this.updDir = updDir;
    }

    public void reload(CommandSender snd, Msg msg) {
        if (!snd.hasPermission(PERM_ADMIN)) {
            msg.send(snd, msg.noPerm());
            return;
        }
        reloadAct.run();
        Msg latest = msgSup.get();
        latest.send(snd, latest.reloaded());
    }

    public void alerts(CommandSender snd, Msg msg) {
        if (!snd.hasPermission(PunSvc.PERM_ALERTS)) {
            msg.send(snd, msg.noPerm());
            return;
        }
        if (!(snd instanceof Player p)) {
            msg.send(snd, msg.playerOnly());
            return;
        }
        boolean on = sessList.toggleAlerts(p.getUniqueId());
        msg.send(p, on ? msg.alertsOn() : msg.alertsOff());
    }

    public void reset(CommandSender snd, String[] args, Msg msg) {
        if (!snd.hasPermission(PERM_ADMIN)) {
            msg.send(snd, msg.noPerm());
            return;
        }
        if (args.length < 2) {
            msg.sendLines(snd, msg.help(), Map.of());
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg.send(snd, msg.notFound(), Map.of("%player%", args[1]));
            return;
        }
        trapMgr.clearPlayer(target.getUniqueId(), true);
        Sess sess = sessList.get(target.getUniqueId());
        if (sess != null) {
            sess.resetAll();
            sessList.save(sess);
        } else {
            repo.saveSessState(target.getUniqueId(), target.getName(), 0.0, 0L, 0, 0L);
        }
        msg.send(snd, msg.resetDone(), Map.of("%player%", target.getName()));
    }

    public void update(CommandSender snd, Msg msg) {
        if (!snd.hasPermission(PERM_UPDATE) && !snd.hasPermission(PERM_ADMIN)) {
            msg.send(snd, msg.noPerm());
            return;
        }
        msg.send(snd, msg.updChecking());
        exec.runAsync(() -> {
            UpdSvc.UpdRes res = updSvc.downloadUpd(plugJar, updDir);
            exec.runForSnd(snd, () -> {
                Msg m = msgSup.get();
                switch (res.status()) {
                    case DOWNLOADED -> m.send(snd, m.updDownloaded(), Map.of("%version%", res.ver()));
                    case UP_TO_DATE -> m.send(snd, m.updUpToDate(), Map.of("%version%", res.ver()));
                    case NO_ASSET -> m.send(snd, m.updNoAsset(), Map.of("%version%", res.ver(), "%url%", res.detail()));
                    case IN_PROGRESS -> m.send(snd, m.updInProgress());
                    case FAILED -> m.send(snd, m.updFailed(), Map.of("%error%", res.detail()));
                }
            });
        });
    }
}
