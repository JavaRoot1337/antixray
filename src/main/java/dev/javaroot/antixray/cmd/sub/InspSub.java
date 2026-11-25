package dev.javaroot.antixray.cmd.sub;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.WorldCfg;
import dev.javaroot.antixray.db.DetRepo;
import dev.javaroot.antixray.db.mdl.DetLog;
import dev.javaroot.antixray.plat.sched.Exec;
import dev.javaroot.antixray.plat.txt.Msg;
import dev.javaroot.antixray.sess.Sess;
import dev.javaroot.antixray.sess.SessList;
import dev.javaroot.antixray.trap.TrapMgr;
import dev.javaroot.antixray.trap.TrapSpawn;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class InspSub {

    public static final String PERM_CHECK = "antixray.check";
    public static final String PERM_SPAWN = "antixray.spawn";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final int PAGE_SIZE = 8;

    private final Supplier<Cfg> cfgSup;
    private final Supplier<Msg> msgSup;
    private final SessList sessList;
    private final TrapMgr trapMgr;
    private final TrapSpawn trapSpawn;
    private final DetRepo repo;
    private final Exec exec;

    public InspSub(
            Supplier<Cfg> cfgSup,
            Supplier<Msg> msgSup,
            SessList sessList,
            TrapMgr trapMgr,
            TrapSpawn trapSpawn,
            DetRepo repo,
            Exec exec
    ) {
        this.cfgSup = cfgSup;
        this.msgSup = msgSup;
        this.sessList = sessList;
        this.trapMgr = trapMgr;
        this.trapSpawn = trapSpawn;
        this.repo = repo;
        this.exec = exec;
    }

    public void check(CommandSender snd, String[] args, Msg msg) {
        if (!snd.hasPermission(PERM_CHECK)) {
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

        Cfg cfg = cfgSup.get();
        Sess sess = sessList.getOrCreate(target);
        long nowMs = System.currentTimeMillis();
        double thresh = cfg.sus().triggerThreshold();
        long watchSec = sess.sus().watchRemSec(nowMs);
        String watchTxt = sess.sus().isWatch(nowMs, thresh)
                ? (watchSec > 0 ? watchSec + "s" : "ON")
                : "OFF";

        Map<String, String> vars = new HashMap<>();
        vars.put("%player%", target.getName());
        vars.put("%uuid%", target.getUniqueId().toString());
        vars.put("%suspicion%", String.format(Locale.ROOT, "%.1f", sess.sus().score()));
        vars.put("%threshold%", String.format(Locale.ROOT, "%.1f", thresh));
        vars.put("%watch_mode%", watchTxt);
        vars.put("%count%", String.valueOf(sess.traps().hits(nowMs, cfg.pun().resetAfterMin())));
        vars.put("%max_count%", String.valueOf(cfg.pun().triggerCount()));
        vars.put("%active_trap%", String.valueOf(trapMgr.activeCount(target.getUniqueId())));
        vars.put("%total_punishments%", String.valueOf(sess.traps().totalPunishments()));

        msg.send(snd, msg.checkHdr(), vars);
        msg.sendLines(snd, msg.checkLines(), vars);
    }

    public void spawn(CommandSender snd, String[] args, Msg msg) {
        if (!snd.hasPermission(PERM_SPAWN)) {
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

        Material forcedOre = null;
        if (args.length >= 3) {
            forcedOre = Material.matchMaterial(args[2].toUpperCase(Locale.ROOT));
            if (forcedOre == null || !forcedOre.isBlock() || forcedOre.isAir()) {
                msg.send(snd, msg.spawnFail(), Map.of("%player%", target.getName()));
                return;
            }
        }

        Material finalOre = forcedOre;
        exec.runEntity(target, () -> {
            Cfg cfg = cfgSup.get();
            WorldCfg wCfg = cfg.world(target.getWorld().getName());
            boolean ok = wCfg != null && wCfg.enabled()
                    && trapSpawn.spawnManual(target, sessList.getOrCreate(target), wCfg, finalOre);
            exec.runForSnd(snd, () -> {
                Msg m = msgSup.get();
                m.send(snd, ok ? m.spawnOk() : m.spawnFail(), Map.of("%player%", target.getName()));
            });
        });
    }

    public void history(CommandSender snd, String[] args, Msg msg) {
        if (!snd.hasPermission(PERM_CHECK)) {
            msg.send(snd, msg.noPerm());
            return;
        }
        if (args.length < 2) {
            msg.sendLines(snd, msg.help(), Map.of());
            return;
        }
        String name = args[1];
        int reqPage = parsePage(args);

        Player online = Bukkit.getPlayerExact(name);
        CompletableFuture<UUID> uuidFut = online != null
                ? CompletableFuture.completedFuture(online.getUniqueId())
                : repo.findUuidByName(name);

        int maxCnt = cfgSup.get().pun().triggerCount();
        uuidFut.thenAccept(uuid -> {
            if (uuid == null) {
                sendEmpty(snd, name);
                return;
            }
            repo.fetchHist(uuid, reqPage, PAGE_SIZE).thenAccept(page -> {
                if (page.entries().isEmpty()) {
                    sendEmpty(snd, name);
                    return;
                }
                exec.runForSnd(snd, () -> {
                    Msg m = msgSup.get();
                    m.send(snd, m.histHdr(), Map.of(
                            "%player%", name,
                            "%page%", String.valueOf(page.page()),
                            "%max_page%", String.valueOf(page.maxPage())
                    ));
                    for (DetLog e : page.entries()) {
                        Map<String, String> row = new HashMap<>();
                        row.put("%date%", DATE_FMT.format(Instant.ofEpochMilli(e.createdAtMs())));
                        row.put("%ore%", m.oreName(e.oreType()));
                        row.put("%count%", String.valueOf(e.sessCount()));
                        row.put("%max_count%", String.valueOf(maxCnt));
                        row.put("%world%", e.world());
                        row.put("%x%", String.valueOf(e.x()));
                        row.put("%y%", String.valueOf(e.y()));
                        row.put("%z%", String.valueOf(e.z()));
                        m.send(snd, m.histEntry(), row);
                    }
                });
            });
        });
    }

    private void sendEmpty(CommandSender snd, String name) {
        exec.runForSnd(snd, () -> {
            Msg m = msgSup.get();
            m.send(snd, m.histEmpty(), Map.of("%player%", name));
        });
    }

    private static int parsePage(String[] args) {
        if (args.length < 3) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(args[2]));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}
