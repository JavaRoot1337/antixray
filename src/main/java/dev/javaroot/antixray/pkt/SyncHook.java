package dev.javaroot.antixray.pkt;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import dev.javaroot.antixray.list.SessList;
import dev.javaroot.antixray.mdl.Session;
import dev.javaroot.antixray.mdl.Vein;
import dev.javaroot.antixray.trap.TrapMgr;
import org.bukkit.entity.Player;

public final class SyncHook extends PacketListenerAbstract {

    private final SessList sessList;
    private final TrapMgr trapMgr;

    public SyncHook(SessList sessList, TrapMgr trapMgr) {
        super(PacketListenerPriority.MONITOR);
        this.sessList = sessList;
        this.trapMgr = trapMgr;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Object rawPlayer = event.getPlayer();
        if (!(rawPlayer instanceof Player player)) {
            return;
        }
        if (trapMgr.activeCount(player.getUniqueId()) == 0) {
            return;
        }
        Session session = sessList.get(player.getUniqueId());
        String worldName = session != null ? session.currentWorld() : null;
        if (worldName == null) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            WrapperPlayServerBlockChange pkt = new WrapperPlayServerBlockChange(event);
            Vector3i pos = pkt.getBlockPosition();
            Vein vein = trapMgr.findVeinAt(player.getUniqueId(), worldName, pos.x, pos.y, pos.z);
            if (vein == null) {
                return;
            }
            Vein.BlockEntry entry = vein.entryAt(pos.x, pos.y, pos.z);
            if (entry != null && pkt.getBlockId() == entry.phantomState().getGlobalId()) {
                return;
            }
            trapMgr.removeVein(vein, false);
        } else if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            WrapperPlayServerChunkData pkt = new WrapperPlayServerChunkData(event);
            Column col = pkt.getColumn();
            if (col != null) {
                trapMgr.handleChunkDesync(player.getUniqueId(), worldName, col.getX(), col.getZ());
            }
        }
    }
}
