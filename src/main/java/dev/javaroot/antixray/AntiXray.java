package dev.javaroot.antixray;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.Msg;
import dev.javaroot.antixray.cmd.AxrCmd;
import dev.javaroot.antixray.db.Db;
import dev.javaroot.antixray.db.LogRepo;
import dev.javaroot.antixray.det.Decay;
import dev.javaroot.antixray.det.SusEngine;
import dev.javaroot.antixray.list.BreakList;
import dev.javaroot.antixray.list.SessList;
import dev.javaroot.antixray.list.WorldList;
import dev.javaroot.antixray.pkt.PktHook;
import dev.javaroot.antixray.pun.Punish;
import dev.javaroot.antixray.sched.Exec;
import dev.javaroot.antixray.stor.HideMap;
import dev.javaroot.antixray.stor.OccTbl;
import dev.javaroot.antixray.stor.RevealList;
import dev.javaroot.antixray.stor.StorCfg;
import dev.javaroot.antixray.trap.TrapMgr;
import dev.javaroot.antixray.trap.TrapSpawn;
import dev.javaroot.antixray.update.UpdChecker;
import dev.javaroot.antixray.update.UpdList;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;

public final class AntiXray extends JavaPlugin {

    private volatile Cfg cfg;
    private volatile Msg msg;
    private volatile StorCfg storCfg;

    private Exec exec;
    private Db db;
    private SessList sessList;
    private TrapMgr trapMgr;
    private Decay decay;
    private PktHook pktHook;
    private OccTbl occTbl;
    private HideMap hideMap;
    private UpdChecker updChecker;

    @Override
    public void onEnable() {
        this.cfg = Cfg.load(this);
        this.msg = Msg.load(this);
        this.storCfg = StorCfg.load(this);
        this.exec = new Exec(this);

        File dbFile = new File(getDataFolder(), cfg.databaseFile);
        this.db = new Db(dbFile);
        try {
            this.db.init();
        } catch (SQLException ex) {
            getLogger().log(Level.SEVERE, "Failed to initialize SQLite database", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        LogRepo logRepo = new LogRepo(db, getLogger());
        logRepo.pruneOldDetections(cfg.historyRetentionDays);

        this.trapMgr = new TrapMgr(() -> cfg, exec);
        TrapSpawn trapSpawn = new TrapSpawn(() -> cfg, trapMgr);
        this.sessList = new SessList(logRepo, trapMgr);
        Punish punish = new Punish(() -> cfg, () -> msg, sessList, trapMgr, logRepo, exec, getLogger());
        SusEngine susEngine = new SusEngine(() -> cfg, trapSpawn);

        this.occTbl = new OccTbl(storCfg);
        this.hideMap = new HideMap();
        RevealList revealList = new RevealList(() -> storCfg, hideMap, exec);

        this.decay = new Decay(() -> cfg, sessList::all, trapMgr, exec);
        this.pktHook = new PktHook(() -> cfg, () -> storCfg, sessList, trapMgr, punish, exec, occTbl, hideMap, revealList);
        this.updChecker = new UpdChecker(this, () -> cfg);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(sessList, this);
        pm.registerEvents(new BreakList(() -> cfg, sessList, susEngine), this);
        pm.registerEvents(new WorldList(() -> cfg, trapMgr), this);
        pm.registerEvents(revealList, this);
        pm.registerEvents(new UpdList(this, () -> cfg, updChecker), this);

        this.pktHook.register();

        AxrCmd axrCmd = new AxrCmd(
                () -> cfg,
                () -> msg,
                this::reloadConfigs,
                sessList,
                trapMgr,
                trapSpawn,
                logRepo,
                exec,
                updChecker,
                getFile(),
                getServer().getUpdateFolderFile());
        getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                event -> event.registrar().register(
                        "ax",
                        "Команда AntiXray",
                        List.of("antixray", "axr"),
                        axrCmd));

        this.decay.start();
        this.updChecker.start();
    }

    public void reloadConfigs() {
        this.cfg = Cfg.load(this);
        this.msg = Msg.load(this);
        StorCfg loadedStor = StorCfg.load(this);
        this.storCfg = loadedStor;
        if (occTbl != null) {
            occTbl.reload(loadedStor);
        }
        if (hideMap != null && !loadedStor.enabled) {
            hideMap.clearAll();
        }
    }

    @Override
    public void onDisable() {
        if (updChecker != null) {
            updChecker.stop();
        }
        if (decay != null) {
            decay.stop();
        }
        if (pktHook != null) {
            pktHook.unregister();
        }
        if (hideMap != null) {
            hideMap.clearAll();
        }
        if (trapMgr != null) {
            trapMgr.clearAll(false);
        }
        if (sessList != null) {
            sessList.saveAllSync();
        }
        if (exec != null) {
            exec.cancelAll();
        }
        if (db != null) {
            db.close();
        }
    }
}
