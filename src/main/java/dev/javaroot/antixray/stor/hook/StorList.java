package dev.javaroot.antixray.stor.hook;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk;
import dev.javaroot.antixray.sess.Sess;
import dev.javaroot.antixray.sess.SessList;
import dev.javaroot.antixray.stor.cfg.StorCfg;
import dev.javaroot.antixray.stor.mask.ChunkMask;
import dev.javaroot.antixray.stor.mask.HideMap;
import dev.javaroot.antixray.stor.mask.OccTbl;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Supplier;

public final class StorList extends PacketListenerAbstract {

    public static final String PERM_BYPASS = "antixray.storage.bypass";
    public static final String PERM_VIEW = "antixray.storage.view";

    private final Supplier<StorCfg> cfgSup;
    private final SessList sessList;
    private final OccTbl occTbl;
    private final HideMap hideMap;
    private final RevealList revealList;

    public StorList(
            Supplier<StorCfg> cfgSup,
            SessList sessList,
            OccTbl occTbl,
            HideMap hideMap,
            RevealList revealList
    ) {
        super(PacketListenerPriority.NORMAL);
        this.cfgSup = cfgSup;
        this.sessList = sessList;
        this.occTbl = occTbl;
        this.hideMap = hideMap;
        this.revealList = revealList;
    }

    @Override
    public void onPacketSend(PacketSendEvent e) {
        StorCfg cfg = cfgSup.get();
        if (!cfg.enabled() || !(e.getPlayer() instanceof Player p)) {
            return;
        }

        var type = e.getPacketType();
        if (type == PacketType.Play.Server.CHUNK_DATA && cfg.pkt().chunkData()) {
            onChunkData(e, p, cfg);
        } else if (type == PacketType.Play.Server.BLOCK_ENTITY_DATA && cfg.pkt().blockEnt()) {
            onBlockEnt(e, p);
        } else if (type == PacketType.Play.Server.BLOCK_ACTION && cfg.pkt().blockAct()) {
            onBlockAct(e, p);
        } else if (type == PacketType.Play.Server.BLOCK_CHANGE && cfg.pkt().blockChg()) {
            onBlockChg(e, p);
        } else if (type == PacketType.Play.Server.MULTI_BLOCK_CHANGE && cfg.pkt().multiChg()) {
            onMultiChg(e, p);
        } else if (type == PacketType.Play.Server.UNLOAD_CHUNK && cfg.pkt().unload()) {
            onUnload(e, p);
        }
    }

    private void onChunkData(PacketSendEvent e, Player p, StorCfg cfg) {
        if (bypass(p, cfg.bypass())) {
            return;
        }
        String wName = resolveWorld(p);
        StorCfg.WorldRule rule = cfg.world(wName);
        if (rule == null || !rule.enabled()) {
            return;
        }

        WrapperPlayServerChunkData pkt = new WrapperPlayServerChunkData(e);
        Column col = pkt.getColumn();
        if (col == null || !col.isFullChunk()) {
            return;
        }

        ClientVersion ver = e.getUser().getClientVersion();
        OccTbl.Palette pal = occTbl.palette(ver);
        int minY = revealList.minY(wName, rule.minY());
        int maxY = revealList.maxYExcl(wName, rule.maxY() + 1);

        ChunkMask.Res res = ChunkMask.mask(col, ver, wName, cfg, rule, pal, minY, maxY);
        if (res == null) {
            hideMap.clearChunk(p.getUniqueId(), wName, col.getX(), col.getZ());
            return;
        }

        pkt.setColumn(res.col());
        e.markForReEncode(true);
        hideMap.replaceChunk(p.getUniqueId(), wName, col.getX(), col.getZ(), res.hidden());
    }

    private void onBlockEnt(PacketSendEvent e, Player p) {
        String wName = resolveWorld(p);
        if (wName == null) {
            return;
        }
        Vector3i pos = new WrapperPlayServerBlockEntityData(e).getPosition();
        if (hideMap.isHidden(p.getUniqueId(), wName, pos.x, pos.y, pos.z)) {
            e.setCancelled(true);
        }
    }

    private void onBlockAct(PacketSendEvent e, Player p) {
        String wName = resolveWorld(p);
        if (wName == null) {
            return;
        }
        Vector3i pos = new WrapperPlayServerBlockAction(e).getBlockPosition();
        if (hideMap.isHidden(p.getUniqueId(), wName, pos.x, pos.y, pos.z)) {
            e.setCancelled(true);
        }
    }

    private void onBlockChg(PacketSendEvent e, Player p) {
        String wName = resolveWorld(p);
        if (wName == null) {
            return;
        }
        WrapperPlayServerBlockChange pkt = new WrapperPlayServerBlockChange(e);
        Vector3i pos = pkt.getBlockPosition();
        UUID id = p.getUniqueId();
        if (!hideMap.isHidden(id, wName, pos.x, pos.y, pos.z)) {
            return;
        }

        OccTbl.Palette pal = occTbl.palette(e.getUser().getClientVersion());
        if (pal.isTarget(wName, pkt.getBlockId())) {
            e.setCancelled(true);
        } else {
            hideMap.removeEntry(id, wName, pos.x, pos.y, pos.z);
        }
    }

    private void onMultiChg(PacketSendEvent e, Player p) {
        String wName = resolveWorld(p);
        if (wName == null) {
            return;
        }
        WrapperPlayServerMultiBlockChange pkt = new WrapperPlayServerMultiBlockChange(e);
        Vector3i secPos = pkt.getChunkPosition();
        UUID id = p.getUniqueId();
        if (!hideMap.hasInChunk(id, wName, secPos.x, secPos.z)) {
            return;
        }

        OccTbl.Palette pal = occTbl.palette(e.getUser().getClientVersion());
        boolean mod = false;
        for (WrapperPlayServerMultiBlockChange.EncodedBlock b : pkt.getBlocks()) {
            Integer mId = hideMap.maskId(id, wName, b.getX(), b.getY(), b.getZ());
            if (mId == null) {
                continue;
            }
            if (pal.isTarget(wName, b.getBlockId())) {
                b.setBlockId(mId);
                mod = true;
            } else {
                hideMap.removeEntry(id, wName, b.getX(), b.getY(), b.getZ());
            }
        }
        if (mod) {
            e.markForReEncode(true);
        }
    }

    private void onUnload(PacketSendEvent e, Player p) {
        String wName = resolveWorld(p);
        if (wName == null) {
            return;
        }
        WrapperPlayServerUnloadChunk pkt = new WrapperPlayServerUnloadChunk(e);
        hideMap.clearChunk(p.getUniqueId(), wName, pkt.getChunkX(), pkt.getChunkZ());
    }

    private String resolveWorld(Player p) {
        // Read world reference directly first so dimension switches don't lag behind PlayerChangedWorldEvent
        World w = p.getWorld();
        if (w != null) {
            return w.getName();
        }
        Sess sess = sessList.get(p.getUniqueId());
        return sess != null ? sess.curWorld() : null;
    }

    private boolean bypass(Player p, StorCfg.BypassCfg bp) {
        if (bp.perm() && p.hasPermission(PERM_BYPASS)) {
            return true;
        }
        Sess sess = sessList.get(p.getUniqueId());
        GameMode mode = sess != null ? sess.gameMode() : p.getGameMode();
        if (bp.creative() && mode == GameMode.CREATIVE) {
            return true;
        }
        if (mode == GameMode.SPECTATOR) {
            if (bp.spec()) {
                return true;
            }
            return bp.specMod() && p.hasPermission(PERM_VIEW);
        }
        return false;
    }
}
