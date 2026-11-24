package dev.javaroot.antixray.stor;

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
import dev.javaroot.antixray.list.SessList;
import dev.javaroot.antixray.mdl.Session;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Supplier;

public final class StorHook extends PacketListenerAbstract {

    public static final String PERM_BYPASS = "antixray.storage.bypass";
    public static final String PERM_VIEW = "antixray.storage.view";

    private final Supplier<StorCfg> cfgSupplier;
    private final SessList sessList;
    private final OccTbl occTbl;
    private final HideMap hideMap;
    private final RevealList revealList;

    public StorHook(
            Supplier<StorCfg> cfgSupplier,
            SessList sessList,
            OccTbl occTbl,
            HideMap hideMap,
            RevealList revealList
    ) {
        super(PacketListenerPriority.NORMAL);
        this.cfgSupplier = cfgSupplier;
        this.sessList = sessList;
        this.occTbl = occTbl;
        this.hideMap = hideMap;
        this.revealList = revealList;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        StorCfg cfg = cfgSupplier.get();
        if (!cfg.enabled) {
            return;
        }
        Object rawPlayer = event.getPlayer();
        if (!(rawPlayer instanceof Player player)) {
            return;
        }

        var type = event.getPacketType();
        if (type == PacketType.Play.Server.CHUNK_DATA) {
            if (cfg.packets.filterChunkData()) {
                handleChunkData(event, player, cfg);
            }
        } else if (type == PacketType.Play.Server.BLOCK_ENTITY_DATA) {
            if (cfg.packets.blockEntityData()) {
                handleBlockEntityData(event, player);
            }
        } else if (type == PacketType.Play.Server.BLOCK_ACTION) {
            if (cfg.packets.blockAction()) {
                handleBlockAction(event, player);
            }
        } else if (type == PacketType.Play.Server.BLOCK_CHANGE) {
            if (cfg.packets.filterBlockChange()) {
                handleBlockChange(event, player);
            }
        } else if (type == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            if (cfg.packets.filterMultiBlockChange()) {
                handleMultiBlockChange(event, player);
            }
        } else if (type == PacketType.Play.Server.UNLOAD_CHUNK) {
            if (cfg.packets.clearOnUnloadChunk()) {
                handleUnloadChunk(event, player);
            }
        }
    }

    private void handleChunkData(PacketSendEvent event, Player player, StorCfg cfg) {
        if (shouldBypass(player, cfg.bypass)) {
            return;
        }
        String worldName = resolveWorld(player);
        StorCfg.WorldRule rule = cfg.world(worldName);
        if (rule == null || !rule.enabled()) {
            return;
        }

        WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
        Column column = wrapper.getColumn();
        if (column == null || !column.isFullChunk()) {
            return;
        }

        ClientVersion version = event.getUser().getClientVersion();
        OccTbl.Palette palette = occTbl.palette(version);
        int minY = revealList.worldMinY(worldName, rule.minY());
        int maxY = revealList.worldMaxYExclusive(worldName, rule.maxY() + 1);

        ChunkMask.Result masked = ChunkMask.mask(column, version, worldName, cfg, rule, palette, minY, maxY);
        if (masked == null) {
            hideMap.clearChunk(player.getUniqueId(), worldName, column.getX(), column.getZ());
            return;
        }

        wrapper.setColumn(masked.column());
        event.markForReEncode(true);
        hideMap.replaceChunk(player.getUniqueId(), worldName, column.getX(), column.getZ(), masked.hidden());
    }

    private void handleBlockEntityData(PacketSendEvent event, Player player) {
        String worldName = resolveWorld(player);
        if (worldName == null) {
            return;
        }
        WrapperPlayServerBlockEntityData pkt = new WrapperPlayServerBlockEntityData(event);
        Vector3i pos = pkt.getPosition();
        if (hideMap.isHidden(player.getUniqueId(), worldName, pos.x, pos.y, pos.z)) {
            event.setCancelled(true);
        }
    }

    private void handleBlockAction(PacketSendEvent event, Player player) {
        String worldName = resolveWorld(player);
        if (worldName == null) {
            return;
        }
        WrapperPlayServerBlockAction pkt = new WrapperPlayServerBlockAction(event);
        Vector3i pos = pkt.getBlockPosition();
        if (hideMap.isHidden(player.getUniqueId(), worldName, pos.x, pos.y, pos.z)) {
            event.setCancelled(true);
        }
    }

    private void handleBlockChange(PacketSendEvent event, Player player) {
        String worldName = resolveWorld(player);
        if (worldName == null) {
            return;
        }
        WrapperPlayServerBlockChange pkt = new WrapperPlayServerBlockChange(event);
        Vector3i pos = pkt.getBlockPosition();
        UUID uuid = player.getUniqueId();
        if (!hideMap.isHidden(uuid, worldName, pos.x, pos.y, pos.z)) {
            return;
        }

        OccTbl.Palette palette = occTbl.palette(event.getUser().getClientVersion());
        if (palette.isTarget(worldName, pkt.getBlockId())) {
            event.setCancelled(true);
        } else {
            hideMap.removeEntry(uuid, worldName, pos.x, pos.y, pos.z);
        }
    }

    private void handleMultiBlockChange(PacketSendEvent event, Player player) {
        String worldName = resolveWorld(player);
        if (worldName == null) {
            return;
        }
        WrapperPlayServerMultiBlockChange pkt = new WrapperPlayServerMultiBlockChange(event);
        Vector3i secPos = pkt.getChunkPosition();
        UUID uuid = player.getUniqueId();
        if (!hideMap.hasHiddenInChunk(uuid, worldName, secPos.x, secPos.z)) {
            return;
        }

        OccTbl.Palette palette = occTbl.palette(event.getUser().getClientVersion());
        boolean modified = false;
        for (WrapperPlayServerMultiBlockChange.EncodedBlock block : pkt.getBlocks()) {
            Integer maskId = hideMap.maskId(uuid, worldName, block.getX(), block.getY(), block.getZ());
            if (maskId == null) {
                continue;
            }
            if (palette.isTarget(worldName, block.getBlockId())) {
                block.setBlockId(maskId);
                modified = true;
            } else {
                hideMap.removeEntry(uuid, worldName, block.getX(), block.getY(), block.getZ());
            }
        }
        if (modified) {
            event.markForReEncode(true);
        }
    }

    private void handleUnloadChunk(PacketSendEvent event, Player player) {
        String worldName = resolveWorld(player);
        if (worldName == null) {
            return;
        }
        WrapperPlayServerUnloadChunk pkt = new WrapperPlayServerUnloadChunk(event);
        hideMap.clearChunk(player.getUniqueId(), worldName, pkt.getChunkX(), pkt.getChunkZ());
    }

    private String resolveWorld(Player player) {
        // Read world reference directly first so dimension switches don't lag behind PlayerChangedWorldEvent
        World world = player.getWorld();
        if (world != null) {
            return world.getName();
        }
        Session session = sessList.get(player.getUniqueId());
        return session != null ? session.currentWorld() : null;
    }

    private static boolean shouldBypass(Player player, StorCfg.BypassCfg bypass) {
        if (bypass.permissionEnabled() && player.hasPermission(PERM_BYPASS)) {
            return true;
        }
        GameMode mode = player.getGameMode();
        if (bypass.ignoreCreative() && mode == GameMode.CREATIVE) {
            return true;
        }
        if (mode == GameMode.SPECTATOR) {
            if (bypass.ignoreSpectator()) {
                return true;
            }
            return bypass.ignoreSpectatorModerators() && player.hasPermission(PERM_VIEW);
        }
        return false;
    }
}
