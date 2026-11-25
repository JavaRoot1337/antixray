package dev.javaroot.antixray.cmd;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.WorldCfg;
import dev.javaroot.antixray.cmd.sub.AdminSub;
import dev.javaroot.antixray.cmd.sub.InspSub;
import dev.javaroot.antixray.plat.txt.Msg;
import dev.javaroot.antixray.pun.PunSvc;
import dev.javaroot.antixray.sess.Sess;
import dev.javaroot.antixray.sess.SessList;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class AxrCmd implements BasicCommand {

    private final Supplier<Cfg> cfgSup;
    private final Supplier<Msg> msgSup;
    private final SessList sessList;
    private final AdminSub adminSub;
    private final InspSub inspSub;

    public AxrCmd(
            Supplier<Cfg> cfgSup,
            Supplier<Msg> msgSup,
            SessList sessList,
            AdminSub adminSub,
            InspSub inspSub
    ) {
        this.cfgSup = cfgSup;
        this.msgSup = msgSup;
        this.sessList = sessList;
        this.adminSub = adminSub;
        this.inspSub = inspSub;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender snd = stack.getSender();
        Msg msg = msgSup.get();
        if (args.length == 0) {
            msg.sendLines(snd, msg.help(), Map.of());
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> adminSub.reload(snd, msg);
            case "alerts" -> adminSub.alerts(snd, msg);
            case "reset" -> adminSub.reset(snd, args, msg);
            case "update" -> adminSub.update(snd, msg);
            case "check" -> inspSub.check(snd, args, msg);
            case "spawn" -> inspSub.spawn(snd, args, msg);
            case "history" -> inspSub.history(snd, args, msg);
            default -> msg.sendLines(snd, msg.help(), Map.of());
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender snd = stack.getSender();
        if (args.length <= 1) {
            return suggestSubs(snd, args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "");
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("check") || sub.equals("spawn") || sub.equals("reset") || sub.equals("history"))) {
            String pfx = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                addIfMatch(names, p.getName(), pfx);
            }
            return names;
        }

        if (args.length == 3 && sub.equals("spawn") && snd.hasPermission(InspSub.PERM_SPAWN)) {
            return suggestOres(args[1], args[2].toLowerCase(Locale.ROOT));
        }
        return List.of();
    }

    private List<String> suggestSubs(CommandSender snd, String pfx) {
        List<String> out = new ArrayList<>();
        if (snd.hasPermission(AdminSub.PERM_ADMIN)) {
            addIfMatch(out, "reload", pfx);
            addIfMatch(out, "reset", pfx);
        }
        if (snd.hasPermission(PunSvc.PERM_ALERTS)) {
            addIfMatch(out, "alerts", pfx);
        }
        if (snd.hasPermission(InspSub.PERM_CHECK)) {
            addIfMatch(out, "check", pfx);
            addIfMatch(out, "history", pfx);
        }
        if (snd.hasPermission(InspSub.PERM_SPAWN)) {
            addIfMatch(out, "spawn", pfx);
        }
        if (snd.hasPermission(AdminSub.PERM_UPDATE) || snd.hasPermission(AdminSub.PERM_ADMIN)) {
            addIfMatch(out, "update", pfx);
        }
        return out;
    }

    private List<String> suggestOres(String pName, String pfx) {
        Player target = Bukkit.getPlayerExact(pName);
        if (target == null) {
            return List.of();
        }
        Sess sess = sessList.get(target.getUniqueId());
        String wName = sess != null ? sess.curWorld() : null;
        WorldCfg wCfg = cfgSup.get().world(wName);
        if (wCfg == null) {
            return List.of();
        }
        List<String> ores = new ArrayList<>();
        for (Material mat : wCfg.trackedOres()) {
            addIfMatch(ores, mat.name(), pfx);
        }
        return ores;
    }

    private static void addIfMatch(List<String> out, String cand, String pfx) {
        if (cand.toLowerCase(Locale.ROOT).startsWith(pfx)) {
            out.add(cand);
        }
    }
}
