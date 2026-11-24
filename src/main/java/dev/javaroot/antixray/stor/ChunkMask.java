package dev.javaroot.antixray.stor;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.enums.Type;

import java.util.ArrayList;
import java.util.List;

public final class ChunkMask {

    public record Result(Column column, List<HideMap.HiddenEntry> hidden) {
    }

    private ChunkMask() {
    }

    public static Result mask(
            Column column,
            ClientVersion version,
            String worldName,
            StorCfg cfg,
            StorCfg.WorldRule rule,
            OccTbl.Palette palette,
            int minY,
            int maxYExclusive
    ) {
        TileEntity[] tiles = column.getTileEntities();
        BaseChunk[] sections = column.getChunks();
        if (tiles == null || tiles.length == 0 || sections == null || sections.length == 0) {
            return null;
        }

        boolean[] hideTile = new boolean[tiles.length];
        List<HideMap.HiddenEntry> hidden = null;
        int baseX = column.getX() << 4;
        int baseZ = column.getZ() << 4;
        boolean skipBorder = cfg.masking.skipChunkBorder();
        boolean checkPartner = cfg.masking.requireBothDoubleChestHalvesBuried();

        for (int i = 0; i < tiles.length; i++) {
            TileEntity tile = tiles[i];
            if (tile == null) {
                continue;
            }
            byte packed = tile.getPackedByte();
            int lx = (packed >> 4) & 0xF;
            int lz = packed & 0xF;
            int y = tile.getYShort();

            if (!isInBounds(lx, y, lz, skipBorder, rule, minY, maxYExclusive)) {
                continue;
            }

            int blockId = blockAt(sections, minY, lx, y, lz);
            if (!palette.isTarget(worldName, blockId)) {
                continue;
            }

            boolean buried;
            if (checkPartner && palette.isChest(blockId)) {
                buried = isChestBuried(sections, version, palette, skipBorder, minY, maxYExclusive, lx, y, lz, blockId);
            } else {
                buried = areAllSixOccluding(sections, palette, minY, lx, y, lz, -1, -1);
            }

            if (!buried) {
                continue;
            }

            hideTile[i] = true;
            if (hidden == null) {
                hidden = new ArrayList<>(4);
            }
            int maskId = pickReplacement(sections, palette, worldName, rule, minY, lx, y, lz);
            hidden.add(new HideMap.HiddenEntry(baseX + lx, y, baseZ + lz, maskId));
        }

        if (hidden == null || hidden.isEmpty()) {
            return null;
        }

        for (HideMap.HiddenEntry entry : hidden) {
            int lx = entry.x() & 15;
            int lz = entry.z() & 15;
            int secIdx = (entry.y() - minY) >> 4;
            sections[secIdx].set(lx, entry.y() & 15, lz, entry.maskGlobalId());
        }

        TileEntity[] filtered = new TileEntity[tiles.length - hidden.size()];
        int idx = 0;
        for (int i = 0; i < tiles.length; i++) {
            if (!hideTile[i]) {
                filtered[idx++] = tiles[i];
            }
        }

        Column updated = rebuildColumn(column, filtered);
        return new Result(updated, hidden);
    }

    private static boolean isInBounds(
            int lx,
            int y,
            int lz,
            boolean skipBorder,
            StorCfg.WorldRule rule,
            int minY,
            int maxYExclusive
    ) {
        if (y <= minY || y >= maxYExclusive - 1) {
            return false;
        }
        if (y < rule.minY() || y > rule.maxY()) {
            return false;
        }
        if (skipBorder && (lx <= 0 || lx >= 15 || lz <= 0 || lz >= 15)) {
            return false;
        }
        return lx >= 0 && lx <= 15 && lz >= 0 && lz <= 15;
    }

    private static boolean isChestBuried(
            BaseChunk[] sections,
            ClientVersion version,
            OccTbl.Palette palette,
            boolean skipBorder,
            int minY,
            int maxYExclusive,
            int lx,
            int y,
            int lz,
            int blockId
    ) {
        WrappedBlockState state = WrappedBlockState.getByGlobalId(version, blockId, false);
        Type type = state.getTypeData();
        if (type != Type.LEFT && type != Type.RIGHT) {
            return areAllSixOccluding(sections, palette, minY, lx, y, lz, -1, -1);
        }

        int[] offset = doubleChestPartnerOffset(state.getFacing(), type);
        if (offset == null) {
            return false;
        }
        int plx = lx + offset[0];
        int plz = lz + offset[1];
        if (plx < 0 || plx > 15 || plz < 0 || plz > 15) {
            return false;
        }
        if (skipBorder && (plx == 0 || plx == 15 || plz == 0 || plz == 15)) {
            return false;
        }
        if (y <= minY || y >= maxYExclusive - 1) {
            return false;
        }

        int partnerId = blockAt(sections, minY, plx, y, plz);
        if (!palette.isChest(partnerId)) {
            return false;
        }
        WrappedBlockState partner = WrappedBlockState.getByGlobalId(version, partnerId, false);
        if (partner.getType() != state.getType() || partner.getFacing() != state.getFacing()) {
            return false;
        }
        Type expectedPartner = type == Type.LEFT ? Type.RIGHT : Type.LEFT;
        if (partner.getTypeData() != expectedPartner) {
            return false;
        }

        return areAllSixOccluding(sections, palette, minY, lx, y, lz, plx, plz)
                && areAllSixOccluding(sections, palette, minY, plx, y, plz, lx, lz);
    }

    private static int[] doubleChestPartnerOffset(BlockFace facing, Type type) {
        if (facing == null) {
            return null;
        }
        int sign = type == Type.LEFT ? 1 : -1;
        return switch (facing) {
            case NORTH -> new int[]{sign, 0};
            case SOUTH -> new int[]{-sign, 0};
            case EAST -> new int[]{0, sign};
            case WEST -> new int[]{0, -sign};
            default -> null;
        };
    }

    private static boolean areAllSixOccluding(
            BaseChunk[] sections,
            OccTbl.Palette palette,
            int minY,
            int lx,
            int y,
            int lz,
            int skipLx,
            int skipLz
    ) {
        if (lx - 1 < 0 || lx + 1 > 15 || lz - 1 < 0 || lz + 1 > 15) {
            return false;
        }
        if (!checkNeighbor(sections, palette, minY, lx - 1, y, lz, skipLx, skipLz)) return false;
        if (!checkNeighbor(sections, palette, minY, lx + 1, y, lz, skipLx, skipLz)) return false;
        if (!checkNeighbor(sections, palette, minY, lx, y, lz - 1, skipLx, skipLz)) return false;
        if (!checkNeighbor(sections, palette, minY, lx, y, lz + 1, skipLx, skipLz)) return false;
        if (!palette.isOccluding(blockAt(sections, minY, lx, y - 1, lz))) return false;
        return palette.isOccluding(blockAt(sections, minY, lx, y + 1, lz));
    }

    private static boolean checkNeighbor(
            BaseChunk[] sections,
            OccTbl.Palette palette,
            int minY,
            int nx,
            int y,
            int nz,
            int skipLx,
            int skipLz
    ) {
        if (nx == skipLx && nz == skipLz) {
            return true;
        }
        return palette.isOccluding(blockAt(sections, minY, nx, y, nz));
    }

    private static int pickReplacement(
            BaseChunk[] sections,
            OccTbl.Palette palette,
            String worldName,
            StorCfg.WorldRule rule,
            int minY,
            int lx,
            int y,
            int lz
    ) {
        int fallbackId = palette.replacementId(rule.fallbackForY(y));
        if (!rule.useNeighborCamouflage()) {
            return fallbackId;
        }

        int[] ids = {
                blockAt(sections, minY, lx - 1, y, lz),
                blockAt(sections, minY, lx + 1, y, lz),
                blockAt(sections, minY, lx, y, lz - 1),
                blockAt(sections, minY, lx, y, lz + 1),
                blockAt(sections, minY, lx, y - 1, lz),
                blockAt(sections, minY, lx, y + 1, lz)
        };

        int bestId = fallbackId;
        int bestCount = 0;
        for (int i = 0; i < 6; i++) {
            int candidate = ids[i];
            if (!palette.isAllowedCamouflage(worldName, candidate)) {
                continue;
            }
            int count = 1;
            for (int j = i + 1; j < 6; j++) {
                if (ids[j] == candidate) {
                    count++;
                }
            }
            if (count > bestCount || (count == bestCount && candidate == fallbackId)) {
                bestCount = count;
                bestId = candidate;
            }
        }
        return bestId;
    }

    private static int blockAt(BaseChunk[] sections, int minY, int lx, int y, int lz) {
        int secIdx = (y - minY) >> 4;
        if (secIdx < 0 || secIdx >= sections.length) {
            return 0;
        }
        BaseChunk chunk = sections[secIdx];
        if (chunk == null) {
            return 0;
        }
        return chunk.getBlockId(lx, y & 15, lz);
    }

    @SuppressWarnings("deprecation")
    private static Column rebuildColumn(Column column, TileEntity[] filtered) {
        if (column.getHeightmaps() != null) {
            return new Column(
                    column.getX(),
                    column.getZ(),
                    column.isFullChunk(),
                    column.getChunks(),
                    filtered,
                    column.getHeightmaps()
            );
        }
        if (column.getBiomeDataInts() != null) {
            return new Column(
                    column.getX(),
                    column.getZ(),
                    column.isFullChunk(),
                    column.getChunks(),
                    filtered,
                    column.getHeightMaps(),
                    column.getBiomeDataInts()
            );
        }
        if (column.getBiomeDataBytes() != null) {
            return new Column(
                    column.getX(),
                    column.getZ(),
                    column.isFullChunk(),
                    column.getChunks(),
                    filtered,
                    column.getHeightMaps(),
                    column.getBiomeDataBytes()
            );
        }
        return new Column(
                column.getX(),
                column.getZ(),
                column.isFullChunk(),
                column.getChunks(),
                filtered,
                column.getHeightMaps()
        );
    }
}
