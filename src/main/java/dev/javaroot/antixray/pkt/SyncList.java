package dev.javaroot.antixray.pkt;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import dev.javaroot.antixray.sess.Sess;
import dev.javaroot.antixray.sess.SessList;
import dev.javaroot.antixray.trap.TrapMgr;
import dev.javaroot.antixray.trap.mdl.Vein;
import org.bukkit.entity.Player;

public final class SyncList extends PacketListenerAbstract {

    private final SessList sessList;
    private final TrapMgr trapMgr;

    public SyncList(SessList sessList, TrapMgr trapMgr) {
        super(PacketListenerPriority.MONITOR);
        this.sessList = sessList;
        this.trapMgr = trapMgr;
    }

    @Override
    public void onPacketSend(PacketSendEvent e) {
        if (!(e.getPlayer() instanceof Player p) || trapMgr.activeCount(p.getUniqueId()) == 0) {
            return;
        }
        Sess sess = sessList.get(p.getUniqueId());
        String wName = sess != null ? sess.curWorld() : null;
        if (wName == null) {
            return;
        }

        if (e.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            WrapperPlayServerBlockChange pkt = new WrapperPlayServerBlockChange(e);
            Vector3i pos = pkt.getBlockPosition();
            Vein vein = trapMgr.findVeinAt(p.getUniqueId(), wName, pos.x, pos.y, pos.z);
            if (vein == null) {
                return;
            }
            Vein.BlockEntry entry = vein.entryAt(pos.x, pos.y, pos.z);
            if (entry != null && pkt.getBlockId() == entry.phantomState().getGlobalId()) {
                return;
            }
            trapMgr.removeVein(vein, false);
        } else if (e.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            Column col = new WrapperPlayServerChunkData(e).getColumn();
            if (col != null) {
                trapMgr.handleChunkDesync(p.getUniqueId(), wName, col.getX(), col.getZ());
            }
        }
    }
}
