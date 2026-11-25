package dev.javaroot.antixray.stor.mask;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.enums.Type;
import dev.javaroot.antixray.stor.cfg.StorCfg;

import java.util.ArrayList;
import java.util.List;

public final class ChunkMask {

    public record Res(Column col, List<HideMap.HiddenEntry> hidden) {
    }

    private ChunkMask() {
    }

    public static Res mask(
            Column col,
            ClientVersion ver,
            String wName,
            StorCfg cfg,
            StorCfg.WorldRule rule,
            OccTbl.Palette pal,
            int minY,
            int maxYExcl
    ) {
        TileEntity[] tiles = col.getTileEntities();
        BaseChunk[] secs = col.getChunks();
        if (tiles == null || tiles.length == 0 || secs == null || secs.length == 0) {
            return null;
        }

        boolean[] hideTile = new boolean[tiles.length];
        List<HideMap.HiddenEntry> hidden = null;
        int baseX = col.getX() << 4;
        int baseZ = col.getZ() << 4;
        boolean skipBorder = cfg.mask().skipBorder();
        boolean checkPartner = cfg.mask().bothHalves();

        for (int i = 0; i < tiles.length; i++) {
            TileEntity tile = tiles[i];
            if (tile == null) {
                continue;
            }
            byte packed = tile.getPackedByte();
            int lx = (packed >> 4) & 0xF;
            int lz = packed & 0xF;
            int y = tile.getYShort();

            if (!inBounds(lx, y, lz, skipBorder, rule, minY, maxYExcl)) {
                continue;
            }

            int blockId = blockAt(secs, minY, lx, y, lz);
            if (!pal.isTarget(wName, blockId)) {
                continue;
            }

            boolean buried = checkPartner && pal.isChest(blockId)
                    ? isChestBuried(secs, ver, pal, skipBorder, minY, maxYExcl, lx, y, lz, blockId)
                    : allSixOcc(secs, pal, minY, lx, y, lz, -1, -1);
            if (!buried) {
                continue;
            }

            hideTile[i] = true;
            if (hidden == null) {
                hidden = new ArrayList<>(4);
            }
            int maskId = pickRep(secs, pal, wName, rule, minY, lx, y, lz);
            hidden.add(new HideMap.HiddenEntry(baseX + lx, y, baseZ + lz, maskId));
        }

        if (hidden == null || hidden.isEmpty()) {
            return null;
        }

        for (HideMap.HiddenEntry e : hidden) {
            secs[(e.y() - minY) >> 4].set(e.x() & 15, e.y() & 15, e.z() & 15, e.maskId());
        }

        TileEntity[] filtered = new TileEntity[tiles.length - hidden.size()];
        int idx = 0;
        for (int i = 0; i < tiles.length; i++) {
            if (!hideTile[i]) {
                filtered[idx++] = tiles[i];
            }
        }

        return new Res(rebuildCol(col, filtered), hidden);
    }

    private static boolean inBounds(
            int lx, int y, int lz, boolean skipBorder, StorCfg.WorldRule rule, int minY, int maxYExcl
    ) {
        if (y <= minY || y >= maxYExcl - 1 || y < rule.minY() || y > rule.maxY()) {
            return false;
        }
        if (skipBorder && (lx <= 0 || lx >= 15 || lz <= 0 || lz >= 15)) {
            return false;
        }
        return lx >= 0 && lx <= 15 && lz >= 0 && lz <= 15;
    }

    private static boolean isChestBuried(
            BaseChunk[] secs, ClientVersion ver, OccTbl.Palette pal, boolean skipBorder,
            int minY, int maxYExcl, int lx, int y, int lz, int blockId
    ) {
        WrappedBlockState state = WrappedBlockState.getByGlobalId(ver, blockId, false);
        Type type = state.getTypeData();
        if (type != Type.LEFT && type != Type.RIGHT) {
            return allSixOcc(secs, pal, minY, lx, y, lz, -1, -1);
        }

        int[] off = ChestHelper.offset(state.getFacing(), type);
        if (off == null) {
            return false;
        }
        int plx = lx + off[0];
        int plz = lz + off[1];
        if (plx < 0 || plx > 15 || plz < 0 || plz > 15) {
            return false;
        }
        if (skipBorder && (plx == 0 || plx == 15 || plz == 0 || plz == 15)) {
            return false;
        }
        if (y <= minY || y >= maxYExcl - 1) {
            return false;
        }

        int partnerId = blockAt(secs, minY, plx, y, plz);
        if (!pal.isChest(partnerId)) {
            return false;
        }
        WrappedBlockState partner = WrappedBlockState.getByGlobalId(ver, partnerId, false);
        if (partner.getType() != state.getType() || partner.getFacing() != state.getFacing()) {
            return false;
        }
        Type expected = type == Type.LEFT ? Type.RIGHT : Type.LEFT;
        if (partner.getTypeData() != expected) {
            return false;
        }

        return allSixOcc(secs, pal, minY, lx, y, lz, plx, plz)
                && allSixOcc(secs, pal, minY, plx, y, plz, lx, lz);
    }

    private static boolean allSixOcc(
            BaseChunk[] secs, OccTbl.Palette pal, int minY, int lx, int y, int lz, int skipLx, int skipLz
    ) {
        if (lx - 1 < 0 || lx + 1 > 15 || lz - 1 < 0 || lz + 1 > 15) {
            return false;
        }
        if (!chkNb(secs, pal, minY, lx - 1, y, lz, skipLx, skipLz)) return false;
        if (!chkNb(secs, pal, minY, lx + 1, y, lz, skipLx, skipLz)) return false;
        if (!chkNb(secs, pal, minY, lx, y, lz - 1, skipLx, skipLz)) return false;
        if (!chkNb(secs, pal, minY, lx, y, lz + 1, skipLx, skipLz)) return false;
        if (!pal.isOcc(blockAt(secs, minY, lx, y - 1, lz))) return false;
        return pal.isOcc(blockAt(secs, minY, lx, y + 1, lz));
    }

    private static boolean chkNb(
            BaseChunk[] secs, OccTbl.Palette pal, int minY, int nx, int y, int nz, int skipLx, int skipLz
    ) {
        if (nx == skipLx && nz == skipLz) {
            return true;
        }
        return pal.isOcc(blockAt(secs, minY, nx, y, nz));
    }

    private static int pickRep(
            BaseChunk[] secs, OccTbl.Palette pal, String wName, StorCfg.WorldRule rule,
            int minY, int lx, int y, int lz
    ) {
        int fbId = pal.repId(rule.fallbackY(y));
        if (!rule.camo()) {
            return fbId;
        }

        int[] ids = {
                blockAt(secs, minY, lx - 1, y, lz),
                blockAt(secs, minY, lx + 1, y, lz),
                blockAt(secs, minY, lx, y, lz - 1),
                blockAt(secs, minY, lx, y, lz + 1),
                blockAt(secs, minY, lx, y - 1, lz),
                blockAt(secs, minY, lx, y + 1, lz)
        };

        int bestId = fbId;
        int bestCnt = 0;
        for (int i = 0; i < 6; i++) {
            int cand = ids[i];
            if (!pal.isCamo(wName, cand)) {
                continue;
            }
            int cnt = 1;
            for (int j = i + 1; j < 6; j++) {
                if (ids[j] == cand) {
                    cnt++;
                }
            }
            if (cnt > bestCnt || (cnt == bestCnt && cand == fbId)) {
                bestCnt = cnt;
                bestId = cand;
            }
        }
        return bestId;
    }

    private static int blockAt(BaseChunk[] secs, int minY, int lx, int y, int lz) {
        int secIdx = (y - minY) >> 4;
        if (secIdx < 0 || secIdx >= secs.length) {
            return 0;
        }
        BaseChunk chunk = secs[secIdx];
        return chunk == null ? 0 : chunk.getBlockId(lx, y & 15, lz);
    }

    @SuppressWarnings("deprecation")
    private static Column rebuildCol(Column col, TileEntity[] filtered) {
        if (col.getHeightmaps() != null) {
            return new Column(col.getX(), col.getZ(), col.isFullChunk(), col.getChunks(), filtered, col.getHeightmaps());
        }
        if (col.getBiomeDataInts() != null) {
            return new Column(col.getX(), col.getZ(), col.isFullChunk(), col.getChunks(), filtered, col.getHeightMaps(), col.getBiomeDataInts());
        }
        if (col.getBiomeDataBytes() != null) {
            return new Column(col.getX(), col.getZ(), col.isFullChunk(), col.getChunks(), filtered, col.getHeightMaps(), col.getBiomeDataBytes());
        }
        return new Column(col.getX(), col.getZ(), col.isFullChunk(), col.getChunks(), filtered, col.getHeightMaps());
    }
}
