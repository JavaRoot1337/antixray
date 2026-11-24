package dev.javaroot.antixray.cfg;

import dev.javaroot.antixray.clr.Hex;
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

public final class Msg {

    private final Hex hex;
    private final List<String> alertHit;
    private final List<String> alertPunished;
    private final String consoleHit;
    private final String consolePunished;

    private final String noPermission;
    private final String playerNotFound;
    private final String reloaded;
    private final String alertsEnabled;
    private final String alertsDisabled;
    private final String resetDone;
    private final String spawnSuccess;
    private final String spawnFailed;
    private final String updateChecking;
    private final String updateUpToDate;
    private final String updateDownloaded;
    private final String updateNoAsset;
    private final String updateInProgress;
    private final String updateFailed;

    private final String checkHeader;
    private final List<String> checkLines;
    private final String historyHeader;
    private final String historyEntry;
    private final String historyEmpty;
    private final List<String> help;

    private final Map<String, String> oreNames;

    private Msg(YamlConfiguration cfg) {
        Map<String, String> palette = new HashMap<>();
        ConfigurationSection palSec = cfg.getConfigurationSection("palette");
        if (palSec != null) {
            for (String key : palSec.getKeys(false)) {
                String val = palSec.getString(key);
                if (val != null) {
                    palette.put(key, val);
                }
            }
        }
        this.hex = new Hex(palette);

        this.alertHit = List.copyOf(cfg.getStringList("alerts.hit"));
        this.alertPunished = List.copyOf(cfg.getStringList("alerts.punished"));
        this.consoleHit = cfg.getString("alerts.console-hit", "");
        this.consolePunished = cfg.getString("alerts.console-punished", "");

        this.noPermission = cfg.getString("commands.no-permission", "%danger%Нет прав.");
        this.playerNotFound = cfg.getString("commands.player-not-found", "%danger%Игрок %player% не найден.");
        this.reloaded = cfg.getString("commands.reloaded", "%success%Конфиги перезагружены.");
        this.alertsEnabled = cfg.getString("commands.alerts-enabled", "%success%Алерты включены.");
        this.alertsDisabled = cfg.getString("commands.alerts-disabled", "%muted%Алерты выключены.");
        this.resetDone = cfg.getString("commands.reset-done", "%success%Данные %accent%%player% %success%сброшены.");
        this.spawnSuccess = cfg.getString("commands.spawn-success", "%success%Ловушка заспавнена рядом с %accent%%player%.");
        this.spawnFailed = cfg.getString("commands.spawn-failed", "%warning%Нет подходящей стены рядом с %accent%%player%.");
        this.updateChecking = cfg.getString("commands.update-checking", "%primary%Проверяю обновления...");
        this.updateUpToDate = cfg.getString("commands.update-up-to-date", "%success%Установлена актуальная версия %accent%v%version%");
        this.updateDownloaded = cfg.getString("commands.update-downloaded", "%success%Обновление %accent%v%version% %success%загружено, перезапусти сервер.");
        this.updateNoAsset = cfg.getString("commands.update-no-asset", "%warning%В релизе %accent%v%version% %warning%нет .jar файла: %url%");
        this.updateInProgress = cfg.getString("commands.update-in-progress", "%warning%Обновление уже загружается.");
        this.updateFailed = cfg.getString("commands.update-failed", "%danger%Ошибка обновления: %error%");

        this.checkHeader = cfg.getString("commands.check-header", "%primary%--- %player% ---");
        this.checkLines = List.copyOf(cfg.getStringList("commands.check-lines"));
        this.historyHeader = cfg.getString("commands.history-header", "%primary%--- История %player% (%page%/%max_page%) ---");
        this.historyEntry = cfg.getString("commands.history-entry", "%muted%[%date%] %primary%%ore% %muted%(%count%/%max_count%) -> %accent%%world% %x% %y% %z%");
        this.historyEmpty = cfg.getString("commands.history-empty", "%muted%Записей для %player% нет.");
        this.help = List.copyOf(cfg.getStringList("commands.help"));

        Map<String, String> ores = new HashMap<>();
        ConfigurationSection oreSec = cfg.getConfigurationSection("ore-names");
        if (oreSec != null) {
            for (String key : oreSec.getKeys(false)) {
                String val = oreSec.getString(key);
                if (val != null) {
                    ores.put(key, val);
                }
            }
        }
        this.oreNames = Map.copyOf(ores);
    }

    public static Msg load(Plugin plugin) {
        CfgMig.ensureUpdated(plugin, "message.yml", java.util.Set.of("ore-names", "palette"));
        File file = new File(plugin.getDataFolder(), "message.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return new Msg(yaml);
    }

    public String oreName(Material material) {
        if (material == null) {
            return "";
        }
        return oreNames.getOrDefault(material.name(), material.name());
    }

    public String oreName(String materialName) {
        if (materialName == null) {
            return "";
        }
        return oreNames.getOrDefault(materialName, materialName);
    }

    public Component parse(String template, Map<String, String> placeholders) {
        return hex.parse(applyPlaceholders(template, placeholders));
    }

    public String plain(String template, Map<String, String> placeholders) {
        return applyPlaceholders(template, placeholders);
    }

    public void send(CommandSender sender, String template) {
        sender.sendMessage(hex.parse(template));
    }

    public void send(CommandSender sender, String template, Map<String, String> placeholders) {
        sender.sendMessage(parse(template, placeholders));
    }

    public void sendLines(CommandSender sender, List<String> lines, Map<String, String> placeholders) {
        for (String line : lines) {
            send(sender, line, placeholders);
        }
    }

    public List<String> alertHit() {
        return alertHit;
    }

    public List<String> alertPunished() {
        return alertPunished;
    }

    public String consoleHit() {
        return consoleHit;
    }

    public String consolePunished() {
        return consolePunished;
    }

    public String noPermission() {
        return noPermission;
    }

    public String playerNotFound() {
        return playerNotFound;
    }

    public String reloaded() {
        return reloaded;
    }

    public String alertsEnabled() {
        return alertsEnabled;
    }

    public String alertsDisabled() {
        return alertsDisabled;
    }

    public String resetDone() {
        return resetDone;
    }

    public String spawnSuccess() {
        return spawnSuccess;
    }

    public String spawnFailed() {
        return spawnFailed;
    }

    public String updateChecking() {
        return updateChecking;
    }

    public String updateUpToDate() {
        return updateUpToDate;
    }

    public String updateDownloaded() {
        return updateDownloaded;
    }

    public String updateNoAsset() {
        return updateNoAsset;
    }

    public String updateInProgress() {
        return updateInProgress;
    }

    public String updateFailed() {
        return updateFailed;
    }

    public String checkHeader() {
        return checkHeader;
    }

    public List<String> checkLines() {
        return checkLines;
    }

    public String historyHeader() {
        return historyHeader;
    }

    public String historyEntry() {
        return historyEntry;
    }

    public String historyEmpty() {
        return historyEmpty;
    }

    public List<String> help() {
        return help;
    }

    public static String applyPlaceholders(String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty() || placeholders == null || placeholders.isEmpty()) {
            return template == null ? "" : template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace(key, val);
        }
        return result;
    }
}
