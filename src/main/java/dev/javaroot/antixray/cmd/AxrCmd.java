package dev.javaroot.antixray.cmd;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.Msg;
import dev.javaroot.antixray.cfg.WorldCfg;
import dev.javaroot.antixray.db.LogRepo;
import dev.javaroot.antixray.list.SessList;
import dev.javaroot.antixray.mdl.DetLog;
import dev.javaroot.antixray.mdl.Session;
import dev.javaroot.antixray.pun.Punish;
import dev.javaroot.antixray.sched.Exec;
import dev.javaroot.antixray.trap.TrapMgr;
import dev.javaroot.antixray.trap.TrapSpawn;
import dev.javaroot.antixray.update.UpdChecker;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class AxrCmd implements BasicCommand {

    private static final String PERM_ADMIN = "antixray.admin";
    private static final String PERM_CHECK = "antixray.check";
    private static final String PERM_SPAWN = "antixray.spawn";
    private static final String PERM_UPDATE = "antixray.update";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final int HISTORY_PAGE_SIZE = 8;

    private final Supplier<Cfg> cfgSupplier;
    private final Supplier<Msg> msgSupplier;
    private final Runnable reloadAction;
    private final SessList sessList;
    private final TrapMgr trapMgr;
    private final TrapSpawn trapSpawn;
    private final LogRepo logRepo;
    private final Exec exec;
    private final UpdChecker updChecker;
    private final File pluginJar;
    private final File updateFolder;

    public AxrCmd(
            Supplier<Cfg> cfgSupplier,
            Supplier<Msg> msgSupplier,
            Runnable reloadAction,
            SessList sessList,
            TrapMgr trapMgr,
            TrapSpawn trapSpawn,
            LogRepo logRepo,
            Exec exec,
            UpdChecker updChecker,
            File pluginJar,
            File updateFolder
    ) {
        this.cfgSupplier = cfgSupplier;
        this.msgSupplier = msgSupplier;
        this.reloadAction = reloadAction;
        this.sessList = sessList;
        this.trapMgr = trapMgr;
        this.trapSpawn = trapSpawn;
        this.logRepo = logRepo;
        this.exec = exec;
        this.updChecker = updChecker;
        this.pluginJar = pluginJar;
        this.updateFolder = updateFolder;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        Msg msg = msgSupplier.get();

        if (args.length == 0) {
            msg.sendLines(sender, msg.help(), Map.of());
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> handleReload(sender, msg);
            case "alerts" -> handleAlerts(sender, msg);
            case "check" -> handleCheck(sender, args, msg);
            case "spawn" -> handleSpawn(sender, args, msg);
            case "reset" -> handleReset(sender, args, msg);
            case "history" -> handleHistory(sender, args, msg);
            case "update" -> handleUpdate(sender, msg);
            default -> msg.sendLines(sender, msg.help(), Map.of());
        }
    }

    private void handleUpdate(CommandSender sender, Msg msg) {
        if (!sender.hasPermission(PERM_UPDATE) && !sender.hasPermission(PERM_ADMIN)) {
            msg.send(sender, msg.noPermission());
            return;
        }
        msg.send(sender, msg.updateChecking());
        exec.runAsync(() -> {
            UpdChecker.UpdateResult result = updChecker.downloadUpdate(pluginJar, updateFolder);
            Msg latestMsg = msgSupplier.get();
            switch (result.status()) {
                case DOWNLOADED -> sendFeedback(sender, latestMsg.updateDownloaded(), Map.of("%version%", result.version()));
                case UP_TO_DATE -> sendFeedback(sender, latestMsg.updateUpToDate(), Map.of("%version%", result.version()));
                case NO_ASSET -> sendFeedback(sender, latestMsg.updateNoAsset(),
                        Map.of("%version%", result.version(), "%url%", result.detail()));
                case IN_PROGRESS -> sendFeedback(sender, latestMsg.updateInProgress(), Map.of());
                case FAILED -> sendFeedback(sender, latestMsg.updateFailed(), Map.of("%error%", result.detail()));
            }
        });
    }

    private void handleReload(CommandSender sender, Msg msg) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            msg.send(sender, msg.noPermission());
            return;
        }
        reloadAction.run();
        msgSupplier.get().send(sender, msgSupplier.get().reloaded());
    }

    private void handleAlerts(CommandSender sender, Msg msg) {
        if (!sender.hasPermission(Punish.PERM_ALERTS)) {
            msg.send(sender, msg.noPermission());
            return;
        }
        if (!(sender instanceof Player player)) {
            return;
        }
        boolean enabled = sessList.toggleAlerts(player.getUniqueId());
        msg.send(player, enabled ? msg.alertsEnabled() : msg.alertsDisabled());
    }

    private void handleCheck(CommandSender sender, String[] args, Msg msg) {
        if (!sender.hasPermission(PERM_CHECK)) {
            msg.send(sender, msg.noPermission());
            return;
        }
        if (args.length < 2) {
            msg.sendLines(sender, msg.help(), Map.of());
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg.send(sender, msg.playerNotFound(), Map.of("%player%", args[1]));
            return;
        }

        Cfg cfg = cfgSupplier.get();
        Session session = sessList.getOrCreate(target);
        long nowMs = System.currentTimeMillis();
        long watchSec = session.watchRemainingSeconds(nowMs);
        String watchText = session.isWatchMode(nowMs, cfg.triggerThreshold)
                ? (watchSec > 0 ? watchSec + "s" : "ON")
                : "OFF";

        Map<String, String> vars = new HashMap<>();
        vars.put("%player%", target.getName());
        vars.put("%uuid%", target.getUniqueId().toString());
        vars.put("%suspicion%", String.format(Locale.ROOT, "%.1f", session.suspicionScore()));
        vars.put("%threshold%", String.format(Locale.ROOT, "%.1f", cfg.triggerThreshold));
        vars.put("%watch_mode%", watchText);
        vars.put("%count%", String.valueOf(session.phantomHitCount(nowMs, cfg.countResetAfterMinutes)));
        vars.put("%max_count%", String.valueOf(cfg.punishmentTriggerCount));
        vars.put("%active_trap%", String.valueOf(trapMgr.activeCount(target.getUniqueId())));
        vars.put("%total_punishments%", String.valueOf(session.totalPunishments()));

        msg.send(sender, msg.checkHeader(), vars);
        msg.sendLines(sender, msg.checkLines(), vars);
    }

    private void handleSpawn(CommandSender sender, String[] args, Msg msg) {
        if (!sender.hasPermission(PERM_SPAWN)) {
            msg.send(sender, msg.noPermission());
            return;
        }
        if (args.length < 2) {
            msg.sendLines(sender, msg.help(), Map.of());
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg.send(sender, msg.playerNotFound(), Map.of("%player%", args[1]));
            return;
        }

        Material forcedOre = null;
        if (args.length >= 3) {
            forcedOre = Material.matchMaterial(args[2].toUpperCase(Locale.ROOT));
            if (forcedOre == null || !forcedOre.isBlock() || forcedOre.isAir()) {
                msg.send(sender, msg.spawnFailed(), Map.of("%player%", target.getName()));
                return;
            }
        }

        Material finalOre = forcedOre;
        exec.runEntity(target, () -> {
            Cfg cfg = cfgSupplier.get();
            WorldCfg worldCfg = cfg.world(target.getWorld().getName());
            boolean spawned = worldCfg != null && worldCfg.enabled()
                    && trapSpawn.spawnManual(target, sessList.getOrCreate(target), worldCfg, finalOre);
            sendFeedback(sender, spawned ? msg.spawnSuccess() : msg.spawnFailed(), Map.of("%player%", target.getName()));
        });
    }

    private void handleReset(CommandSender sender, String[] args, Msg msg) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            msg.send(sender, msg.noPermission());
            return;
        }
        if (args.length < 2) {
            msg.sendLines(sender, msg.help(), Map.of());
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg.send(sender, msg.playerNotFound(), Map.of("%player%", args[1]));
            return;
        }
        trapMgr.clearPlayer(target.getUniqueId(), true);
        Session session = sessList.get(target.getUniqueId());
        if (session != null) {
            session.resetAll();
            sessList.saveSession(session);
        } else {
            logRepo.saveSessionState(target.getUniqueId(), target.getName(), 0.0, 0L, 0, 0L);
        }
        msg.send(sender, msg.resetDone(), Map.of("%player%", target.getName()));
    }

    private void handleHistory(CommandSender sender, String[] args, Msg msg) {
        if (!sender.hasPermission(PERM_CHECK)) {
            msg.send(sender, msg.noPermission());
            return;
        }
        if (args.length < 2) {
            msg.sendLines(sender, msg.help(), Map.of());
            return;
        }
        String targetName = args[1];
        int requestedPage = 1;
        if (args.length >= 3) {
            try {
                requestedPage = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException ignored) {
                requestedPage = 1;
            }
        }

        Player online = Bukkit.getPlayerExact(targetName);
        CompletableFuture<UUID> uuidFuture = online != null
                ? CompletableFuture.completedFuture(online.getUniqueId())
                : logRepo.findUuidByUsername(targetName);

        int finalPage = requestedPage;
        int maxCount = cfgSupplier.get().punishmentTriggerCount;
        uuidFuture.thenAccept(uuid -> {
            if (uuid == null) {
                sendFeedback(sender, msg.historyEmpty(), Map.of("%player%", targetName));
                return;
            }
            logRepo.fetchHistory(uuid, finalPage, HISTORY_PAGE_SIZE).thenAccept(page -> {
                if (page.entries().isEmpty()) {
                    sendFeedback(sender, msg.historyEmpty(), Map.of("%player%", targetName));
                    return;
                }
                Runnable reply = () -> {
                    Map<String, String> headerVars = Map.of(
                            "%player%", targetName,
                            "%page%", String.valueOf(page.page()),
                            "%max_page%", String.valueOf(page.maxPage())
                    );
                    msg.send(sender, msg.historyHeader(), headerVars);
                    for (DetLog entry : page.entries()) {
                        Map<String, String> rowVars = new HashMap<>();
                        rowVars.put("%date%", DATE_FMT.format(Instant.ofEpochMilli(entry.createdAtMs())));
                        rowVars.put("%ore%", msg.oreName(entry.oreType()));
                        rowVars.put("%count%", String.valueOf(entry.sessionCount()));
                        rowVars.put("%max_count%", String.valueOf(maxCount));
                        rowVars.put("%world%", entry.world());
                        rowVars.put("%x%", String.valueOf(entry.x()));
                        rowVars.put("%y%", String.valueOf(entry.y()));
                        rowVars.put("%z%", String.valueOf(entry.z()));
                        msg.send(sender, msg.historyEntry(), rowVars);
                    }
                };
                if (sender instanceof Player sp) {
                    exec.runEntity(sp, reply);
                } else {
                    exec.runGlobal(reply);
                }
            });
        });
    }

    private void sendFeedback(CommandSender sender, String template, Map<String, String> vars) {
        Msg msg = msgSupplier.get();
        if (sender instanceof Player player) {
            exec.runEntity(player, () -> msg.send(player, template, vars));
        } else {
            exec.runGlobal(() -> msg.send(sender, template, vars));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            List<String> out = new ArrayList<>();
            if (sender.hasPermission(PERM_ADMIN)) {
                addIfMatches(out, "reload", prefix);
                addIfMatches(out, "reset", prefix);
            }
            if (sender.hasPermission(Punish.PERM_ALERTS)) {
                addIfMatches(out, "alerts", prefix);
            }
            if (sender.hasPermission(PERM_CHECK)) {
                addIfMatches(out, "check", prefix);
                addIfMatches(out, "history", prefix);
            }
            if (sender.hasPermission(PERM_SPAWN)) {
                addIfMatches(out, "spawn", prefix);
            }
            if (sender.hasPermission(PERM_UPDATE) || sender.hasPermission(PERM_ADMIN)) {
                addIfMatches(out, "update", prefix);
            }
            return out;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("check") || sub.equals("spawn") || sub.equals("reset") || sub.equals("history"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                addIfMatches(names, p.getName(), prefix);
            }
            return names;
        }

        if (args.length == 3 && sub.equals("spawn") && sender.hasPermission(PERM_SPAWN)) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                return List.of();
            }
            Session targetSession = sessList.get(target.getUniqueId());
            String worldName = targetSession != null ? targetSession.currentWorld() : null;
            WorldCfg worldCfg = cfgSupplier.get().world(worldName);
            if (worldCfg == null) {
                return List.of();
            }
            String prefix = args[2].toLowerCase(Locale.ROOT);
            List<String> ores = new ArrayList<>();
            for (Material mat : worldCfg.trackedOres()) {
                addIfMatches(ores, mat.name(), prefix);
            }
            return ores;
        }
        return List.of();
    }

    private static void addIfMatches(List<String> out, String candidate, String prefix) {
        if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            out.add(candidate);
        }
    }
}
