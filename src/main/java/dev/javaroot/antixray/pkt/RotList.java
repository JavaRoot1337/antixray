package dev.javaroot.antixray.pkt;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPosition;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation;
import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.WorldCfg;
import dev.javaroot.antixray.cfg.sec.ReactCfg;
import dev.javaroot.antixray.det.list.BreakList;
import dev.javaroot.antixray.sess.Sess;
import dev.javaroot.antixray.sess.SessList;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

public final class RotList extends PacketListenerAbstract {

    private final Supplier<Cfg> cfgSup;
    private final SessList sessList;

    public RotList(Supplier<Cfg> cfgSup, SessList sessList) {
        super(PacketListenerPriority.MONITOR);
        this.cfgSup = cfgSup;
        this.sessList = sessList;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent e) {
        PacketTypeCommon type = e.getPacketType();
        if (type != PacketType.Play.Client.PLAYER_ROTATION
                && type != PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION
                && type != PacketType.Play.Client.PLAYER_POSITION) {
            return;
        }

        Cfg cfg = cfgSup.get();
        ReactCfg rc = cfg.react();
        if (!rc.enabled() || !(e.getPlayer() instanceof Player p)) {
            return;
        }

        Sess sess = sessList.get(p.getUniqueId());
        if (sess == null || sess.curWorld() == null) {
            return;
        }
        WorldCfg wCfg = cfg.world(sess.curWorld());
        if (wCfg == null || !wCfg.enabled()) {
            return;
        }
        if (cfg.gen().bypassPerm() && p.hasPermission(BreakList.PERM_BYPASS)) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (type == PacketType.Play.Client.PLAYER_ROTATION) {
            WrapperPlayClientPlayerRotation pkt = new WrapperPlayClientPlayerRotation(e);
            sess.rot().recRot(pkt.getYaw(), pkt.getPitch(), nowMs,
                    rc.bufSize(), rc.minTurnDeg(), rc.turnWindowMs(), rc.stabDegPerSec());
        } else if (type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            Location loc = new WrapperPlayClientPlayerPositionAndRotation(e).getLocation();
            sess.rot().recPosRot(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), nowMs,
                    rc.bufSize(), rc.minTurnDeg(), rc.turnWindowMs(), rc.stabDegPerSec());
        } else {
            Vector3d pos = new WrapperPlayClientPlayerPosition(e).getPosition();
            sess.rot().recPos(pos.getX(), pos.getY(), pos.getZ(), nowMs,
                    rc.bufSize(), rc.minTurnDeg(), rc.turnWindowMs(), rc.stabDegPerSec());
        }
    }
}
