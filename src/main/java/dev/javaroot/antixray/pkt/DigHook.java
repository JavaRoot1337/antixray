package dev.javaroot.antixray.pkt;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.list.SessList;
import dev.javaroot.antixray.mdl.Session;
import dev.javaroot.antixray.mdl.Vein;
import dev.javaroot.antixray.pun.Punish;
import dev.javaroot.antixray.sched.Exec;
import dev.javaroot.antixray.stor.HideMap;
import dev.javaroot.antixray.stor.RevealList;
import dev.javaroot.antixray.stor.StorCfg;
import dev.javaroot.antixray.trap.TrapMgr;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

public final class DigHook extends PacketListenerAbstract {

    private final Supplier<Cfg> cfgSupplier;
    private final Supplier<StorCfg> storCfgSupplier;
    private final SessList sessList;
    private final TrapMgr trapMgr;
    private final Punish punish;
    private final Exec exec;
    private final HideMap hideMap;
    private final RevealList revealList;

    public DigHook(
            Supplier<Cfg> cfgSupplier,
            Supplier<StorCfg> storCfgSupplier,
            SessList sessList,
            TrapMgr trapMgr,
            Punish punish,
            Exec exec,
            HideMap hideMap,
            RevealList revealList
    ) {
        super(PacketListenerPriority.NORMAL);
        this.cfgSupplier = cfgSupplier;
        this.storCfgSupplier = storCfgSupplier;
        this.sessList = sessList;
        this.trapMgr = trapMgr;
        this.punish = punish;
        this.exec = exec;
        this.hideMap = hideMap;
        this.revealList = revealList;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) {
            return;
        }
        Object rawPlayer = event.getPlayer();
        if (!(rawPlayer instanceof Player player)) {
            return;
        }

        StorCfg storCfg = storCfgSupplier.get();
        boolean checkTrap = trapMgr.activeCount(player.getUniqueId()) > 0;
        boolean checkReveal = storCfg.enabled && storCfg.reveal.onStartDig();
        if (!checkTrap && !checkReveal) {
            return;
        }

        WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
        DiggingAction action = wrapper.getAction();
        World playerWorld = player.getWorld();
        Session session = sessList.get(player.getUniqueId());
        String worldName = playerWorld != null
                ? playerWorld.getName()
                : (session != null ? session.currentWorld() : null);
        if (worldName == null) {
            return;
        }

        Vector3i pos = wrapper.getBlockPosition();
        if (checkReveal && action == DiggingAction.START_DIGGING
                && hideMap.hasHiddenNear(worldName, pos.x, pos.y, pos.z)) {
            World world = playerWorld != null ? playerWorld : Bukkit.getWorld(worldName);
            if (world != null) {
                int radius = storCfg.reveal.radius();
                exec.runRegion(world, pos.x >> 4, pos.z >> 4,
                        () -> revealList.revealAround(world, pos.x, pos.y, pos.z, radius));
            }
        }

        if (!checkTrap) {
            return;
        }
        Cfg cfg = cfgSupplier.get();
        boolean match = cfg.detectMode == Cfg.DetectMode.ON_START_DIG
                ? action == DiggingAction.START_DIGGING
                : action == DiggingAction.FINISHED_DIGGING;
        if (!match) {
            return;
        }

        Vein vein = trapMgr.findVeinAt(player.getUniqueId(), worldName, pos.x, pos.y, pos.z);
        if (vein == null) {
            return;
        }

        if (cfg.cancelDigPacket) {
            event.setCancelled(true);
        }
        if (vein.markTriggered()) {
            trapMgr.removeVein(vein, cfg.restoreOnHit);
            exec.runEntity(player, () -> punish.handleTrapHit(player, vein, pos.x, pos.y, pos.z));
        }
    }
}
