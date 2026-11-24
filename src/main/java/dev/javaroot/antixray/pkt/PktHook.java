package dev.javaroot.antixray.pkt;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.list.SessList;
import dev.javaroot.antixray.pun.Punish;
import dev.javaroot.antixray.sched.Exec;
import dev.javaroot.antixray.stor.HideMap;
import dev.javaroot.antixray.stor.OccTbl;
import dev.javaroot.antixray.stor.RevealList;
import dev.javaroot.antixray.stor.StorCfg;
import dev.javaroot.antixray.stor.StorHook;
import dev.javaroot.antixray.trap.TrapMgr;

import java.util.function.Supplier;

public final class PktHook {

    private final DigHook digHook;
    private final SyncHook syncHook;
    private final StorHook storHook;
    private PacketListenerCommon registeredDig;
    private PacketListenerCommon registeredSync;
    private PacketListenerCommon registeredStor;

    public PktHook(
            Supplier<Cfg> cfgSupplier,
            Supplier<StorCfg> storCfgSupplier,
            SessList sessList,
            TrapMgr trapMgr,
            Punish punish,
            Exec exec,
            OccTbl occTbl,
            HideMap hideMap,
            RevealList revealList
    ) {
        this.digHook = new DigHook(cfgSupplier, storCfgSupplier, sessList, trapMgr, punish, exec, hideMap, revealList);
        this.syncHook = new SyncHook(sessList, trapMgr);
        this.storHook = new StorHook(storCfgSupplier, sessList, occTbl, hideMap, revealList);
    }

    public void register() {
        var eventManager = PacketEvents.getAPI().getEventManager();
        this.registeredDig = eventManager.registerListener(digHook);
        this.registeredSync = eventManager.registerListener(syncHook);
        this.registeredStor = eventManager.registerListener(storHook);
    }

    public void unregister() {
        var eventManager = PacketEvents.getAPI().getEventManager();
        if (registeredDig != null) {
            eventManager.unregisterListener(registeredDig);
            registeredDig = null;
        }
        if (registeredSync != null) {
            eventManager.unregisterListener(registeredSync);
            registeredSync = null;
        }
        if (registeredStor != null) {
            eventManager.unregisterListener(registeredStor);
            registeredStor = null;
        }
    }
}
