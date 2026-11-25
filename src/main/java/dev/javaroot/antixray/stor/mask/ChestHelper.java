package dev.javaroot.antixray.stor.mask;

import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.enums.Type;
import org.bukkit.block.data.type.Chest;

public final class ChestHelper {

    private ChestHelper() {
    }

    public static int[] offset(BlockFace facing, Type type) {
        if (facing == null || (type != Type.LEFT && type != Type.RIGHT)) {
            return null;
        }
        return dirOffset(facing.name(), type == Type.LEFT);
    }

    public static int[] offset(org.bukkit.block.BlockFace facing, Chest.Type type) {
        if (facing == null || (type != Chest.Type.LEFT && type != Chest.Type.RIGHT)) {
            return null;
        }
        return dirOffset(facing.name(), type == Chest.Type.LEFT);
    }

    private static int[] dirOffset(String dir, boolean isLeft) {
        int s = isLeft ? 1 : -1;
        return switch (dir) {
            case "NORTH" -> new int[]{s, 0};
            case "SOUTH" -> new int[]{-s, 0};
            case "EAST" -> new int[]{0, s};
            case "WEST" -> new int[]{0, -s};
            default -> null;
        };
    }
}
