package dev.javaroot.antixray.cfg;

import dev.javaroot.antixray.cfg.mig.CfgMig;
import dev.javaroot.antixray.cfg.sec.GenCfg;
import dev.javaroot.antixray.cfg.sec.PunCfg;
import dev.javaroot.antixray.cfg.sec.ReactCfg;
import dev.javaroot.antixray.cfg.sec.SusCfg;
import dev.javaroot.antixray.cfg.sec.TrapCfg;
import dev.javaroot.antixray.cfg.sec.UpdCfg;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class Cfg {

    private final int ver;
    private final GenCfg gen;
    private final UpdCfg upd;
    private final SusCfg sus;
    private final ReactCfg react;
    private final TrapCfg trap;
    private final PunCfg pun;
    private final Map<String, WorldCfg> worlds;

    private Cfg(FileConfiguration raw, Logger log) {
        this.ver = raw.getInt("config-version", 1);
        this.gen = GenCfg.load(raw);
        this.upd = UpdCfg.load(raw);
        this.sus = SusCfg.load(raw);
        this.react = ReactCfg.load(raw);
        this.trap = TrapCfg.load(raw);
        this.pun = PunCfg.load(raw);
        this.worlds = parseWorlds(raw.getConfigurationSection("worlds"), log);
    }

    public static Cfg load(Plugin plug) {
        CfgMig.ensureUpdated(plug, "config.yml", Set.of("worlds"));
        plug.reloadConfig();
        return load(plug.getConfig(), plug.getLogger());
    }

    public static Cfg load(FileConfiguration raw, Logger log) {
        return new Cfg(raw, log);
    }

    public int ver() { return ver; }
    public GenCfg gen() { return gen; }
    public UpdCfg upd() { return upd; }
    public SusCfg sus() { return sus; }
    public ReactCfg react() { return react; }
    public TrapCfg trap() { return trap; }
    public PunCfg pun() { return pun; }

    public WorldCfg world(String wName) {
        return wName == null ? null : worlds.get(wName);
    }

    private static Map<String, WorldCfg> parseWorlds(ConfigurationSection sec, Logger log) {
        if (sec == null) {
            return Collections.emptyMap();
        }
        Map<String, WorldCfg> res = new HashMap<>();
        for (String wName : sec.getKeys(false)) {
            ConfigurationSection ws = sec.getConfigurationSection(wName);
            if (ws == null) {
                continue;
            }
            String ctx = "worlds." + wName;
            Set<Material> repl = MatParse.parseSet(ws.getStringList("replaceable-blocks"), ctx, log);
            Set<Material> tracked = MatParse.parseSet(ws.getStringList("tracked-ores"), ctx, log);
            List<Material> above = MatParse.parseList(ws.getStringList("phantom-ores.above-threshold"), ctx, log);
            List<Material> below = MatParse.parseList(ws.getStringList("phantom-ores.below-threshold"), ctx, log);

            res.put(wName, new WorldCfg(
                    wName,
                    ws.getBoolean("enabled", true),
                    ws.getInt("max-y", 16),
                    ws.getInt("min-y", -60),
                    repl,
                    tracked,
                    ws.getInt("phantom-ores.deepslate-y-threshold", 0),
                    above,
                    below
            ));
        }
        return Collections.unmodifiableMap(res);
    }
}
