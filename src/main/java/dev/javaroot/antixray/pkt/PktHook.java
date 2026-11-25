package dev.javaroot.antixray.pkt;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.plat.sched.Exec;
import dev.javaroot.antixray.pun.PunSvc;
import dev.javaroot.antixray.sess.SessList;
import dev.javaroot.antixray.stor.cfg.StorCfg;
import dev.javaroot.antixray.stor.hook.RevealList;
import dev.javaroot.antixray.stor.hook.StorList;
import dev.javaroot.antixray.stor.mask.HideMap;
import dev.javaroot.antixray.stor.mask.OccTbl;
import dev.javaroot.antixray.trap.TrapMgr;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class PktHook {

    private final List<PacketListenerCommon> lists;
    private final List<PacketListenerCommon> reg = new ArrayList<>(4);

    public PktHook(
            Supplier<Cfg> cfgSup,
            Supplier<StorCfg> storCfgSup,
            SessList sessList,
            TrapMgr trapMgr,
            PunSvc punSvc,
            Exec exec,
            OccTbl occTbl,
            HideMap hideMap,
            RevealList revealList
    ) {
        this.lists = List.of(
                new DigList(cfgSup, storCfgSup, sessList, trapMgr, punSvc, exec, hideMap, revealList),
                new SyncList(sessList, trapMgr),
                new StorList(storCfgSup, sessList, occTbl, hideMap, revealList),
                new RotList(cfgSup, sessList)
        );
    }

    public void register() {
        var em = PacketEvents.getAPI().getEventManager();
        for (PacketListenerCommon list : lists) {
            reg.add(em.registerListener(list));
        }
    }

    public void unregister() {
        var em = PacketEvents.getAPI().getEventManager();
        for (PacketListenerCommon list : reg) {
            em.unregisterListener(list);
        }
        reg.clear();
    }
}
