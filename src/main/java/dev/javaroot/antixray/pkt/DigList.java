package dev.javaroot.antixray.pkt;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.sec.TrapCfg;
import dev.javaroot.antixray.plat.sched.Exec;
import dev.javaroot.antixray.pun.PunSvc;
import dev.javaroot.antixray.sess.Sess;
import dev.javaroot.antixray.sess.SessList;
import dev.javaroot.antixray.stor.cfg.StorCfg;
import dev.javaroot.antixray.stor.hook.RevealList;
import dev.javaroot.antixray.stor.mask.HideMap;
import dev.javaroot.antixray.trap.TrapMgr;
import dev.javaroot.antixray.trap.mdl.Vein;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

public final class DigList extends PacketListenerAbstract {

    private final Supplier<Cfg> cfgSup;
    private final Supplier<StorCfg> storCfgSup;
    private final SessList sessList;
    private final TrapMgr trapMgr;
    private final PunSvc punSvc;
    private final Exec exec;
    private final HideMap hideMap;
    private final RevealList revealList;

    public DigList(
            Supplier<Cfg> cfgSup,
            Supplier<StorCfg> storCfgSup,
            SessList sessList,
            TrapMgr trapMgr,
            PunSvc punSvc,
            Exec exec,
            HideMap hideMap,
            RevealList revealList
    ) {
        super(PacketListenerPriority.NORMAL);
        this.cfgSup = cfgSup;
        this.storCfgSup = storCfgSup;
        this.sessList = sessList;
        this.trapMgr = trapMgr;
        this.punSvc = punSvc;
        this.exec = exec;
        this.hideMap = hideMap;
        this.revealList = revealList;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent e) {
        if (e.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING || !(e.getPlayer() instanceof Player p)) {
            return;
        }

        StorCfg storCfg = storCfgSup.get();
        boolean chkTrap = trapMgr.activeCount(p.getUniqueId()) > 0;
        boolean chkReveal = storCfg.enabled() && storCfg.reveal().startDig();
        if (!chkTrap && !chkReveal) {
            return;
        }

        WrapperPlayClientPlayerDigging pkt = new WrapperPlayClientPlayerDigging(e);
        DiggingAction act = pkt.getAction();
        Sess sess = sessList.get(p.getUniqueId());
        String wName = sess != null && sess.curWorld() != null
                ? sess.curWorld()
                : (p.getWorld() != null ? p.getWorld().getName() : null);
        if (wName == null) {
            return;
        }

        Vector3i pos = pkt.getBlockPosition();
        if (chkReveal && act == DiggingAction.START_DIGGING && hideMap.hasNear(wName, pos.x, pos.y, pos.z)) {
            World w = Bukkit.getWorld(wName);
            if (w != null) {
                int r = storCfg.reveal().radius();
                exec.runRegion(w, pos.x >> 4, pos.z >> 4, () -> revealList.revealAround(w, pos.x, pos.y, pos.z, r));
            }
        }

        if (!chkTrap) {
            return;
        }
        TrapCfg tc = cfgSup.get().trap();
        boolean match = tc.detectMode() == TrapCfg.DetectMode.ON_START_DIG
                ? act == DiggingAction.START_DIGGING
                : act == DiggingAction.FINISHED_DIGGING;
        if (!match) {
            return;
        }

        Vein vein = trapMgr.findVeinAt(p.getUniqueId(), wName, pos.x, pos.y, pos.z);
        if (vein == null) {
            return;
        }
        if (tc.cancelDig()) {
            e.setCancelled(true);
        }
        if (vein.markTriggered()) {
            trapMgr.removeVein(vein, tc.restoreOnHit());
            exec.runEntity(p, () -> punSvc.onTrapHit(p, vein, pos.x, pos.y, pos.z));
        }
    }
}
