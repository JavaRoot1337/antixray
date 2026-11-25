package dev.javaroot.antixray.db.mdl;

import java.util.UUID;

public record DetLog(
        long id,
        UUID uuid,
        String user,
        String world,
        int x,
        int y,
        int z,
        String oreType,
        double susScore,
        int sessCount,
        long createdAtMs
) {
}
