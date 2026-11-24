package dev.javaroot.antixray.pun;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.Msg;
import dev.javaroot.antixray.db.LogRepo;
import dev.javaroot.antixray.list.SessList;
import dev.javaroot.antixray.mdl.Session;
import dev.javaroot.antixray.mdl.Vein;
import dev.javaroot.antixray.sched.Exec;
import dev.javaroot.antixray.trap.TrapMgr;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class Punish {

    public static final String PERM_ALERTS = "antixray.alerts";

    private final Supplier<Cfg> cfgSupplier;
    private final Supplier<Msg> msgSupplier;
    private final SessList sessList;
    private final TrapMgr trapMgr;
    private final LogRepo logRepo;
    private final Exec exec;
    private final Logger logger;

    public Punish(
            Supplier<Cfg> cfgSupplier,
            Supplier<Msg> msgSupplier,
            SessList sessList,
            TrapMgr trapMgr,
            LogRepo logRepo,
            Exec exec,
            Logger logger
    ) {
        this.cfgSupplier = cfgSupplier;
        this.msgSupplier = msgSupplier;
        this.sessList = sessList;
        this.trapMgr = trapMgr;
        this.logRepo = logRepo;
        this.exec = exec;
        this.logger = logger;
    }

    public void handleTrapHit(Player player, Vein vein, int hitX, int hitY, int hitZ) {
        if (!player.isOnline()) {
            return;
        }
        Cfg cfg = cfgSupplier.get();
        Msg msg = msgSupplier.get();
        Session session = sessList.getOrCreate(player);
        long nowMs = System.currentTimeMillis();

        int count = session.incrementPhantomHit(nowMs, cfg.countResetAfterMinutes);
        int maxCount = cfg.punishmentTriggerCount;
        double suspicion = session.suspicionScore();
        double dist = Math.sqrt(vein.distanceSquared(
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ()
        ));

        logRepo.recordDetection(
                player.getUniqueId(),
                player.getName(),
                vein.worldName(),
                hitX,
                hitY,
                hitZ,
                vein.phantomOre().name(),
                suspicion,
                count,
                nowMs
        );

        Map<String, String> vars = new HashMap<>();
        vars.put("%player%", player.getName());
        vars.put("%uuid%", player.getUniqueId().toString());
        vars.put("%count%", String.valueOf(count));
        vars.put("%max_count%", String.valueOf(maxCount));
        vars.put("%ore%", msg.oreName(vein.phantomOre()));
        vars.put("%world%", vein.worldName());
        vars.put("%x%", String.valueOf(hitX));
        vars.put("%y%", String.valueOf(hitY));
        vars.put("%z%", String.valueOf(hitZ));
        vars.put("%suspicion%", String.format(Locale.ROOT, "%.1f", suspicion));
        vars.put("%distance%", String.format(Locale.ROOT, "%.1f", dist));
        vars.put("%threshold%", String.format(Locale.ROOT, "%.1f", cfg.triggerThreshold));
        vars.put("%watch_mode%", session.isWatchMode(nowMs, cfg.triggerThreshold) ? "ON" : "OFF");
        vars.put("%active_trap%", String.valueOf(trapMgr.activeCount(player.getUniqueId())));
        vars.put("%total_punishments%", String.valueOf(session.totalPunishments()));

        if (cfg.logToConsole && !msg.consoleHit().isEmpty()) {
            logger.info(msg.plain(msg.consoleHit(), vars));
        }

        if (count >= maxCount) {
            List<String> executed = new ArrayList<>(cfg.punishmentCommands.size());
            for (String template : cfg.punishmentCommands) {
                executed.add(Msg.applyPlaceholders(template, vars));
            }
            if (!executed.isEmpty()) {
                exec.runGlobal(() -> {
                    for (String cmd : executed) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    }
                });
            }

            session.incrementTotalPunishments();
            vars.put("%total_punishments%", String.valueOf(session.totalPunishments()));

            logRepo.recordPunishment(
                    player.getUniqueId(),
                    player.getName(),
                    vein.worldName(),
                    hitX,
                    hitY,
                    hitZ,
                    count,
                    String.join("; ", executed),
                    nowMs
            );

            if (cfg.logToConsole && !msg.consolePunished().isEmpty()) {
                logger.info(msg.plain(msg.consolePunished(), vars));
            }

            if (cfg.resetCountOnPunish) {
                session.resetPhantomHitCount();
            }
            if (cfg.alertOnPunish) {
                broadcastStaff(msg, msg.alertPunished(), vars);
            }
            return;
        }

        session.forceTrapCooldown(nowMs, cfg.fastRespawnAfterHitSeconds);

        boolean shouldAlert = (count == 1 && cfg.alertOnFirstHit)
                || (count == 2 && cfg.alertOnSecondHit)
                || count > 2;
        if (shouldAlert) {
            broadcastStaff(msg, msg.alertHit(), vars);
        }
    }

    private void broadcastStaff(Msg msg, List<String> lines, Map<String, String> vars) {
        if (lines.isEmpty()) {
            return;
        }
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!sessList.hasAlertsEnabled(staff.getUniqueId())) {
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
