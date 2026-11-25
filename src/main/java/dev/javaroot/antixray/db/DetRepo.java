package dev.javaroot.antixray.db;

import dev.javaroot.antixray.db.mdl.DetLog;
import dev.javaroot.antixray.db.mdl.HistPage;
import dev.javaroot.antixray.db.mdl.Profile;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DetRepo {

    private final Db db;
    private final Logger log;

    public DetRepo(Db db, Logger log) {
        this.db = db;
        this.log = log;
    }

    private <T> T logFail(String action, Throwable ex, T fallback) {
        if (log != null) {
            log.log(Level.WARNING, "Database error during " + action, ex);
        }
        return fallback;
    }

    public CompletableFuture<Profile> loadProfile(UUID uuid) {
        return db.submit(c -> {
            String sql = """
                    SELECT total_punishments, suspicion_score, watch_until, phantom_hit_count, last_phantom_hit_at
                    FROM player_profiles
                    WHERE uuid = ?
                    """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Profile.EMPTY;
                    }
                    return new Profile(rs.getInt(1), rs.getDouble(2), rs.getLong(3), rs.getInt(4), rs.getLong(5));
                }
            }
        }).exceptionally(ex -> logFail("loadProfile(" + uuid + ")", ex, Profile.EMPTY));
    }

    public CompletableFuture<Void> saveSessState(
            UUID uuid,
            String user,
            double susScore,
            long watchUntilMs,
            int phantomHits,
            long lastHitMs
    ) {
        return db.execute(c -> {
            String upsert = """
                    INSERT INTO player_profiles (
                        uuid, username, total_phantom_mined, total_punishments, last_detection_at,
                        suspicion_score, watch_until, phantom_hit_count, last_phantom_hit_at
                    )
                    VALUES (?, ?, 0, 0, 0, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        username = excluded.username,
                        suspicion_score = excluded.suspicion_score,
                        watch_until = excluded.watch_until,
                        phantom_hit_count = excluded.phantom_hit_count,
                        last_phantom_hit_at = excluded.last_phantom_hit_at
                    """;
            try (PreparedStatement ps = c.prepareStatement(upsert)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, user);
                ps.setDouble(3, susScore);
                ps.setLong(4, watchUntilMs);
                ps.setInt(5, phantomHits);
                ps.setLong(6, lastHitMs);
                ps.executeUpdate();
            }
        }).exceptionally(ex -> logFail("saveSessState(" + uuid + ")", ex, null));
    }

    public CompletableFuture<Integer> loadTotalPunishments(UUID uuid) {
        return db.submit(c -> {
            String sql = "SELECT total_punishments FROM player_profiles WHERE uuid = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        }).exceptionally(ex -> logFail("loadTotalPunishments(" + uuid + ")", ex, 0));
    }

    public CompletableFuture<Void> recordDet(
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
        return db.inTx(c -> {
            String upsert = """
                    INSERT INTO player_profiles (uuid, username, total_phantom_mined, total_punishments, last_detection_at)
                    VALUES (?, ?, 1, 0, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        username = excluded.username,
                        total_phantom_mined = player_profiles.total_phantom_mined + 1,
                        last_detection_at = excluded.last_detection_at
                    """;
            try (PreparedStatement ps = c.prepareStatement(upsert)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, user);
                ps.setLong(3, createdAtMs);
                ps.executeUpdate();
            }

            String insert = """
                    INSERT INTO detections_log (uuid, username, world, x, y, z, ore_type, suspicion_score, session_count, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, user);
                ps.setString(3, world);
                ps.setInt(4, x);
                ps.setInt(5, y);
                ps.setInt(6, z);
                ps.setString(7, oreType);
                ps.setDouble(8, susScore);
                ps.setInt(9, sessCount);
                ps.setLong(10, createdAtMs);
                ps.executeUpdate();
            }
        }).exceptionally(ex -> logFail("recordDet(" + uuid + ")", ex, null));
    }

    public CompletableFuture<Void> recordPun(
            UUID uuid,
            String user,
            String world,
            int x,
            int y,
            int z,
            int phantomCount,
            String cmds,
            long createdAtMs
    ) {
        return db.inTx(c -> {
            String upsert = """
                    INSERT INTO player_profiles (uuid, username, total_phantom_mined, total_punishments, last_detection_at)
                    VALUES (?, ?, 0, 1, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        username = excluded.username,
                        total_punishments = player_profiles.total_punishments + 1
                    """;
            try (PreparedStatement ps = c.prepareStatement(upsert)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, user);
                ps.setLong(3, createdAtMs);
                ps.executeUpdate();
            }

            String insert = """
                    INSERT INTO punishments_log (uuid, username, world, x, y, z, phantom_count, executed_commands, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, user);
                ps.setString(3, world);
                ps.setInt(4, x);
                ps.setInt(5, y);
                ps.setInt(6, z);
                ps.setInt(7, phantomCount);
                ps.setString(8, cmds);
                ps.setLong(9, createdAtMs);
                ps.executeUpdate();
            }
        }).exceptionally(ex -> logFail("recordPun(" + uuid + ")", ex, null));
    }

    public CompletableFuture<HistPage> fetchHist(UUID uuid, int reqPage, int pageSize) {
        return db.submit(c -> {
            int total = 0;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM detections_log WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getInt(1);
                    }
                }
            }
            if (total == 0) {
                return HistPage.EMPTY;
            }

            int maxPage = Math.max(1, (total + pageSize - 1) / pageSize);
            int page = Math.max(1, Math.min(reqPage, maxPage));
            int offset = (page - 1) * pageSize;

            List<DetLog> list = new ArrayList<>(pageSize);
            String sql = """
                    SELECT id, username, world, x, y, z, ore_type, suspicion_score, session_count, created_at
                    FROM detections_log
                    WHERE uuid = ?
                    ORDER BY created_at DESC
                    LIMIT ? OFFSET ?
                    """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, pageSize);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new DetLog(
                                rs.getLong("id"),
                                uuid,
                                rs.getString("username"),
                                rs.getString("world"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z"),
                                rs.getString("ore_type"),
                                rs.getDouble("suspicion_score"),
                                rs.getInt("session_count"),
                                rs.getLong("created_at")
                        ));
                    }
                }
            }
            return new HistPage(list, page, maxPage, total);
        }).exceptionally(ex -> logFail("fetchHist(" + uuid + ")", ex, HistPage.EMPTY));
    }

    public CompletableFuture<UUID> findUuidByName(String user) {
        return db.submit(c -> {
            String sql = "SELECT uuid FROM player_profiles WHERE LOWER(username) = LOWER(?) LIMIT 1";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, user);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? UUID.fromString(rs.getString(1)) : null;
                }
            }
        }).exceptionally(ex -> logFail("findUuidByName(" + user + ")", ex, null));
    }

    public CompletableFuture<Void> pruneOld(int retentionDays) {
        if (retentionDays <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        long cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L;
        return db.execute(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM detections_log WHERE created_at < ?")) {
                ps.setLong(1, cutoff);
                ps.executeUpdate();
            }
        }).exceptionally(ex -> logFail("pruneOld", ex, null));
    }
}
