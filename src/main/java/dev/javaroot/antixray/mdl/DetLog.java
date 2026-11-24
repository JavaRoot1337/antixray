package dev.javaroot.antixray.mdl;

import java.util.UUID;

public record DetLog(
        long id,
        UUID uuid,
        String username,
        String world,
        int x,
        int y,
        int z,
        String oreType,
        double suspicionScore,
        int sessionCount,
        long createdAtMs
) {
}
