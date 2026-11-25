package dev.javaroot.antixray.plat.txt;

import dev.javaroot.antixray.cfg.mig.CfgMig;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Msg {

    private final Hex hex;
    private final List<String> alertHit;
    private final List<String> alertPunished;
    private final String consoleHit;
    private final String consolePunished;

    private final String noPerm;
    private final String playerOnly;
    private final String notFound;
    private final String reloaded;
    private final String alertsOn;
    private final String alertsOff;
    private final String resetDone;
    private final String spawnOk;
    private final String spawnFail;
    private final String updChecking;
    private final String updUpToDate;
    private final String updDownloaded;
    private final String updNoAsset;
    private final String updInProgress;
    private final String updFailed;

    private final String checkHdr;
    private final List<String> checkLines;
    private final String histHdr;
    private final String histEntry;
    private final String histEmpty;
    private final List<String> help;

    private final Map<String, String> oreNames;

    private Msg(YamlConfiguration cfg) {
        this.hex = new Hex(readMap(cfg.getConfigurationSection("palette")));

        this.alertHit = List.copyOf(cfg.getStringList("alerts.hit"));
        this.alertPunished = List.copyOf(cfg.getStringList("alerts.punished"));
        this.consoleHit = cfg.getString("alerts.console-hit", "");
        this.consolePunished = cfg.getString("alerts.console-punished", "");

        this.noPerm = cfg.getString("commands.no-permission", "%danger%Нет прав.");
        this.playerOnly = cfg.getString("commands.player-only", "%danger%Команда только для игрока.");
        this.notFound = cfg.getString("commands.player-not-found", "%danger%Игрок %player% не найден.");
        this.reloaded = cfg.getString("commands.reloaded", "%success%Конфиги перезагружены.");
        this.alertsOn = cfg.getString("commands.alerts-enabled", "%success%Алерты включены.");
        this.alertsOff = cfg.getString("commands.alerts-disabled", "%muted%Алерты выключены.");
        this.resetDone = cfg.getString("commands.reset-done", "%success%Данные %accent%%player% %success%сброшены.");
        this.spawnOk = cfg.getString("commands.spawn-success", "%success%Ловушка заспавнена рядом с %accent%%player%.");
        this.spawnFail = cfg.getString("commands.spawn-failed", "%warning%Нет подходящей стены рядом с %accent%%player%.");
        this.updChecking = cfg.getString("commands.update-checking", "%primary%Проверяю обновления...");
        this.updUpToDate = cfg.getString("commands.update-up-to-date", "%success%Установлена актуальная версия %accent%v%version%");
        this.updDownloaded = cfg.getString("commands.update-downloaded", "%success%Обновление %accent%v%version% %success%загружено, перезапусти сервер.");
        this.updNoAsset = cfg.getString("commands.update-no-asset", "%warning%В релизе %accent%v%version% %warning%нет .jar файла: %url%");
        this.updInProgress = cfg.getString("commands.update-in-progress", "%warning%Обновление уже загружается.");
        this.updFailed = cfg.getString("commands.update-failed", "%danger%Ошибка обновления: %error%");

        this.checkHdr = cfg.getString("commands.check-header", "%primary%--- %player% ---");
        this.checkLines = List.copyOf(cfg.getStringList("commands.check-lines"));
        this.histHdr = cfg.getString("commands.history-header", "%primary%--- История %player% (%page%/%max_page%) ---");
        this.histEntry = cfg.getString("commands.history-entry", "%muted%[%date%] %primary%%ore% %muted%(%count%/%max_count%) -> %accent%%world% %x% %y% %z%");
        this.histEmpty = cfg.getString("commands.history-empty", "%muted%Записей для %player% нет.");
        this.help = List.copyOf(cfg.getStringList("commands.help"));

        this.oreNames = Map.copyOf(readMap(cfg.getConfigurationSection("ore-names")));
    }

    public static Msg load(Plugin plug) {
        CfgMig.ensureUpdated(plug, "message.yml", Set.of("ore-names", "palette"));
        return new Msg(YamlConfiguration.loadConfiguration(new File(plug.getDataFolder(), "message.yml")));
    }

    public String oreName(Material mat) {
        return mat == null ? "" : oreNames.getOrDefault(mat.name(), mat.name());
    }

    public String oreName(String matName) {
        return matName == null ? "" : oreNames.getOrDefault(matName, matName);
    }

    public Component parse(String tpl, Map<String, String> vars) {
        return hex.parse(Hex.applyVars(tpl, vars));
    }

    public String plain(String tpl, Map<String, String> vars) {
        return Hex.applyVars(tpl, vars);
    }

    public void send(CommandSender snd, String tpl) {
        snd.sendMessage(hex.parse(tpl));
    }

    public void send(CommandSender snd, String tpl, Map<String, String> vars) {
        snd.sendMessage(parse(tpl, vars));
    }

    public void sendLines(CommandSender snd, List<String> lines, Map<String, String> vars) {
        for (String line : lines) {
            send(snd, line, vars);
        }
    }

    public List<String> alertHit() { return alertHit; }
    public List<String> alertPunished() { return alertPunished; }
    public String consoleHit() { return consoleHit; }
    public String consolePunished() { return consolePunished; }
    public String noPerm() { return noPerm; }
    public String playerOnly() { return playerOnly; }
    public String notFound() { return notFound; }
    public String reloaded() { return reloaded; }
    public String alertsOn() { return alertsOn; }
    public String alertsOff() { return alertsOff; }
    public String resetDone() { return resetDone; }
    public String spawnOk() { return spawnOk; }
    public String spawnFail() { return spawnFail; }
    public String updChecking() { return updChecking; }
    public String updUpToDate() { return updUpToDate; }
    public String updDownloaded() { return updDownloaded; }
    public String updNoAsset() { return updNoAsset; }
    public String updInProgress() { return updInProgress; }
    public String updFailed() { return updFailed; }
    public String checkHdr() { return checkHdr; }
    public List<String> checkLines() { return checkLines; }
    public String histHdr() { return histHdr; }
    public String histEntry() { return histEntry; }
    public String histEmpty() { return histEmpty; }
    public List<String> help() { return help; }

    private static Map<String, String> readMap(ConfigurationSection sec) {
        if (sec == null) {
            return Map.of();
        }
        Map<String, String> out = new HashMap<>();
        for (String key : sec.getKeys(false)) {
            String val = sec.getString(key);
            if (val != null) {
                out.put(key, val);
            }
        }
        return out;
    }
}
