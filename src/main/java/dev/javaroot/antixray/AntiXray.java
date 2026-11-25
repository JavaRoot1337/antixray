package dev.javaroot.antixray;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cmd.AxrCmd;
import dev.javaroot.antixray.cmd.sub.AdminSub;
import dev.javaroot.antixray.cmd.sub.InspSub;
import dev.javaroot.antixray.db.Db;
import dev.javaroot.antixray.db.DetRepo;
import dev.javaroot.antixray.det.Decay;
import dev.javaroot.antixray.det.SusEng;
import dev.javaroot.antixray.det.list.BreakList;
import dev.javaroot.antixray.pkt.PktHook;
import dev.javaroot.antixray.plat.sched.Exec;
import dev.javaroot.antixray.plat.txt.Msg;
import dev.javaroot.antixray.pun.PunSvc;
import dev.javaroot.antixray.sess.SessList;
import dev.javaroot.antixray.stor.cfg.StorCfg;
import dev.javaroot.antixray.stor.hook.RevealList;
import dev.javaroot.antixray.stor.mask.HideMap;
import dev.javaroot.antixray.stor.mask.OccTbl;
import dev.javaroot.antixray.trap.TrapMgr;
import dev.javaroot.antixray.trap.TrapSpawn;
import dev.javaroot.antixray.trap.list.WorldList;
import dev.javaroot.antixray.upd.UpdList;
import dev.javaroot.antixray.upd.UpdSvc;
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
    private UpdSvc updSvc;

    @Override
    public void onEnable() {
        this.cfg = Cfg.load(this);
        this.msg = Msg.load(this);
        this.storCfg = StorCfg.load(this);
        this.exec = new Exec(this);

        File dbFile = new File(getDataFolder(), cfg.gen().dbFile());
        this.db = new Db(dbFile, getLogger());
        try {
            this.db.init();
        } catch (SQLException ex) {
            getLogger().log(Level.SEVERE, "Failed to initialize SQLite database", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        DetRepo repo = new DetRepo(db, getLogger());
        repo.pruneOld(cfg.gen().retentionDays());

        this.trapMgr = new TrapMgr(() -> cfg, exec);
        TrapSpawn trapSpawn = new TrapSpawn(() -> cfg, trapMgr);
        this.sessList = new SessList(repo, trapMgr, getLogger());
        PunSvc punSvc = new PunSvc(() -> cfg, () -> msg, sessList, trapMgr, repo, exec, getLogger());
        SusEng susEng = new SusEng(() -> cfg, trapSpawn);

        this.occTbl = new OccTbl(storCfg);
        this.hideMap = new HideMap();
        RevealList revealList = new RevealList(() -> storCfg, hideMap, exec);

        this.decay = new Decay(() -> cfg, sessList::all, trapMgr, exec);
        this.pktHook = new PktHook(
                () -> cfg, () -> storCfg, sessList, trapMgr,
                punSvc, exec, occTbl, hideMap, revealList
        );
        this.updSvc = new UpdSvc(this, () -> cfg);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(sessList, this);
        pm.registerEvents(new BreakList(() -> cfg, sessList, susEng), this);
        pm.registerEvents(new WorldList(() -> cfg, trapMgr), this);
        pm.registerEvents(revealList, this);
        pm.registerEvents(new UpdList(() -> cfg, () -> msg, updSvc, exec), this);

        this.pktHook.register();

        AdminSub adminSub = new AdminSub(
                () -> msg, this::reloadConfigs, sessList, trapMgr,
                repo, exec, updSvc, getFile(), getServer().getUpdateFolderFile()
        );
        InspSub inspSub = new InspSub(
                () -> cfg, () -> msg, sessList, trapMgr, trapSpawn, repo, exec
        );
        AxrCmd cmd = new AxrCmd(() -> cfg, () -> msg, sessList, adminSub, inspSub);
        getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                e -> e.registrar().register("ax", "Команда AntiXray", List.of("antixray", "axr"), cmd)
        );

        this.decay.start();
        this.updSvc.start();
    }

    public void reloadConfigs() {
        this.cfg = Cfg.load(this);
        this.msg = Msg.load(this);
        StorCfg loadedStor = StorCfg.load(this);
        this.storCfg = loadedStor;
        if (occTbl != null) {
            occTbl.reload(loadedStor);
        }
        if (hideMap != null && !loadedStor.enabled()) {
            hideMap.clearAll();
        }
    }

    @Override
    public void onDisable() {
        if (updSvc != null) {
            updSvc.stop();
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
